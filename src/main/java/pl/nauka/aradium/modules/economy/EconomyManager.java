package pl.nauka.aradium.modules.economy;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import pl.nauka.aradium.core.database.DatabaseManager;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Menedżer ekonomii oparty o bezpieczny wątkowo cache w pamięci (O(1))
 * oraz wsadowy zapis (write-behind) do SQLite/MySQL lub pliku YAML.
 */
public class EconomyManager {

    private static final String SQLITE_UPSERT =
            "INSERT INTO economy_accounts (uuid, balance, updated_at) VALUES (?, ?, ?) "
                    + "ON CONFLICT(uuid) DO UPDATE SET balance = excluded.balance, updated_at = excluded.updated_at";
    private static final String MYSQL_UPSERT =
            "INSERT INTO economy_accounts (uuid, balance, updated_at) VALUES (?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE balance = VALUES(balance), updated_at = VALUES(updated_at)";

    private final DatabaseManager databaseManager;
    private final File yamlFallbackFile;
    private final Logger logger;
    private final double startingBalance;

    private final Map<UUID, Double> balances = new ConcurrentHashMap<>();
    private final Set<UUID> dirtyAccounts = ConcurrentHashMap.newKeySet();

    public EconomyManager(DatabaseManager databaseManager, File yamlFallbackFile, Logger logger, double startingBalance) {
        this.databaseManager = databaseManager;
        this.yamlFallbackFile = yamlFallbackFile;
        this.logger = Objects.requireNonNull(logger, "logger");
        this.startingBalance = Math.max(0.0, normalizeAmount(startingBalance));
    }

    public EconomyManager(File yamlFile, Logger logger, double startingBalance) {
        this(null, Objects.requireNonNull(yamlFile, "yamlFile"), logger, startingBalance);
    }

    public EconomyManager(double startingBalance) {
        this.databaseManager = null;
        this.yamlFallbackFile = null;
        this.logger = Logger.getLogger(EconomyManager.class.getName());
        this.startingBalance = Math.max(0.0, normalizeAmount(startingBalance));
    }

    /**
     * Normalizuje kwotę do 2 miejsc po przecinku. Zwraca -1.0 dla wartości niepoprawnych lub ujemnych.
     */
    public static double normalizeAmount(double amount) {
        if (Double.isNaN(amount) || Double.isInfinite(amount) || amount < 0.0) {
            return -1.0;
        }
        return Math.round(amount * 100.0) / 100.0;
    }

    public static String formatAmount(double amount) {
        double safe = Double.isNaN(amount) || Double.isInfinite(amount) ? 0.0 : amount;
        return String.format(Locale.US, "%,.2f", safe);
    }

    public static String formatMoney(double amount) {
        return formatAmount(amount) + "$";
    }

    public double getStartingBalance() {
        return startingBalance;
    }

    public double getMoney(UUID playerId) {
        if (playerId == null) {
            return 0.0;
        }
        return balances.computeIfAbsent(playerId, ignored -> {
            dirtyAccounts.add(playerId);
            return startingBalance;
        });
    }

    public boolean hasMoney(UUID playerId, double amount) {
        if (playerId == null) {
            return false;
        }
        double normalized = normalizeAmount(amount);
        if (normalized < 0.0) {
            return false;
        }
        if (normalized == 0.0) {
            return true;
        }
        return getMoney(playerId) >= normalized;
    }

    public boolean addMoney(UUID playerId, double amount) {
        double normalized = normalizeAmount(amount);
        if (playerId == null || normalized <= 0.0) {
            return false;
        }
        balances.compute(playerId, (ignored, current) -> {
            double base = current != null ? current : startingBalance;
            return normalizeAmount(base + normalized);
        });
        dirtyAccounts.add(playerId);
        return true;
    }

    public boolean takeMoney(UUID playerId, double amount) {
        double normalized = normalizeAmount(amount);
        if (playerId == null || normalized <= 0.0) {
            return false;
        }

        AtomicBoolean success = new AtomicBoolean(false);
        balances.compute(playerId, (ignored, current) -> {
            double base = current != null ? current : startingBalance;
            if (base < normalized) {
                return current;
            }
            success.set(true);
            return normalizeAmount(base - normalized);
        });

        if (success.get()) {
            dirtyAccounts.add(playerId);
        }
        return success.get();
    }

