package pl.nauka.aradium.modules.oreregions;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;
import pl.nauka.aradium.AradiumPlugin;
import pl.nauka.aradium.core.AbstractModule;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

public class OreRegionModule extends AbstractModule {

    private static final String DEFAULT_ZONE = "default";
    private static final String PENDING_SECTION = "pending_regens";

    public record BlockKey(String worldName, int x, int y, int z) {

        public static BlockKey of(Block block) {
            return new BlockKey(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
        }

        public Block resolveBlock() {
            World world = Bukkit.getWorld(worldName);
            return world != null ? world.getBlockAt(x, y, z) : null;
        }

        public String toStorageKey() {
            String encodedWorld = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(worldName.getBytes(StandardCharsets.UTF_8));
            return encodedWorld + "_" + x + "_" + y + "_" + z;
        }
    }

    private record PendingRegen(Material originalType, BukkitTask task) {
    }

    private File regionsFile;
    private FileConfiguration regionsConfig;
    private int regenDelaySeconds = 10;

    private final Map<Material, Map<String, CuboidRegion>> regionCache = new EnumMap<>(Material.class);
    private final Map<BlockKey, PendingRegen> pendingRegens = new HashMap<>();

    public OreRegionModule(AradiumPlugin plugin) {
        super(plugin, "ore_regions");
    }

    @Override
    protected void onEnable() {
        this.regenDelaySeconds = Math.max(1, plugin.getConfig().getInt("ore_regions.regen_delay_seconds", 10));
        setupRegionsFile();
        loadRegionCache();
        restorePersistedPendingBlocks();
        registerCommand("oreregion", new OreRegionCommand(plugin, this));
        registerListener(new OreBreakListener(this));
    }

    @Override
    protected void onDisable() {
        restoreAllPendingBlocks();
        regionCache.clear();
    }

    private void setupRegionsFile() {
        regionsFile = new File(plugin.getDataFolder(), "regions.yml");
        if (!regionsFile.exists()) {
            plugin.saveResource("regions.yml", false);
        }
        regionsConfig = YamlConfiguration.loadConfiguration(regionsFile);
    }

    private void loadRegionCache() {
        regionCache.clear();
        ConfigurationSection regionsSection = regionsConfig.getConfigurationSection("regions");
        if (regionsSection == null) {
            return;
        }

        boolean migrated = false;
        for (String materialName : regionsSection.getKeys(false)) {
            Material material = Material.matchMaterial(materialName);
            if (material == null || !material.isBlock()) {
                plugin.getLogger().warning("Nieznany blok '" + materialName + "' w regions.yml — pomijam.");
                continue;
            }

            ConfigurationSection matSection = regionsSection.getConfigurationSection(materialName);
            if (matSection == null) {
                continue;
            }

            // Migracja starszego formatu (regions.<MATERIAL>.min/max) do strefy "default"
            if (matSection.contains("min") || matSection.contains("max")) {
                if (!matSection.contains(DEFAULT_ZONE)) {
                    if (matSection.contains("min")) {
                        copyCorner(matSection.getConfigurationSection("min"), matSection.createSection(DEFAULT_ZONE + ".min"));
                    }
                    if (matSection.contains("max")) {
                        copyCorner(matSection.getConfigurationSection("max"), matSection.createSection(DEFAULT_ZONE + ".max"));
                    }
                }
                matSection.set("min", null);
                matSection.set("max", null);
                migrated = true;
            }

            for (String zoneName : matSection.getKeys(false)) {
                ConfigurationSection zoneSection = matSection.getConfigurationSection(zoneName);
                if (zoneSection == null) {
                    continue;
                }
                CuboidRegion zoneRegion = readRegionFromSection(zoneSection);
                if (zoneRegion != null) {
                    regionCache.computeIfAbsent(material, ignored -> new LinkedHashMap<>())
                            .put(zoneName.toLowerCase(Locale.ROOT), zoneRegion);
                }
            }
        }

        if (migrated) {
            saveRegionsConfig();
        }
    }

    private void copyCorner(ConfigurationSection source, ConfigurationSection target) {
        if (source == null || target == null) {
            return;
        }
        target.set("world", source.getString("world"));
        target.set("x", source.getInt("x"));
        target.set("y", source.getInt("y"));
        target.set("z", source.getInt("z"));
    }

    private CuboidRegion readRegionFromSection(ConfigurationSection section) {
        ConfigurationSection minSec = section.getConfigurationSection("min");
        ConfigurationSection maxSec = section.getConfigurationSection("max");
        if (minSec == null || maxSec == null) {
            return null;
        }

        String minWorld = minSec.getString("world");
        String maxWorld = maxSec.getString("world");
        if (minWorld == null || minWorld.isBlank() || !minWorld.equals(maxWorld)) {
            return null;
        }

        return CuboidRegion.ofCoordinates(
                minWorld,
                minSec.getInt("x"), minSec.getInt("y"), minSec.getInt("z"),
                maxSec.getInt("x"), maxSec.getInt("y"), maxSec.getInt("z")
        );
    }

    public boolean setMin(Material ore, Location loc, String zoneName) {
        return saveCorner(ore, zoneName, "min", loc);
    }

    public boolean setMax(Material ore, Location loc, String zoneName) {
        return saveCorner(ore, zoneName, "max", loc);
    }

