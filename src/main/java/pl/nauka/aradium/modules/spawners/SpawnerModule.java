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
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
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
 * 4 niezależnymi ścieżkami ulepszeń, sklepem mobów oraz obsługą PDC / Silk Touch.
 */
public class SpawnerModule extends AbstractModule {

    public static final Set<EntityType> FORBIDDEN_BOSS_ENTITIES = Set.of(
            EntityType.WITHER,
            EntityType.ENDER_DRAGON,
            EntityType.WARDEN
    );

    private SpawnerDataManager dataManager;
    private SpawnerGUI spawnerGUI;

    private String guiTitle = "&8» &6Panel Spawnera &8(&e%entity%&8)";
    private String mobGuiTitle = "&8» &6Wybór Moba &8(&e%page%&8/&e%pages%&8)";
    private int guiSize = 36;
    private int mobGuiSize = 54;
    private boolean requireSilkTouch = true;

    private final Set<EntityType> allowedEntities = new LinkedHashSet<>();
    private final Map<Integer, SpawnerTierDefinition> tiers = new TreeMap<>();
    private final Map<SpawnerUpgradeTrack, Map<Integer, SpawnerTrackLevelDefinition>> upgradeTracks =
            new EnumMap<>(SpawnerUpgradeTrack.class);
    private final Map<EntityType, SpawnerMobDefinition> mobDefinitions = new LinkedHashMap<>();

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
        upgradeTracks.clear();
        mobDefinitions.clear();
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

        this.guiTitle = yaml.getString("settings.gui-title", "&8» &6Panel Spawnera &8(&e%entity%&8)");
        this.mobGuiTitle = yaml.getString("settings.mob-gui-title", "&8» &6Wybór Moba &8(&e%page%&8/&e%pages%&8)");
        this.guiSize = yaml.getInt("settings.gui-size", 36);
        this.mobGuiSize = yaml.getInt("settings.mob-gui-size", 54);
        this.requireSilkTouch = yaml.getBoolean("settings.require-silk-touch", true);

        this.allowedEntities.clear();
        for (String rawEntity : yaml.getStringList("settings.allowed-entities")) {
            EntityType parsed = SpawnerDataManager.parseEntityType(rawEntity);
            if (parsed != null && SpawnerDataManager.isSpawnableEntity(parsed) && !FORBIDDEN_BOSS_ENTITIES.contains(parsed)) {
                this.allowedEntities.add(parsed);
            } else {
                plugin.getLogger().warning("Nieprawidłowy lub niedozwolony typ moba w spawners.yml (allowed-entities): " + rawEntity);
            }
        }

        this.tiers.clear();
        this.tiers.putAll(parseTiers(yaml, plugin.getLogger()));

        this.upgradeTracks.clear();
        this.upgradeTracks.putAll(parseUpgradeTracks(yaml, plugin.getLogger()));

        this.mobDefinitions.clear();
        this.mobDefinitions.putAll(parseMobDefinitions(yaml, plugin.getLogger()));
        this.allowedEntities.addAll(this.mobDefinitions.keySet());

