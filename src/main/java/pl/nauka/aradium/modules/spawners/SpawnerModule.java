package pl.nauka.aradium.modules.spawners;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import pl.nauka.aradium.AradiumPlugin;
import pl.nauka.aradium.core.AbstractModule;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Logger;

/**
 * Moduł Zaawansowanych Spawnerów zarządzający konfiguracją spawners.yml,
 * GUI ulepszeń, danymi PDC oraz obsługą Silk Touch.
 */
public class SpawnerModule extends AbstractModule {

    private SpawnerDataManager dataManager;
    private SpawnerGUI spawnerGUI;

    private String guiTitle = "&8» &6Ulepszenie Spawnera &8(&eTier %tier%&8)";
    private int guiSize = 27;
    private boolean requireSilkTouch = true;
    private final Set<EntityType> allowedEntities = new LinkedHashSet<>();
    private final Map<Integer, SpawnerTierDefinition> tiers = new TreeMap<>();

    public SpawnerModule(AradiumPlugin plugin) {
        super(plugin, "spawners");
    }

    @Override
    protected void onEnable() {
        saveDefaultSpawnersConfig();
        loadConfiguration();

        this.dataManager = new SpawnerDataManager(plugin);
        this.spawnerGUI = new SpawnerGUI(plugin, this);

        registerCommand("spawner", new SpawnerCommand(plugin, this));
        registerListener(new SpawnerListener(plugin, this));
    }

    @Override
    protected void onDisable() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof SpawnerUpgradeHolder) {
                player.closeInventory();
            }
        }
        tiers.clear();
        allowedEntities.clear();
    }

    private void saveDefaultSpawnersConfig() {
        File file = new File(plugin.getDataFolder(), "spawners.yml");
        if (!file.exists() && plugin.getResource("spawners.yml") != null) {
            plugin.saveResource("spawners.yml", false);
        }
    }

    private void loadConfiguration() {
        File file = new File(plugin.getDataFolder(), "spawners.yml");
        FileConfiguration yaml = YamlConfiguration.loadConfiguration(file);

        this.guiTitle = yaml.getString("settings.gui-title", "&8» &6Ulepszenie Spawnera &8(&eTier %tier%&8)");
        this.guiSize = yaml.getInt("settings.gui-size", 27);
        this.requireSilkTouch = yaml.getBoolean("settings.require-silk-touch", true);

        this.allowedEntities.clear();
        for (String rawEntity : yaml.getStringList("settings.allowed-entities")) {
            EntityType parsed = SpawnerDataManager.parseEntityType(rawEntity);
            if (parsed != null && SpawnerDataManager.isSpawnableEntity(parsed)) {
                this.allowedEntities.add(parsed);
            } else {
                plugin.getLogger().warning("Nieprawidłowy typ moba w spawners.yml (allowed-entities): " + rawEntity);
            }
        }

        this.tiers.clear();
        this.tiers.putAll(parseTiers(yaml, plugin.getLogger()));
        plugin.getLogger().info("Załadowano tiery spawnerów: " + this.tiers.size() + ".");
    }

    public static Map<Integer, SpawnerTierDefinition> parseTiers(FileConfiguration yaml, Logger logger) {
        Map<Integer, SpawnerTierDefinition> parsed = new TreeMap<>();
        if (yaml == null) {
            return parsed;
        }
        ConfigurationSection tiersSection = yaml.getConfigurationSection("tiers");
        if (tiersSection == null) {
            return parsed;
        }

        for (String tierKey : tiersSection.getKeys(false)) {
            int tierNumber;
            try {
                tierNumber = Integer.parseInt(tierKey.trim());
            } catch (NumberFormatException e) {
                if (logger != null) {
                    logger.warning("Pominięto nieprawidłowy klucz tieru w spawners.yml: " + tierKey);
                }
                continue;
            }
            if (tierNumber < 1) {
                continue;
            }

            ConfigurationSection sec = tiersSection.getConfigurationSection(tierKey);
            if (sec == null) {
                continue;
            }

            String displayName = sec.getString("name", "&e&lSpawner &8[&7Tier " + tierNumber + "&8]");
            double upgradeCost = Math.max(0.0, sec.getDouble("upgrade-cost", 0.0));
            int minDelay = Math.max(1, sec.getInt("min-spawn-delay", 200));
            int maxDelay = Math.max(1, sec.getInt("max-spawn-delay", 600));
            int spawnCount = Math.max(1, sec.getInt("spawn-count", 4));
            int maxNearby = Math.max(1, sec.getInt("max-nearby-entities", 6));
            int playerRange = Math.max(1, sec.getInt("required-player-range", 16));
            List<String> lore = sec.getStringList("lore");

            Map<Material, Integer> requiredItems = new LinkedHashMap<>();
            ConfigurationSection itemsSec = sec.getConfigurationSection("required-items");
            if (itemsSec != null) {
                for (String matKey : itemsSec.getKeys(false)) {
                    Material material = Material.matchMaterial(matKey.trim().toUpperCase(Locale.ROOT));
                    int amount = itemsSec.getInt(matKey, 0);
                    if (!isValidRequirementMaterial(material) || amount <= 0) {
                        if (logger != null) {
                            logger.warning("Nieprawidłowy wymagany przedmiot '" + matKey + "' w spawners.yml tier " + tierNumber);
                        }
                        continue;
                    }
                    requiredItems.put(material, amount);
                }
            }

            parsed.put(tierNumber, new SpawnerTierDefinition(
                    tierNumber,
                    displayName,
                    upgradeCost,
                    requiredItems,
                    minDelay,
                    maxDelay,
                    spawnCount,
                    maxNearby,
                    playerRange,
                    lore
            ));
        }

        return parsed;
    }

    private static boolean isValidRequirementMaterial(Material material) {
        if (material == null
                || material == Material.AIR
                || material == Material.CAVE_AIR
                || material == Material.VOID_AIR
                || material.isLegacy()) {
            return false;
        }
        return Bukkit.getServer() == null || material.isItem();
    }

    public SpawnerDataManager getDataManager() {
        return dataManager;
    }

    public SpawnerGUI getSpawnerGUI() {
        return spawnerGUI;
    }

    public String getGuiTitle() {
        return guiTitle;
    }

    public int getGuiSize() {
        return guiSize;
    }

    public boolean isRequireSilkTouch() {
        return requireSilkTouch;
    }

    public boolean isAllowedEntity(EntityType entityType) {
        if (entityType == null) {
            return false;
        }
        return allowedEntities.isEmpty() || allowedEntities.contains(entityType);
    }

    public List<String> getAllowedEntityNames() {
        if (allowedEntities.isEmpty()) {
            return List.of("ZOMBIE", "SKELETON", "SPIDER", "CREEPER", "COW", "PIG", "IRON_GOLEM", "BLAZE");
        }
        return allowedEntities.stream().map(EntityType::name).toList();
    }

    public SpawnerTierDefinition getTierDefinition(int tier) {
        return tiers.get(tier);
    }

    public Set<Integer> getAvailableTiers() {
        return Collections.unmodifiableSet(tiers.keySet());
    }
}
