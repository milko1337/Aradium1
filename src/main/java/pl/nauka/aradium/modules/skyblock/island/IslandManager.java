package pl.nauka.aradium.modules.skyblock.island;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import pl.nauka.aradium.AradiumPlugin;
import pl.nauka.aradium.core.database.DatabaseManager;
import pl.nauka.aradium.modules.skyblock.upgrades.IslandUpgradeManager;
import pl.nauka.aradium.modules.skyblock.upgrades.IslandUpgradeType;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Menedżer wysp SkyBlock odpowiedzialny za przydział współrzędnych w siatce,
 * cykl życia wysp, tymczasowe zaufania (TRUSTED), granice WorldBorder oraz zapis SQL/YAML.
 */
public class IslandManager {

    private static final String SQLITE_ISLAND_UPSERT =
            "INSERT INTO skyblock_islands (island_id, owner_uuid, world_name, center_x, center_z, "
                    + "home_x, home_y, home_z, home_yaw, home_pitch, is_public, border_color, "
                    + "island_level, bank_balance, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                    + "ON CONFLICT(island_id) DO UPDATE SET "
                    + "owner_uuid = excluded.owner_uuid, world_name = excluded.world_name, "
                    + "center_x = excluded.center_x, center_z = excluded.center_z, "
                    + "home_x = excluded.home_x, home_y = excluded.home_y, home_z = excluded.home_z, "
                    + "home_yaw = excluded.home_yaw, home_pitch = excluded.home_pitch, "
                    + "is_public = excluded.is_public, border_color = excluded.border_color, "
                    + "island_level = excluded.island_level, bank_balance = excluded.bank_balance";

    private static final String MYSQL_ISLAND_UPSERT =
            "INSERT INTO skyblock_islands (island_id, owner_uuid, world_name, center_x, center_z, "
                    + "home_x, home_y, home_z, home_yaw, home_pitch, is_public, border_color, "
                    + "island_level, bank_balance, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE "
                    + "owner_uuid = VALUES(owner_uuid), world_name = VALUES(world_name), "
                    + "center_x = VALUES(center_x), center_z = VALUES(center_z), "
                    + "home_x = VALUES(home_x), home_y = VALUES(home_y), home_z = VALUES(home_z), "
                    + "home_yaw = VALUES(home_yaw), home_pitch = VALUES(home_pitch), "
                    + "is_public = VALUES(is_public), border_color = VALUES(border_color), "
                    + "island_level = VALUES(island_level), bank_balance = VALUES(bank_balance)";

    public record PendingInvite(UUID islandId, String inviterName, long expiresAtMillis) {
        public boolean isExpired(long now) {
            return now > expiresAtMillis;
        }
    }

    private final AradiumPlugin plugin;
    private final DatabaseManager databaseManager;
    private final File yamlFallbackFile;
    private final Logger logger;

    private String worldName = "skyblock_world";
    private int islandSpacing = 400;
    private int islandHeight = 100;
    private int defaultBorderRadius = 50;
    private int maxWarpsPerIsland = 3;
    private int maxMembersDefault = 4;
    private int defaultSpawnerLimit = 8;
    private IslandUpgradeManager upgradeManager;

    private final Map<UUID, Island> islandsById = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> playerToIslandId = new ConcurrentHashMap<>();
    private final Map<Long, UUID> cellToIslandId = new ConcurrentHashMap<>();
    private final Map<UUID, PendingInvite> pendingInvites = new ConcurrentHashMap<>();
    private final Map<UUID, Long> deleteConfirmations = new ConcurrentHashMap<>();
    private final Map<UUID, String> activeBorderStates = new ConcurrentHashMap<>();

    private int nextGridIndex = 0;

    public IslandManager(AradiumPlugin plugin, DatabaseManager databaseManager, File yamlFallbackFile, Logger logger) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.databaseManager = databaseManager;
        this.yamlFallbackFile = yamlFallbackFile;
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public void setUpgradeManager(IslandUpgradeManager upgradeManager) {
        this.upgradeManager = upgradeManager;
    }