        plugin.getLogger().info("Załadowano spawner: " + this.tiers.size() + " tierów legacy, "
                + this.upgradeTracks.size() + " ścieżek ulepszeń, "
                + this.mobDefinitions.size() + " mobów.");
    }

    public static Map<SpawnerUpgradeTrack, Map<Integer, SpawnerTrackLevelDefinition>> parseUpgradeTracks(
            FileConfiguration yaml,
            Logger logger
    ) {
        Map<SpawnerUpgradeTrack, Map<Integer, SpawnerTrackLevelDefinition>> result =
                new EnumMap<>(SpawnerUpgradeTrack.class);
        for (SpawnerUpgradeTrack track : SpawnerUpgradeTrack.values()) {
            result.put(track, new TreeMap<>());
        }
        if (yaml == null) {
            return result;
        }

        ConfigurationSection upgradesRoot = yaml.getConfigurationSection("upgrades");
        if (upgradesRoot == null) {
            return result;
        }

        for (SpawnerUpgradeTrack track : SpawnerUpgradeTrack.values()) {
            ConfigurationSection trackSection = upgradesRoot.getConfigurationSection(track.configKey());
            if (trackSection == null) {
                continue;
            }
            Map<Integer, SpawnerTrackLevelDefinition> levels = new TreeMap<>();
            for (String levelKey : trackSection.getKeys(false)) {
                int levelNumber;
                try {
                    levelNumber = Integer.parseInt(levelKey.trim());
                } catch (NumberFormatException e) {
                    if (logger != null) {
                        logger.warning("Pominięto nieprawidłowy poziom ścieżki " + track.configKey() + ": " + levelKey);
                    }
                    continue;
                }
                if (levelNumber < track.defaultLevel()) {
                    continue;
                }

                ConfigurationSection sec = trackSection.getConfigurationSection(levelKey);
                if (sec == null) {
                    continue;
                }

                String displayName = sec.getString("name", track.displayName() + " &8[&7Poziom " + levelNumber + "&8]");
                double upgradeCost = Math.max(0.0, sec.getDouble("upgrade-cost", 0.0));
                int minDelay = Math.max(1, sec.getInt("min-spawn-delay", 200));
                int maxDelay = Math.max(1, sec.getInt("max-spawn-delay", 600));
                int spawnCount = Math.max(1, sec.getInt("spawn-count", 2));
                int maxNearby = Math.max(1, sec.getInt("max-nearby-entities", 6));
                int playerRange = Math.max(1, sec.getInt("required-player-range", 16));
                boolean ignoreLight = sec.getBoolean("ignore-light", track == SpawnerUpgradeTrack.LIGHT_IMMUNITY && levelNumber >= 1);
                List<String> lore = sec.getStringList("lore");
                Map<Material, Integer> requiredItems = parseRequiredItems(
                        sec.getConfigurationSection("required-items"),
                        "upgrades." + track.configKey() + "." + levelNumber,
                        logger
                );

                levels.put(levelNumber, new SpawnerTrackLevelDefinition(
                        track,
                        levelNumber,
                        displayName,
                        upgradeCost,
                        requiredItems,
                        minDelay,
                        maxDelay,
                        spawnCount,
                        maxNearby,
                        playerRange,
                        ignoreLight,
                        lore
                ));
            }
            result.put(track, Collections.unmodifiableMap(levels));
        }

        return result;
    }

    public static Map<EntityType, SpawnerMobDefinition> parseMobDefinitions(FileConfiguration yaml, Logger logger) {
        Map<EntityType, SpawnerMobDefinition> result = new LinkedHashMap<>();
        if (yaml == null) {
            return result;
        }
        ConfigurationSection mobsSection = yaml.getConfigurationSection("mobs");
        if (mobsSection == null) {
            return result;
        }

        for (String mobKey : mobsSection.getKeys(false)) {
            EntityType entityType = SpawnerDataManager.parseEntityType(mobKey);
            if (entityType == null || !SpawnerDataManager.isSpawnableEntity(entityType) || FORBIDDEN_BOSS_ENTITIES.contains(entityType)) {
                if (logger != null) {
                    logger.warning("Pominięto nieprawidłowy lub zablokowany mob w spawners.yml (mobs): " + mobKey);
                }
                continue;
            }

            ConfigurationSection sec = mobsSection.getConfigurationSection(mobKey);
            if (sec == null) {
                continue;
            }

            String displayName = sec.getString("name", SpawnerDataManager.formatEntityName(entityType));
            String rawIcon = sec.getString("icon", entityType.name() + "_SPAWN_EGG");
            Material icon = Material.matchMaterial(rawIcon.trim().toUpperCase(Locale.ROOT));
            if (!isValidRequirementMaterial(icon)) {
                if (logger != null) {
                    logger.warning("Nieprawidłowa ikona '" + rawIcon + "' dla moba " + mobKey + " w spawners.yml");
                }
                continue;
            }

            double cost = entityType == EntityType.PIG ? 0.0 : Math.max(0.0, sec.getDouble("cost", 0.0));
            result.put(entityType, new SpawnerMobDefinition(entityType, displayName, icon, cost));
        }
        return result;
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

            Map<Material, Integer> requiredItems = parseRequiredItems(
                    sec.getConfigurationSection("required-items"),
                    "tier " + tierNumber,
                    logger
            );

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

    private static Map<Material, Integer> parseRequiredItems(
            ConfigurationSection itemsSec,
            String contextLabel,
            Logger logger
    ) {
        Map<Material, Integer> requiredItems = new LinkedHashMap<>();
        if (itemsSec == null) {
            return requiredItems;
        }
        for (String matKey : itemsSec.getKeys(false)) {
            Material material = Material.matchMaterial(matKey.trim().toUpperCase(Locale.ROOT));
            int amount = itemsSec.getInt(matKey, 0);
            if (!isValidRequirementMaterial(material) || amount <= 0) {
                if (logger != null) {
                    logger.warning("Nieprawidłowy wymagany przedmiot '" + matKey + "' w spawners.yml (" + contextLabel + ")");
                }
                continue;
            }
            requiredItems.put(material, amount);
        }
        return requiredItems;
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

    public String getMobGuiTitle() {
        return mobGuiTitle;
    }

    public int getGuiSize() {
        return guiSize;
    }

    public int getMobGuiSize() {
        return mobGuiSize;
    }

    public boolean isRequireSilkTouch() {
        return requireSilkTouch;
    }

    public boolean isAllowedEntity(EntityType entityType) {
        if (entityType == null || FORBIDDEN_BOSS_ENTITIES.contains(entityType)) {
            return false;
        }
        if (mobDefinitions.containsKey(entityType)) {
            return true;
        }
        return allowedEntities.isEmpty() || allowedEntities.contains(entityType);
    }

    public List<String> getAllowedEntityNames() {
        if (!mobDefinitions.isEmpty()) {
            return mobDefinitions.keySet().stream().map(EntityType::name).toList();
        }
        if (allowedEntities.isEmpty()) {
            return List.of("PIG", "ZOMBIE", "SKELETON", "SPIDER", "CREEPER", "COW", "IRON_GOLEM", "BLAZE");
        }
        return allowedEntities.stream().map(EntityType::name).toList();
    }

    public SpawnerTierDefinition getTierDefinition(int tier) {
        return tiers.get(tier);
    }

    public Set<Integer> getAvailableTiers() {
        return Collections.unmodifiableSet(tiers.keySet());
    }

    public SpawnerTrackLevelDefinition getTrackLevelDefinition(SpawnerUpgradeTrack track, int level) {
        if (track == null) {
            return null;
        }
        Map<Integer, SpawnerTrackLevelDefinition> levels = upgradeTracks.get(track);
        return levels != null ? levels.get(level) : null;
    }

    public int getMaxTrackLevel(SpawnerUpgradeTrack track) {
        if (track == null) {
            return 1;
        }
        Map<Integer, SpawnerTrackLevelDefinition> levels = upgradeTracks.get(track);
        if (levels == null || levels.isEmpty()) {
            return track.defaultLevel();
        }
        int max = track.defaultLevel();
        for (int lvl : levels.keySet()) {
            if (lvl > max) {
                max = lvl;
            }
        }
        return max;
    }

    public Collection<SpawnerMobDefinition> getMobDefinitions() {
        return Collections.unmodifiableCollection(mobDefinitions.values());
    }

    public SpawnerMobDefinition getMobDefinition(EntityType entityType) {
        return entityType != null ? mobDefinitions.get(entityType) : null;
    }

    public String formatMobDisplayName(EntityType entityType) {
        SpawnerMobDefinition def = getMobDefinition(entityType);
        return def != null ? def.displayName() : SpawnerDataManager.formatEntityName(entityType);
    }
}