    private boolean saveCorner(Material ore, String zoneName, String corner, Location loc) {
        if (ore == null || loc == null || loc.getWorld() == null) {
            return false;
        }
        String normalizedZone = (zoneName == null || zoneName.isBlank())
                ? DEFAULT_ZONE
                : zoneName.toLowerCase(Locale.ROOT);
        String basePath = "regions." + ore.name() + "." + normalizedZone + "." + corner;

        regionsConfig.set(basePath + ".world", loc.getWorld().getName());
        regionsConfig.set(basePath + ".x", loc.getBlockX());
        regionsConfig.set(basePath + ".y", loc.getBlockY());
        regionsConfig.set(basePath + ".z", loc.getBlockZ());
        saveRegionsConfig();

        ConfigurationSection zoneSection = regionsConfig.getConfigurationSection("regions." + ore.name() + "." + normalizedZone);
        if (zoneSection != null) {
            CuboidRegion updated = readRegionFromSection(zoneSection);
            if (updated != null) {
                regionCache.computeIfAbsent(ore, ignored -> new LinkedHashMap<>())
                        .put(normalizedZone, updated);
            }
        }
        return true;
    }

    public Set<String> getZoneNames(Material ore) {
        if (ore == null) {
            return Set.of();
        }
        Map<String, CuboidRegion> zones = regionCache.get(ore);
        if (zones == null || zones.isEmpty()) {
            return Set.of();
        }
        return Collections.unmodifiableSet(zones.keySet());
    }

    public boolean isInRegion(Material ore, Location loc) {
        Map<String, CuboidRegion> zones = regionCache.get(ore);
        if (zones == null || zones.isEmpty()) {
            return false;
        }
        for (CuboidRegion region : zones.values()) {
            if (region.contains(loc)) {
                return true;
            }
        }
        return false;
    }

    public static Material resolvePendingMaterial(Material existingPending, Material currentBreak) {
        return existingPending != null ? existingPending : currentBreak;
    }

    public void handleBlockBreak(Block block) {
        Material originalType = block.getType();
        if (!isInRegion(originalType, block.getLocation())) {
            return;
        }

        BlockKey blockKey = BlockKey.of(block);
        PendingRegen previous = pendingRegens.remove(blockKey);
        if (previous != null) {
            previous.task().cancel();
        }

        Material targetMaterial = resolvePendingMaterial(previous != null ? previous.originalType() : null, originalType);
        persistPendingRegen(blockKey, targetMaterial);

        long delayTicks = regenDelaySeconds * 20L;
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            pendingRegens.remove(blockKey);
            removePersistedPendingRegen(blockKey);
            Block targetBlock = blockKey.resolveBlock();
            if (targetBlock != null && targetBlock.getType().isAir()) {
                targetBlock.setType(targetMaterial);
            }
        }, delayTicks);

        pendingRegens.put(blockKey, new PendingRegen(targetMaterial, task));
    }

    private void persistPendingRegen(BlockKey key, Material material) {
        String path = PENDING_SECTION + "." + key.toStorageKey();
        regionsConfig.set(path + ".world", key.worldName());
        regionsConfig.set(path + ".x", key.x());
        regionsConfig.set(path + ".y", key.y());
        regionsConfig.set(path + ".z", key.z());
        regionsConfig.set(path + ".material", material.name());
        saveRegionsConfig();
    }

    private void removePersistedPendingRegen(BlockKey key) {
        String path = PENDING_SECTION + "." + key.toStorageKey();
        if (regionsConfig.contains(path)) {
            regionsConfig.set(path, null);
            ConfigurationSection section = regionsConfig.getConfigurationSection(PENDING_SECTION);
            if (section == null || section.getKeys(false).isEmpty()) {
                regionsConfig.set(PENDING_SECTION, null);
            }
            saveRegionsConfig();
        }
    }

    private void restorePersistedPendingBlocks() {
        ConfigurationSection section = regionsConfig.getConfigurationSection(PENDING_SECTION);
        if (section == null) {
            return;
        }

        boolean changed = false;
        for (String key : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(key);
            if (entry == null) {
                section.set(key, null);
                changed = true;
                continue;
            }

            String worldName = entry.getString("world");
            String matName = entry.getString("material");
            Material material = matName != null ? Material.matchMaterial(matName) : null;
            if (worldName == null || worldName.isBlank() || material == null || !material.isBlock()) {
                section.set(key, null);
                changed = true;
                continue;
            }

            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                continue;
            }

            Block block = world.getBlockAt(entry.getInt("x"), entry.getInt("y"), entry.getInt("z"));
            if (block.getType().isAir()) {
                block.setType(material);
            }
            section.set(key, null);
            changed = true;
        }

        if (section.getKeys(false).isEmpty()) {
            regionsConfig.set(PENDING_SECTION, null);
            changed = true;
        }
        if (changed) {
            saveRegionsConfig();
        }
    }

    private void restoreAllPendingBlocks() {
        boolean configChanged = false;
        for (Map.Entry<BlockKey, PendingRegen> entry : pendingRegens.entrySet()) {
            entry.getValue().task().cancel();
            BlockKey key = entry.getKey();
            Block block = key.resolveBlock();
            if (block != null) {
                if (block.getType().isAir()) {
                    block.setType(entry.getValue().originalType());
                }
                String path = PENDING_SECTION + "." + key.toStorageKey();
                if (regionsConfig.contains(path)) {
                    regionsConfig.set(path, null);
                    configChanged = true;
                }
            }
        }
        pendingRegens.clear();

        if (configChanged) {
            ConfigurationSection section = regionsConfig.getConfigurationSection(PENDING_SECTION);
            if (section == null || section.getKeys(false).isEmpty()) {
                regionsConfig.set(PENDING_SECTION, null);
            }
            saveRegionsConfig();
        }
    }

    private void saveRegionsConfig() {
        try {
            regionsConfig.save(regionsFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Nie udało się zapisać pliku regions.yml", e);
        }
    }
}