    public void reloadSettings() {
        FileConfiguration config = plugin.getConfig();
        String configuredWorld = config.getString("skyblock.world_name", "skyblock_world");
        this.worldName = (configuredWorld != null && !configuredWorld.isBlank()) ? configuredWorld.trim() : "skyblock_world";
        this.islandSpacing = Math.max(100, config.getInt("skyblock.island_spacing", 400));
        this.islandHeight = Math.clamp(config.getInt("skyblock.island_height", 100), 10, 280);
        this.defaultBorderRadius = Math.clamp(config.getInt("skyblock.default_border_radius", 50), 16, (this.islandSpacing / 2) - 8);
        this.maxWarpsPerIsland = Math.max(1, config.getInt("skyblock.max_warps_per_island", 3));
        this.maxMembersDefault = Math.max(1, config.getInt("skyblock.max_members_default", 4));
        this.defaultSpawnerLimit = Math.max(1, config.getInt("skyblock.default_spawner_limit", 8));
    }

    public World ensureSkyBlockWorld() {
        World existing = Bukkit.getWorld(worldName);
        if (existing != null) {
            return existing;
        }
        return new WorldCreator(worldName)
                .environment(World.Environment.NORMAL)
                .generator(new SkyBlockWorldGenerator())
                .generateStructures(false)
                .createWorld();
    }

    public static int[] calculateGridCoordinates(int index, int spacing) {
        int safeIndex = Math.max(0, index);
        int x = 0;
        int z = 0;
        int dx = 1;
        int dz = 0;
        int segmentLength = 1;
        int segmentPassed = 0;

        for (int i = 0; i < safeIndex; i++) {
            x += dx;
            z += dz;
            segmentPassed++;
            if (segmentPassed == segmentLength) {
                segmentPassed = 0;
                int tmp = dx;
                dx = -dz;
                dz = tmp;
                if (dz == 0) {
                    segmentLength++;
                }
            }
        }
        return new int[]{x * spacing, z * spacing};
    }

    public Optional<Island> getIslandById(UUID islandId) {
        return islandId != null ? Optional.ofNullable(islandsById.get(islandId)) : Optional.empty();
    }

    public Optional<Island> getPlayerIsland(UUID playerId) {
        if (playerId == null) {
            return Optional.empty();
        }
        UUID islandId = playerToIslandId.get(playerId);
        return islandId != null ? Optional.ofNullable(islandsById.get(islandId)) : Optional.empty();
    }

    public Optional<Island> getIslandAt(Location location) {
        if (location == null || location.getWorld() == null) {
            return Optional.empty();
        }
        return getIslandAt(location.getWorld().getName(), location.getBlockX(), location.getBlockZ());
    }

    public Optional<Island> getIslandAt(String world, int blockX, int blockZ) {
        if (world == null || !worldName.equals(world)) {
            return Optional.empty();
        }
        int gx = (int) Math.round((double) blockX / islandSpacing);
        int gz = (int) Math.round((double) blockZ / islandSpacing);
        UUID islandId = cellToIslandId.get(packGridKey(gx, gz));
        if (islandId == null) {
            return Optional.empty();
        }
        Island island = islandsById.get(islandId);
        if (island != null && island.containsLocation(world, blockX, blockZ)) {
            return Optional.of(island);
        }
        return Optional.empty();
    }

    public Collection<Island> getAllIslands() {
        return Collections.unmodifiableCollection(islandsById.values());
    }

    public Island createIsland(Player owner, IslandSchematic schematic) {
        Objects.requireNonNull(owner, "owner");
        if (getPlayerIsland(owner.getUniqueId()).isPresent()) {
            return null;
        }

        World world = ensureSkyBlockWorld();
        if (world == null) {
            logger.severe("Nie udało się utworzyć ani wczytać świata SkyBlock: " + worldName);
            return null;
        }

        int centerX;
        int centerZ;
        while (true) {
            int[] coords = calculateGridCoordinates(nextGridIndex++, islandSpacing);
            int gx = Math.round((float) coords[0] / islandSpacing);
            int gz = Math.round((float) coords[1] / islandSpacing);
            if (!cellToIslandId.containsKey(packGridKey(gx, gz))) {
                centerX = coords[0];
                centerZ = coords[1];
                break;
            }
        }

        IslandSchematic chosen = schematic != null ? schematic : IslandSchematic.CLASSIC;
        chosen.generate(world, centerX, islandHeight, centerZ);

        UUID islandId = UUID.randomUUID();
        double homeX = centerX + 0.5;
        double homeY = islandHeight + 1.0;
        double homeZ = centerZ + 0.5;

        Island island = new Island(
                islandId,
                owner.getUniqueId(),
                worldName,
                centerX,
                centerZ,
                homeX,
                homeY,
                homeZ,
                0.0f,
                0.0f,
                false,
                BorderColor.BLUE,
                0.0,
                0.0,
                System.currentTimeMillis(),
                defaultBorderRadius,
                maxMembersDefault,
                defaultSpawnerLimit
        );

        if (upgradeManager != null) {
            upgradeManager.applyUpgradesToIsland(island);
        }
        registerIslandInMemory(island);
        saveIsland(island);

        Location homeLoc = island.getHomeLocation();
        if (homeLoc != null) {
            owner.teleport(homeLoc);
            refreshWorldBorder(owner, true);
        }
        return island;
    }

