package pl.nauka.aradium;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import pl.nauka.aradium.core.AradiumCommand;
import pl.nauka.aradium.core.ModuleManager;
import pl.nauka.aradium.core.database.DatabaseManager;
import pl.nauka.aradium.modules.armor.ArmorModule;
import pl.nauka.aradium.modules.economy.EconomyModule;
import pl.nauka.aradium.modules.magicitems.MagicItemModule;
import pl.nauka.aradium.modules.oreregions.OreRegionModule;
import pl.nauka.aradium.modules.shop.ShopModule;
import pl.nauka.aradium.modules.skyblock.SkyBlockModule;
import pl.nauka.aradium.modules.spawners.SpawnerModule;

import java.io.File;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Optional;
import java.util.logging.Level;

public class AradiumPlugin extends JavaPlugin {

    private DatabaseManager databaseManager;
    private ModuleManager moduleManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        initializeDatabase();

        this.moduleManager = new ModuleManager(this);
        this.moduleManager.registerModule(new EconomyModule(this));
        this.moduleManager.registerModule(new ShopModule(this));
        this.moduleManager.registerModule(new SpawnerModule(this));
        this.moduleManager.registerModule(new SkyBlockModule(this));
        this.moduleManager.registerModule(new MagicItemModule(this));
        this.moduleManager.registerModule(new OreRegionModule(this));
        this.moduleManager.registerModule(new ArmorModule(this));
        this.moduleManager.enableAll();

        PluginCommand aradiumCmd = getCommand("aradium");
        if (aradiumCmd != null) {
            AradiumCommand executor = new AradiumCommand(this);
            aradiumCmd.setExecutor(executor);
            aradiumCmd.setTabCompleter(executor);
        }

        getLogger().info("Plugin Aradium (Modular Paper 26.3) został pomyślnie włączony!");
    }

    @Override
    public void onDisable() {
        if (moduleManager != null) {
            moduleManager.disableAll();
        }
        if (databaseManager != null) {
            databaseManager.close();
        }
        getLogger().info("Plugin Aradium został bezpiecznie wyłączony.");
    }

    private void initializeDatabase() {
        String rawType = getConfig().getString("database.type", "SQLITE");
        String dbType = rawType != null ? rawType.trim().toUpperCase(Locale.ROOT) : "SQLITE";

        if ("MYSQL".equals(dbType)) {
            String host = getConfig().getString("database.mysql.host", "localhost");
            int port = getConfig().getInt("database.mysql.port", 3306);
            String database = getConfig().getString("database.mysql.database", "aradium");
            String username = getConfig().getString("database.mysql.username", "root");
            String password = getConfig().getString("database.mysql.password", "");
            this.databaseManager = new DatabaseManager(host, port, database, username, password, getLogger());
        } else {
            String fileName = getConfig().getString("database.file", "aradium.db");
            File sqliteFile = new File(getDataFolder(), fileName != null && !fileName.isBlank() ? fileName : "aradium.db");
            this.databaseManager = new DatabaseManager(sqliteFile, getLogger());
        }

        try {
            this.databaseManager.initialize();
            getLogger().info("Połączono z bazą danych (" + this.databaseManager.getType() + ").");
        } catch (SQLException e) {
            getLogger().log(Level.WARNING, "Nie udało się zainicjalizować bazy danych (" + dbType
                    + ") — ekonomia przełączy się automatycznie na zapis YAML.", e);
            this.databaseManager.close();
        }
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public ModuleManager getModuleManager() {
        return moduleManager;
    }

    public Optional<EconomyModule> getEconomyModule() {
        return moduleManager != null ? moduleManager.getModule(EconomyModule.class) : Optional.empty();
    }

    public Optional<ShopModule> getShopModule() {
        return moduleManager != null ? moduleManager.getModule(ShopModule.class) : Optional.empty();
    }

    public Optional<SpawnerModule> getSpawnerModule() {
        return moduleManager != null ? moduleManager.getModule(SpawnerModule.class) : Optional.empty();
    }

    public Optional<SkyBlockModule> getSkyBlockModule() {
        return moduleManager != null ? moduleManager.getModule(SkyBlockModule.class) : Optional.empty();
    }

    public Optional<MagicItemModule> getMagicItemModule() {
        return moduleManager != null ? moduleManager.getModule(MagicItemModule.class) : Optional.empty();
    }

    public Optional<OreRegionModule> getOreRegionModule() {
        return moduleManager != null ? moduleManager.getModule(OreRegionModule.class) : Optional.empty();
    }

    public Optional<ArmorModule> getArmorModule() {
        return moduleManager != null ? moduleManager.getModule(ArmorModule.class) : Optional.empty();
    }
}