    public boolean setMoney(UUID playerId, double amount) {
        double normalized = normalizeAmount(amount);
        if (playerId == null || normalized < 0.0) {
            return false;
        }
        balances.put(playerId, normalized);
        dirtyAccounts.add(playerId);
        return true;
    }

    public boolean transferMoney(UUID senderId, UUID targetId, double amount) {
        if (senderId == null || targetId == null || senderId.equals(targetId)) {
            return false;
        }
        double normalized = normalizeAmount(amount);
        if (normalized <= 0.0 || !takeMoney(senderId, normalized)) {
            return false;
        }
        if (!addMoney(targetId, normalized)) {
            addMoney(senderId, normalized);
            return false;
        }
        return true;
    }

    public Map<UUID, Double> getCachedBalancesSnapshot() {
        return Collections.unmodifiableMap(balances);
    }

    public int getDirtyCount() {
        return dirtyAccounts.size();
    }

    public void loadAll() {
        balances.clear();
        dirtyAccounts.clear();

        if (databaseManager != null && databaseManager.isConnected()) {
            loadFromDatabase();
            return;
        }
        if (yamlFallbackFile != null) {
            loadFromYaml();
        }
    }

    public synchronized void flushDirtyAccounts() {
        if (dirtyAccounts.isEmpty()) {
            return;
        }

        if (databaseManager != null && databaseManager.isConnected()) {
            flushToDatabase();
            return;
        }
        if (yamlFallbackFile != null) {
            saveToYaml();
        }
    }

    private void loadFromDatabase() {
        String sql = "SELECT uuid, balance FROM economy_accounts";
        try {
            Connection conn = databaseManager.getConnection();
            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    try {
                        UUID playerId = UUID.fromString(rs.getString("uuid"));
                        double balance = Math.max(0.0, normalizeAmount(rs.getDouble("balance")));
                        balances.put(playerId, balance);
                    } catch (IllegalArgumentException ignored) {
                        // Pomijamy niepoprawne UUID w bazie.
                    }
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Nie udało się wczytać kont ekonomii z bazy danych.", e);
        }
    }

    private void flushToDatabase() {
        String sql = databaseManager.getType() == DatabaseManager.DatabaseType.MYSQL ? MYSQL_UPSERT : SQLITE_UPSERT;
        Set<UUID> batchIds = Set.copyOf(dirtyAccounts);
        if (batchIds.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        try {
            Connection conn = databaseManager.getConnection();
            boolean previousAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (UUID playerId : batchIds) {
                    Double balance = balances.get(playerId);
                    if (balance == null) {
                        continue;
                    }
                    ps.setString(1, playerId.toString());
                    ps.setDouble(2, balance);
                    ps.setLong(3, now);
                    ps.addBatch();
                }
                ps.executeBatch();
                conn.commit();
                dirtyAccounts.removeAll(batchIds);
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Błąd podczas zapisu wsadowego kont ekonomii do bazy danych.", e);
        }
    }

    private void loadFromYaml() {
        if (!yamlFallbackFile.exists()) {
            return;
        }
        FileConfiguration yaml = YamlConfiguration.loadConfiguration(yamlFallbackFile);
        ConfigurationSection section = yaml.getConfigurationSection("accounts");
        if (section == null) {
            return;
        }
        for (String uuidText : section.getKeys(false)) {
            try {
                UUID playerId = UUID.fromString(uuidText);
                double balance = Math.max(0.0, normalizeAmount(section.getDouble(uuidText, startingBalance)));
                balances.put(playerId, balance);
            } catch (IllegalArgumentException ignored) {
                // Pomijamy błędne wpisy UUID.
            }
        }
    }

    private void saveToYaml() {
        FileConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, Double> entry : balances.entrySet()) {
            yaml.set("accounts." + entry.getKey(), entry.getValue());
        }
        try {
            File parent = yamlFallbackFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            yaml.save(yamlFallbackFile);
            dirtyAccounts.clear();
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Nie udało się zapisać pliku economy.yml.", e);
        }
    }
}