    public boolean deleteIsland(Island island) {
        if (island == null) {
            return false;
        }

        World skyWorld = Bukkit.getWorld(island.getWorldName());
        World fallbackWorld = Bukkit.getWorlds().stream()
                .filter(w -> !w.getName().equals(island.getWorldName()))
                .findFirst()
                .orElse(null);

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (island.containsLocation(online.getLocation())) {
                online.setWorldBorder(null);
                activeBorderStates.remove(online.getUniqueId());
                if (fallbackWorld != null) {
                    online.teleport(fallbackWorld.getSpawnLocation());
                } else if (skyWorld != null) {
                    online.teleport(skyWorld.getSpawnLocation());
                }
            }
        }

        if (skyWorld != null) {
            IslandSchematic.clearStarterRegion(skyWorld, island.getCenterX(), islandHeight, island.getCenterZ());
        }

        unregisterIslandFromMemory(island);
        deleteIslandFromStorage(island.getIslandId());
        return true;
    }

    public boolean addMember(Island island, UUID targetId, IslandRole role) {
        if (island == null || targetId == null || getPlayerIsland(targetId).isPresent()) {
            return false;
        }
        if (island.getPermanentMemberCount() >= island.getMaxMembers()) {
            return false;
        }
        island.setMember(targetId, role);
        playerToIslandId.put(targetId, island.getIslandId());
        saveIsland(island);
        return true;
    }

    public boolean removeMember(Island island, UUID targetId) {
        if (island == null || targetId == null || !island.removeMember(targetId)) {
            return false;
        }
        playerToIslandId.remove(targetId);
        saveIsland(island);
        return true;
    }

    public boolean transferOwnership(Island island, UUID newOwnerId) {
        if (island == null || !island.transferOwnership(newOwnerId)) {
            return false;
        }
        saveIsland(island);
        return true;
    }

    public void clearSessionTrusts(UUID playerId) {
        if (playerId == null) {
            return;
        }
        for (Island island : islandsById.values()) {
            island.removeTrusted(playerId);
        }
        pendingInvites.remove(playerId);
        deleteConfirmations.remove(playerId);
        activeBorderStates.remove(playerId);
    }

    public void createInvite(UUID targetId, Island island, String inviterName) {
        long expiresAt = System.currentTimeMillis() + 60_000L;
        pendingInvites.put(targetId, new PendingInvite(island.getIslandId(), inviterName, expiresAt));
    }

    public Optional<PendingInvite> consumeInvite(UUID targetId, String inviterFilter) {
        PendingInvite invite = pendingInvites.get(targetId);
        if (invite == null) {
            return Optional.empty();
        }
        long now = System.currentTimeMillis();
        if (invite.isExpired(now)) {
            pendingInvites.remove(targetId);
            return Optional.empty();
        }
        if (inviterFilter != null && !inviterFilter.isBlank()
                && !invite.inviterName().equalsIgnoreCase(inviterFilter.trim())) {
            return Optional.empty();
        }
        pendingInvites.remove(targetId);
        return Optional.of(invite);
    }

    public boolean hasValidDeleteConfirmation(UUID ownerId) {
        Long expires = deleteConfirmations.get(ownerId);
        if (expires == null) {
            return false;
        }
        if (System.currentTimeMillis() > expires) {
            deleteConfirmations.remove(ownerId);
            return false;
        }
        deleteConfirmations.remove(ownerId);
        return true;
    }

    public void markDeleteConfirmation(UUID ownerId) {
        deleteConfirmations.put(ownerId, System.currentTimeMillis() + 30_000L);
    }

    public void refreshWorldBorder(Player player, boolean force) {
        if (player == null || !player.isOnline()) {
            return;
        }
        Location loc = player.getLocation();
        if (loc.getWorld() == null || !loc.getWorld().getName().equals(worldName)) {
            if (activeBorderStates.remove(player.getUniqueId()) != null || force) {
                player.setWorldBorder(null);
            }
            return;
        }

        Optional<Island> currentIsland = getIslandAt(loc);
        if (currentIsland.isEmpty()) {
            if (activeBorderStates.remove(player.getUniqueId()) != null || force) {
                player.setWorldBorder(null);
            }
            return;
        }

        Island island = currentIsland.get();
        String borderKey = island.getIslandId() + ":" + island.getBorderColor().name() + ":" + island.getBorderRadius();
        if (!force && borderKey.equals(activeBorderStates.get(player.getUniqueId()))) {
            return;
        }

        applyBorderPacket(player, island);
        activeBorderStates.put(player.getUniqueId(), borderKey);
    }

    public void refreshBordersOnIsland(Island island) {
        if (island == null) {
            return;
        }
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (island.containsLocation(online.getLocation())) {
                refreshWorldBorder(online, true);
            }
        }
    }

    private void applyBorderPacket(Player player, Island island) {
        double diameter = island.getBorderRadius() * 2.0;
        WorldBorder border = Bukkit.createWorldBorder();
        border.setCenter(island.getCenterX() + 0.5, island.getCenterZ() + 0.5);
        border.setWarningTimeTicks(0);
        border.setWarningDistance(0);
        border.setDamageBuffer(0.0);
        border.setDamageAmount(0.0);

        switch (island.getBorderColor()) {
            case BLUE -> border.setSize(diameter);
            case GREEN -> {
                border.setSize(diameter);
                border.changeSize(diameter + 0.001, Integer.MAX_VALUE);
            }
            case RED -> {
                border.setSize(diameter + 0.001);
                border.changeSize(diameter, Integer.MAX_VALUE);
            }
        }
        player.setWorldBorder(border);
    }

    public String getWorldName() {
        return worldName;
    }

    public int getIslandSpacing() {
        return islandSpacing;
    }

    public int getIslandHeight() {
        return islandHeight;
    }

    public int getMaxWarpsPerIsland() {
        return maxWarpsPerIsland;
    }

    public synchronized void loadAll() {
        islandsById.clear();
        playerToIslandId.clear();
        cellToIslandId.clear();
        pendingInvites.clear();
        deleteConfirmations.clear();
        activeBorderStates.clear();
        nextGridIndex = 0;

        if (databaseManager != null && databaseManager.isConnected()) {
            loadFromDatabase();
        } else if (yamlFallbackFile != null) {
            loadFromYaml();
        }
        nextGridIndex = Math.max(nextGridIndex, islandsById.size());
        logger.info("Załadowano wysp SkyBlock: " + islandsById.size() + ".");
    }

    public synchronized void saveAll() {
        if (databaseManager != null && databaseManager.isConnected()) {
            for (Island island : islandsById.values()) {
                saveIslandToDatabase(island);
            }
            return;
        }
        if (yamlFallbackFile != null) {
            saveAllToYaml();
        }
    }

    public synchronized void saveIsland(Island island) {
        if (island == null) {
            return;
        }
        if (databaseManager != null && databaseManager.isConnected()) {
            saveIslandToDatabase(island);
            return;
        }
        if (yamlFallbackFile != null) {
            saveAllToYaml();
        }
    }

    private void deleteIslandFromStorage(UUID islandId) {
        if (islandId == null) {
            return;
        }
        if (databaseManager != null && databaseManager.isConnected()) {
            try {
                Connection conn = databaseManager.getConnection();
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM skyblock_islands WHERE island_id = ?")) {
                    ps.setString(1, islandId.toString());
                    ps.executeUpdate();
                }
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Nie udało się usunąć wyspy z bazy danych: " + islandId, e);
            }
            return;
        }
        if (yamlFallbackFile != null) {
            saveAllToYaml();
        }
    }

    private void loadFromDatabase() {
        try {
            Connection conn = databaseManager.getConnection();
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT * FROM skyblock_islands")) {
                while (rs.next()) {
                    try {
                        UUID islandId = UUID.fromString(rs.getString("island_id"));
                        UUID ownerUuid = UUID.fromString(rs.getString("owner_uuid"));
                        String loadedWorld = rs.getString("world_name");
                        int cx = rs.getInt("center_x");
                        int cz = rs.getInt("center_z");
                        double hx = rs.getDouble("home_x");
                        double hy = rs.getDouble("home_y");
                        double hz = rs.getDouble("home_z");
                        float yaw = rs.getFloat("home_yaw");
                        float pitch = rs.getFloat("home_pitch");
                        boolean isPublic = rs.getBoolean("is_public");
                        BorderColor borderColor = BorderColor.parse(rs.getString("border_color"));
                        double level = rs.getDouble("island_level");
                        double bank = rs.getDouble("bank_balance");
                        long createdAt = rs.getLong("created_at");

                        Island island = new Island(
                                islandId, ownerUuid, loadedWorld, cx, cz,
                                hx, hy, hz, yaw, pitch, isPublic, borderColor,
                                level, bank, createdAt, defaultBorderRadius, maxMembersDefault, defaultSpawnerLimit
                        );
                        registerIslandInMemory(island);
                    } catch (IllegalArgumentException ignored) {
                        // Pomijamy niepoprawne rekordy UUID.
                    }
                }
            }

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT island_id, player_uuid, role FROM skyblock_members")) {
                while (rs.next()) {
                    try {
                        UUID islandId = UUID.fromString(rs.getString("island_id"));
                        UUID playerId = UUID.fromString(rs.getString("player_uuid"));
                        IslandRole role = IslandRole.parse(rs.getString("role"));
                        Island island = islandsById.get(islandId);
                        if (island != null && role != null) {
                            island.setMember(playerId, role);
                            playerToIslandId.put(playerId, islandId);
                        }
                    } catch (IllegalArgumentException ignored) {
                        // Pomijamy niepoprawne UUID.
                    }
                }
            }

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT island_id, role, permission, allowed FROM skyblock_role_permissions")) {
                while (rs.next()) {
                    try {
                        UUID islandId = UUID.fromString(rs.getString("island_id"));
                        IslandRole role = IslandRole.parse(rs.getString("role"));
                        IslandPermission perm = IslandPermission.parse(rs.getString("permission"));
                        boolean allowed = rs.getBoolean("allowed");
                        Island island = islandsById.get(islandId);
                        if (island != null && role != null && perm != null) {
                            island.setRolePermission(role, perm, allowed);
                        }
                    } catch (IllegalArgumentException ignored) {
                        // Pomijamy błędne rekordy.
                    }
                }
            }

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT island_id, warp_name, world_name, x, y, z, yaw, pitch, icon FROM skyblock_warps")) {
                while (rs.next()) {
                    try {
                        UUID islandId = UUID.fromString(rs.getString("island_id"));
                        Island island = islandsById.get(islandId);
                        if (island != null) {
                            Material icon = Material.matchMaterial(rs.getString("icon"));
                            IslandWarp warp = new IslandWarp(
                                    rs.getString("warp_name"),
                                    rs.getString("world_name"),
                                    rs.getDouble("x"),
                                    rs.getDouble("y"),
                                    rs.getDouble("z"),
                                    rs.getFloat("yaw"),
                                    rs.getFloat("pitch"),
                                    icon
                            );
                            island.putWarp(warp);
                        }
                    } catch (IllegalArgumentException ignored) {
                        // Pomijamy niepoprawny rekord.
                    }
                }
            }

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT island_id, upgrade_type, tier FROM skyblock_upgrades")) {
                while (rs.next()) {
                    try {
                        UUID islandId = UUID.fromString(rs.getString("island_id"));
                        IslandUpgradeType upgradeType = IslandUpgradeType.parse(rs.getString("upgrade_type"));
                        int tier = rs.getInt("tier");
                        Island island = islandsById.get(islandId);
                        if (island != null && upgradeType != null) {
                            island.setUpgradeTier(upgradeType, tier);
                        }
                    } catch (IllegalArgumentException ignored) {
                        // Pomijamy niepoprawny rekord ulepszenia.
                    }
                }
            }

            if (upgradeManager != null) {
                for (Island island : islandsById.values()) {
                    upgradeManager.applyUpgradesToIsland(island);
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Błąd podczas wczytywania wysp z bazy danych.", e);
        }
    }

    private void saveIslandToDatabase(Island island) {
        String upsertSql = databaseManager.getType() == DatabaseManager.DatabaseType.MYSQL
                ? MYSQL_ISLAND_UPSERT
                : SQLITE_ISLAND_UPSERT;
        String islandIdStr = island.getIslandId().toString();

        try {
            Connection conn = databaseManager.getConnection();
            boolean prevAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps = conn.prepareStatement(upsertSql)) {
                    ps.setString(1, islandIdStr);
                    ps.setString(2, island.getOwnerUuid().toString());
                    ps.setString(3, island.getWorldName());
                    ps.setInt(4, island.getCenterX());
                    ps.setInt(5, island.getCenterZ());
                    ps.setDouble(6, island.getHomeX());
                    ps.setDouble(7, island.getHomeY());
                    ps.setDouble(8, island.getHomeZ());
                    ps.setFloat(9, island.getHomeYaw());
                    ps.setFloat(10, island.getHomePitch());
                    ps.setBoolean(11, island.isPublicAccess());
                    ps.setString(12, island.getBorderColor().name());
                    ps.setDouble(13, island.getIslandLevel());
                    ps.setDouble(14, island.getBankBalance());
                    ps.setLong(15, island.getCreatedAt());
                    ps.executeUpdate();
                }

                try (PreparedStatement delMembers = conn.prepareStatement("DELETE FROM skyblock_members WHERE island_id = ?")) {
                    delMembers.setString(1, islandIdStr);
                    delMembers.executeUpdate();
                }
                try (PreparedStatement insMember = conn.prepareStatement(
                        "INSERT INTO skyblock_members (island_id, player_uuid, role, joined_at) VALUES (?, ?, ?, ?)")) {
                    long now = System.currentTimeMillis();
                    for (Map.Entry<UUID, IslandRole> entry : island.getMembersSnapshot().entrySet()) {
                        insMember.setString(1, islandIdStr);
                        insMember.setString(2, entry.getKey().toString());
                        insMember.setString(3, entry.getValue().name());
                        insMember.setLong(4, now);
                        insMember.addBatch();
                    }
                    insMember.executeBatch();
                }

                try (PreparedStatement delPerms = conn.prepareStatement("DELETE FROM skyblock_role_permissions WHERE island_id = ?")) {
                    delPerms.setString(1, islandIdStr);
                    delPerms.executeUpdate();
                }
                try (PreparedStatement insPerm = conn.prepareStatement(
                        "INSERT INTO skyblock_role_permissions (island_id, role, permission, allowed) VALUES (?, ?, ?, ?)")) {
                    for (Map.Entry<IslandRole, Map<IslandPermission, Boolean>> roleEntry : island.getRolePermissionsSnapshot().entrySet()) {
                        for (Map.Entry<IslandPermission, Boolean> permEntry : roleEntry.getValue().entrySet()) {
                            insPerm.setString(1, islandIdStr);
                            insPerm.setString(2, roleEntry.getKey().name());
                            insPerm.setString(3, permEntry.getKey().name());
                            insPerm.setBoolean(4, Boolean.TRUE.equals(permEntry.getValue()));
                            insPerm.addBatch();
                        }
                    }
                    insPerm.executeBatch();
                }

                try (PreparedStatement delWarps = conn.prepareStatement("DELETE FROM skyblock_warps WHERE island_id = ?")) {
                    delWarps.setString(1, islandIdStr);
                    delWarps.executeUpdate();
                }
                try (PreparedStatement insWarp = conn.prepareStatement(
                        "INSERT INTO skyblock_warps (island_id, warp_name, world_name, x, y, z, yaw, pitch, icon) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                    for (IslandWarp warp : island.getWarpsSnapshot().values()) {
                        insWarp.setString(1, islandIdStr);
                        insWarp.setString(2, warp.name());
                        insWarp.setString(3, warp.worldName());
                        insWarp.setDouble(4, warp.x());
                        insWarp.setDouble(5, warp.y());
                        insWarp.setDouble(6, warp.z());
                        insWarp.setFloat(7, warp.yaw());
                        insWarp.setFloat(8, warp.pitch());
                        insWarp.setString(9, warp.icon().name());
                        insWarp.addBatch();
                    }
                    insWarp.executeBatch();
                }

                try (PreparedStatement delUpgrades = conn.prepareStatement("DELETE FROM skyblock_upgrades WHERE island_id = ?")) {
                    delUpgrades.setString(1, islandIdStr);
                    delUpgrades.executeUpdate();
                }
                try (PreparedStatement insUpgrade = conn.prepareStatement(
                        "INSERT INTO skyblock_upgrades (island_id, upgrade_type, tier) VALUES (?, ?, ?)")) {
                    for (Map.Entry<IslandUpgradeType, Integer> entry : island.getUpgradeTiersSnapshot().entrySet()) {
                        insUpgrade.setString(1, islandIdStr);
                        insUpgrade.setString(2, entry.getKey().name());
                        insUpgrade.setInt(3, entry.getValue());
                        insUpgrade.addBatch();
                    }
                    insUpgrade.executeBatch();
                }

                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(prevAutoCommit);
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Błąd podczas zapisu wyspy " + islandIdStr + " do bazy danych.", e);
        }
    }

    private void loadFromYaml() {
        if (!yamlFallbackFile.exists()) {
            return;
        }
        FileConfiguration yaml = YamlConfiguration.loadConfiguration(yamlFallbackFile);
        ConfigurationSection section = yaml.getConfigurationSection("islands");
        if (section == null) {
            return;
        }
        for (String idKey : section.getKeys(false)) {
            ConfigurationSection sec = section.getConfigurationSection(idKey);
            if (sec == null) {
                continue;
            }
            try {
                UUID islandId = UUID.fromString(idKey);
                UUID ownerUuid = UUID.fromString(sec.getString("owner_uuid", ""));
                String loadedWorld = sec.getString("world_name", worldName);
                int cx = sec.getInt("center_x");
                int cz = sec.getInt("center_z");
                double hx = sec.getDouble("home.x", cx + 0.5);
                double hy = sec.getDouble("home.y", islandHeight + 1.0);
                double hz = sec.getDouble("home.z", cz + 0.5);
                float yaw = (float) sec.getDouble("home.yaw", 0.0);
                float pitch = (float) sec.getDouble("home.pitch", 0.0);
                boolean isPublic = sec.getBoolean("is_public", false);
                BorderColor borderColor = BorderColor.parse(sec.getString("border_color", "BLUE"));
                double level = sec.getDouble("island_level", 0.0);
                double bank = sec.getDouble("bank_balance", 0.0);
                long createdAt = sec.getLong("created_at", System.currentTimeMillis());

                Island island = new Island(
                        islandId, ownerUuid, loadedWorld, cx, cz,
                        hx, hy, hz, yaw, pitch, isPublic, borderColor,
                        level, bank, createdAt, defaultBorderRadius, maxMembersDefault, defaultSpawnerLimit
                );

                ConfigurationSection upgradesSec = sec.getConfigurationSection("upgrades");
                if (upgradesSec != null) {
                    for (String upKey : upgradesSec.getKeys(false)) {
                        IslandUpgradeType upType = IslandUpgradeType.parse(upKey);
                        if (upType != null) {
                            island.setUpgradeTier(upType, upgradesSec.getInt(upKey, 1));
                        }
                    }
                }
                if (upgradeManager != null) {
                    upgradeManager.applyUpgradesToIsland(island);
                }

                ConfigurationSection membersSec = sec.getConfigurationSection("members");
                if (membersSec != null) {
                    for (String memberKey : membersSec.getKeys(false)) {
                        try {
                            UUID memberId = UUID.fromString(memberKey);
                            IslandRole role = IslandRole.parse(membersSec.getString(memberKey));
                            if (role != null) {
                                island.setMember(memberId, role);
                            }
                        } catch (IllegalArgumentException ignored) {
                            // Pomijamy błędne UUID członka.
                        }
                    }
                }

                ConfigurationSection permsSec = sec.getConfigurationSection("role_permissions");
                if (permsSec != null) {
                    for (String roleKey : permsSec.getKeys(false)) {
                        IslandRole role = IslandRole.parse(roleKey);
                        ConfigurationSection rolePermSec = permsSec.getConfigurationSection(roleKey);
                        if (role == null || rolePermSec == null) {
                            continue;
                        }
                        for (String permKey : rolePermSec.getKeys(false)) {
                            IslandPermission perm = IslandPermission.parse(permKey);
                            if (perm != null) {
                                island.setRolePermission(role, perm, rolePermSec.getBoolean(permKey));
                            }
                        }
                    }
                }

                ConfigurationSection warpsSec = sec.getConfigurationSection("warps");
                if (warpsSec != null) {
                    for (String warpKey : warpsSec.getKeys(false)) {
                        ConfigurationSection warpSec = warpsSec.getConfigurationSection(warpKey);
                        if (warpSec == null) {
                            continue;
                        }
                        Material icon = Material.matchMaterial(warpSec.getString("icon", "ENDER_PEARL"));
                        island.putWarp(new IslandWarp(
                                warpSec.getString("name", warpKey),
                                warpSec.getString("world", loadedWorld),
                                warpSec.getDouble("x"),
                                warpSec.getDouble("y"),
                                warpSec.getDouble("z"),
                                (float) warpSec.getDouble("yaw", 0.0),
                                (float) warpSec.getDouble("pitch", 0.0),
                                icon
                        ));
                    }
                }

                registerIslandInMemory(island);
            } catch (IllegalArgumentException ignored) {
                // Pomijamy niepoprawny rekord wyspy.
            }
        }
    }

    private void saveAllToYaml() {
        FileConfiguration yaml = new YamlConfiguration();
        for (Island island : islandsById.values()) {
            String base = "islands." + island.getIslandId();
            yaml.set(base + ".owner_uuid", island.getOwnerUuid().toString());
            yaml.set(base + ".world_name", island.getWorldName());
            yaml.set(base + ".center_x", island.getCenterX());
            yaml.set(base + ".center_z", island.getCenterZ());
            yaml.set(base + ".home.x", island.getHomeX());
            yaml.set(base + ".home.y", island.getHomeY());
            yaml.set(base + ".home.z", island.getHomeZ());
            yaml.set(base + ".home.yaw", island.getHomeYaw());
            yaml.set(base + ".home.pitch", island.getHomePitch());
            yaml.set(base + ".is_public", island.isPublicAccess());
            yaml.set(base + ".border_color", island.getBorderColor().name());
            yaml.set(base + ".island_level", island.getIslandLevel());
            yaml.set(base + ".bank_balance", island.getBankBalance());
            yaml.set(base + ".created_at", island.getCreatedAt());

            for (Map.Entry<UUID, IslandRole> m : island.getMembersSnapshot().entrySet()) {
                yaml.set(base + ".members." + m.getKey(), m.getValue().name());
            }

            for (Map.Entry<IslandRole, Map<IslandPermission, Boolean>> roleEntry : island.getRolePermissionsSnapshot().entrySet()) {
                for (Map.Entry<IslandPermission, Boolean> permEntry : roleEntry.getValue().entrySet()) {
                    yaml.set(base + ".role_permissions." + roleEntry.getKey().name() + "." + permEntry.getKey().name(),
                            permEntry.getValue());
                }
            }

            for (Map.Entry<String, IslandWarp> w : island.getWarpsSnapshot().entrySet()) {
                IslandWarp warp = w.getValue();
                String wBase = base + ".warps." + w.getKey();
                yaml.set(wBase + ".name", warp.name());
                yaml.set(wBase + ".world", warp.worldName());
                yaml.set(wBase + ".x", warp.x());
                yaml.set(wBase + ".y", warp.y());
                yaml.set(wBase + ".z", warp.z());
                yaml.set(wBase + ".yaw", warp.yaw());
                yaml.set(wBase + ".pitch", warp.pitch());
                yaml.set(wBase + ".icon", warp.icon().name());
            }

            for (Map.Entry<IslandUpgradeType, Integer> up : island.getUpgradeTiersSnapshot().entrySet()) {
                yaml.set(base + ".upgrades." + up.getKey().name(), up.getValue());
            }
        }

        try {
            File parent = yamlFallbackFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            yaml.save(yamlFallbackFile);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Nie udało się zapisać pliku skyblock_data.yml.", e);
        }
    }

    private void registerIslandInMemory(Island island) {
        UUID islandId = island.getIslandId();
        islandsById.put(islandId, island);
        playerToIslandId.put(island.getOwnerUuid(), islandId);
        for (UUID memberId : island.getMembersSnapshot().keySet()) {
            playerToIslandId.put(memberId, islandId);
        }
        int gx = Math.round((float) island.getCenterX() / islandSpacing);
        int gz = Math.round((float) island.getCenterZ() / islandSpacing);
        cellToIslandId.put(packGridKey(gx, gz), islandId);
    }

    private void unregisterIslandFromMemory(Island island) {
        islandsById.remove(island.getIslandId());
        playerToIslandId.remove(island.getOwnerUuid());
        for (UUID memberId : island.getMembersSnapshot().keySet()) {
            playerToIslandId.remove(memberId);
        }
        int gx = Math.round((float) island.getCenterX() / islandSpacing);
        int gz = Math.round((float) island.getCenterZ() / islandSpacing);
        cellToIslandId.remove(packGridKey(gx, gz));
    }

    private static long packGridKey(int gx, int gz) {
        return (((long) gx) << 32) | (gz & 0xFFFFFFFFL);
    }
}
