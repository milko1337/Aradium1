package pl.nauka.aradium.modules.skyblock.level;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import pl.nauka.aradium.AradiumPlugin;

import java.io.File;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Menedżer punktacji bloków ładowanej z pliku blocks-value.yml na potrzeby poziomu wyspy.
 */
public class BlockValueManager {

    private final AradiumPlugin plugin;
    private final File file;
    private final Logger logger;

    private double pointsPerLevel = 1.0;
    private Map<Material, Double> blockValues = Collections.emptyMap();

    public BlockValueManager(AradiumPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.file = new File(plugin.getDataFolder(), "blocks-value.yml");
        this.logger = plugin.getLogger();
        reload();
    }

    public synchronized void reload() {
        if (plugin != null && !file.exists()) {
            plugin.saveResource("blocks-value.yml", false);
        }
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        reload(config);
    }

    public synchronized void reload(FileConfiguration config) {
        if (config == null) {
            this.pointsPerLevel = 1.0;
            this.blockValues = Collections.emptyMap();
            return;
        }
        this.pointsPerLevel = Math.max(0.01, config.getDouble("points_per_level", 1.0));
        this.blockValues = Collections.unmodifiableMap(parseBlockValues(config, logger));
    }

    public static Map<Material, Double> parseBlockValues(FileConfiguration config, Logger logger) {
        EnumMap<Material, Double> map = new EnumMap<>(Material.class);
        if (config == null) {
            return map;
        }
        ConfigurationSection section = config.getConfigurationSection("blocks");
        if (section == null) {
            return map;
        }

        for (String key : section.getKeys(false)) {
            double points = section.getDouble(key, 0.0);
            if (points <= 0.0) {
                continue;
            }
            try {
                Material material = Material.valueOf(key.trim().toUpperCase(Locale.ROOT));
                map.put(material, points);
            } catch (IllegalArgumentException ignored) {
                if (logger != null) {
                    logger.warning("Nieznany materiał w blocks-value.yml: " + key);
                }
            }
        }
        return map;
    }

    public synchronized double getBlockPoints(Material material) {
        if (material == null) {
            return 0.0;
        }
        return blockValues.getOrDefault(material, 0.0);
    }

    public synchronized double getPointsPerLevel() {
        return pointsPerLevel;
    }

    public synchronized double calculateLevel(double totalPoints) {
        if (totalPoints <= 0.0) {
            return 0.0;
        }
        double raw = totalPoints / pointsPerLevel;
        return Math.round(raw * 100.0) / 100.0;
    }

    public synchronized Map<Material, Double> getBlockValuesSnapshot() {
        return blockValues;
    }
}
