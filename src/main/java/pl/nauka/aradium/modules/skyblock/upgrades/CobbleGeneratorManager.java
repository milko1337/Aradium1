package pl.nauka.aradium.modules.skyblock.upgrades;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import pl.nauka.aradium.AradiumPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/**
 * Menedżer generatora surowców z lawy i wody (cobble-generator.yml)
 * zarządzający wagami dropów dla poszczególnych tierów wyspy.
 */
public class CobbleGeneratorManager {

    public record GeneratorTierConfig(
            int tier,
            String displayName,
            double cost,
            int dropMultiplier,
            Map<Material, Double> weights,
            double totalWeight
    ) {
        public GeneratorTierConfig {
            weights = weights != null ? Collections.unmodifiableMap(new LinkedHashMap<>(weights)) : Map.of();
        }
    }

    private final AradiumPlugin plugin;
    private final File file;
    private final Logger logger;

    private final TreeMap<Integer, GeneratorTierConfig> tiers = new TreeMap<>();
    private final EnumMap<Material, String> materialNames = new EnumMap<>(Material.class);
    private final EnumSet<Material> generatableMaterials = EnumSet.noneOf(Material.class);

    public CobbleGeneratorManager(AradiumPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.file = new File(plugin.getDataFolder(), "cobble-generator.yml");
        this.logger = plugin.getLogger();
        reload();
    }

    public CobbleGeneratorManager(Logger logger) {
        this.plugin = null;
        this.file = null;
        this.logger = Objects.requireNonNull(logger, "logger");
        loadDefaults();
    }

    public synchronized void reload() {
        if (plugin != null && file != null) {
            if (!file.exists() && plugin.getResource("cobble-generator.yml") != null) {
                plugin.saveResource("cobble-generator.yml", false);
            }
            FileConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            reload(yaml);
        } else {
            loadDefaults();
        }
    }

    public synchronized void reload(FileConfiguration yaml) {
        loadDefaults();
        if (yaml == null) {
            return;
        }

        ConfigurationSection namesSec = yaml.getConfigurationSection("material-names");
        if (namesSec != null) {
            for (String key : namesSec.getKeys(false)) {
                Material mat = parseGeneratableMaterial(key, "material-names");
                String label = namesSec.getString(key);
                if (mat != null && label != null && !label.isBlank()) {
                    materialNames.put(mat, label);
                }
            }
        }

        ConfigurationSection tiersSec = yaml.getConfigurationSection("tiers");
        if (tiersSec == null) {
            return;
        }

        TreeMap<Integer, GeneratorTierConfig> parsedTiers = new TreeMap<>();
        for (String tierKey : tiersSec.getKeys(false)) {
            int tierNumber;
            try {
                tierNumber = Integer.parseInt(tierKey.trim());
            } catch (NumberFormatException ignored) {
                logger.warning("Niepoprawny numer tieru w cobble-generator.yml: " + tierKey);
                continue;
            }
            if (tierNumber < 1) {
                continue;
            }

            ConfigurationSection sec = tiersSec.getConfigurationSection(tierKey);
            if (sec == null) {
                continue;
            }

            String displayName = sec.getString("name", "&7Poziom " + tierNumber);
            double cost = Math.max(0.0, sec.getDouble("cost", 0.0));
            int multiplier = Math.max(1, sec.getInt("drop-multiplier", tierNumber));

            ConfigurationSection dropsSec = sec.getConfigurationSection("drops");
            Map<Material, Double> weights = new LinkedHashMap<>();
            double sum = 0.0;
            if (dropsSec != null) {
                for (String matKey : dropsSec.getKeys(false)) {
                    double weight = dropsSec.getDouble(matKey, 0.0);
                    if (weight <= 0.0) {
                        continue;
                    }
                    Material material = parseGeneratableMaterial(matKey, "tiers." + tierNumber + ".drops");
                    if (material == null) {
                        continue;
                    }
                    weights.put(material, weight);
                    sum += weight;
                }
            }

            if (weights.isEmpty() || sum <= 0.0) {
                logger.warning("Tier " + tierNumber + " w cobble-generator.yml nie ma poprawnych dropów — użyto COBBLESTONE 100%.");
                weights.put(Material.COBBLESTONE, 100.0);
                sum = 100.0;
            }

            parsedTiers.put(tierNumber, new GeneratorTierConfig(tierNumber, displayName, cost, multiplier, weights, sum));
        }

        if (!parsedTiers.isEmpty()) {
            tiers.clear();
            tiers.putAll(parsedTiers);
            rebuildGeneratableMaterials();
        }
    }

    private void loadDefaults() {
        materialNames.clear();
        materialNames.put(Material.COBBLESTONE, "&7Bruk");
        materialNames.put(Material.STONE, "&7Kamień");
        materialNames.put(Material.COAL_ORE, "&8Ruda węgla");
        materialNames.put(Material.COPPER_ORE, "&6Ruda miedzi");
        materialNames.put(Material.IRON_ORE, "&fRuda żelaza");
        materialNames.put(Material.LAPIS_ORE, "&9Ruda lazurytu");
        materialNames.put(Material.REDSTONE_ORE, "&cRuda redstone");
        materialNames.put(Material.GOLD_ORE, "&eRuda złota");
        materialNames.put(Material.DIAMOND_ORE, "&bRuda diamentu");
        materialNames.put(Material.EMERALD_ORE, "&aRuda szmaragdu");
        materialNames.put(Material.ANCIENT_DEBRIS, "&4Pradawne zgliszcza");

        tiers.clear();
        registerDefaultTier(1, "&7Poziom 1 &8(Podstawowy)", 0.0, 1, Map.of(
                Material.COBBLESTONE, 74.0,
                Material.COAL_ORE, 12.0,
                Material.COPPER_ORE, 7.0,
                Material.IRON_ORE, 5.0,
                Material.GOLD_ORE, 1.5,
                Material.DIAMOND_ORE, 0.5
        ));
        registerDefaultTier(2, "&ePoziom 2 &8(Ulepszony)", 1500.0, 2, Map.of(
                Material.COBBLESTONE, 62.0,
                Material.COAL_ORE, 14.0,
                Material.COPPER_ORE, 8.0,
                Material.IRON_ORE, 8.5,
                Material.LAPIS_ORE, 3.5,
                Material.GOLD_ORE, 2.5,
                Material.DIAMOND_ORE, 1.2,
                Material.EMERALD_ORE, 0.3
        ));
        registerDefaultTier(3, "&6Poziom 3 &8(Zaawansowany)", 5000.0, 3, Map.of(
                Material.COBBLESTONE, 50.0,
                Material.COAL_ORE, 14.0,
                Material.COPPER_ORE, 8.0,
                Material.IRON_ORE, 11.0,
                Material.LAPIS_ORE, 5.5,
                Material.REDSTONE_ORE, 5.0,
                Material.GOLD_ORE, 4.0,
                Material.DIAMOND_ORE, 1.8,
                Material.EMERALD_ORE, 0.7
        ));
        registerDefaultTier(4, "&bPoziom 4 &8(Mistrzowski)", 15000.0, 4, Map.of(
                Material.COBBLESTONE, 38.0,
                Material.COAL_ORE, 13.0,
                Material.COPPER_ORE, 7.0,
                Material.IRON_ORE, 14.0,
                Material.LAPIS_ORE, 7.5,
                Material.REDSTONE_ORE, 7.5,
                Material.GOLD_ORE, 7.0,
                Material.DIAMOND_ORE, 3.8,
                Material.EMERALD_ORE, 2.0,
                Material.ANCIENT_DEBRIS, 0.2
        ));
        registerDefaultTier(5, "&dPoziom 5 &8(Legendarny)", 35000.0, 5, Map.of(
                Material.COBBLESTONE, 25.0,
                Material.COAL_ORE, 12.0,
                Material.COPPER_ORE, 6.0,
                Material.IRON_ORE, 16.0,
                Material.LAPIS_ORE, 9.0,
                Material.REDSTONE_ORE, 9.0,
                Material.GOLD_ORE, 10.0,
                Material.DIAMOND_ORE, 7.0,
                Material.EMERALD_ORE, 5.0,
                Material.ANCIENT_DEBRIS, 1.0
        ));
        rebuildGeneratableMaterials();
    }

    private void registerDefaultTier(
            int tier,
            String name,
            double cost,
            int multiplier,
            Map<Material, Double> rawWeights
    ) {
        Map<Material, Double> ordered = new LinkedHashMap<>(rawWeights);
        double total = 0.0;
        for (double w : ordered.values()) {
            total += w;
        }
        tiers.put(tier, new GeneratorTierConfig(tier, name, cost, multiplier, ordered, total));
    }

    private void rebuildGeneratableMaterials() {
        generatableMaterials.clear();
        generatableMaterials.add(Material.COBBLESTONE);
        generatableMaterials.add(Material.STONE);
        for (GeneratorTierConfig cfg : tiers.values()) {
            generatableMaterials.addAll(cfg.weights().keySet());
        }
    }

    private Material parseGeneratableMaterial(String rawName, String sectionContext) {
        if (rawName == null || rawName.isBlank()) {
            return null;
        }
        Material mat = Material.matchMaterial(rawName.trim().toUpperCase(Locale.ROOT));
        if (mat == null || mat.isLegacy() || (Bukkit.getServer() != null && !mat.isBlock())) {
            logger.warning("Nieprawidłowy materiał bloku '" + rawName + "' w cobble-generator.yml (" + sectionContext + ")");
            return null;
        }
        return mat;
    }

    public Material rollBlockForTier(int tier) {
        return rollBlockForTier(tier, ThreadLocalRandom.current().nextDouble());
    }

    public synchronized Material rollBlockForTier(int tier, double randomZeroToOne) {
        GeneratorTierConfig config = getTierConfig(tier);
        if (config == null || config.weights().isEmpty() || config.totalWeight() <= 0.0) {
            return Material.COBBLESTONE;
        }
        double clamped = Math.max(0.0, Math.min(0.999999999, randomZeroToOne));
        double target = clamped * config.totalWeight();
        double cumulative = 0.0;
        Material lastMaterial = Material.COBBLESTONE;
        for (Map.Entry<Material, Double> entry : config.weights().entrySet()) {
            lastMaterial = entry.getKey();
            cumulative += entry.getValue();
            if (target < cumulative) {
                return entry.getKey();
            }
        }
        return lastMaterial;
    }

    public synchronized GeneratorTierConfig getTierConfig(int tier) {
        if (tiers.isEmpty()) {
            return null;
        }
        GeneratorTierConfig exact = tiers.get(tier);
        if (exact != null) {
            return exact;
        }
        NavigableMap<Integer, GeneratorTierConfig> nav = tiers;
        Map.Entry<Integer, GeneratorTierConfig> floor = nav.floorEntry(tier);
        return floor != null ? floor.getValue() : nav.firstEntry().getValue();
    }

    public synchronized Map<Integer, GeneratorTierConfig> getTiersSnapshot() {
        return Collections.unmodifiableMap(new TreeMap<>(tiers));
    }

    public synchronized boolean isGeneratableMaterial(Material material) {
        return material != null && generatableMaterials.contains(material);
    }

    public synchronized Set<Material> getGeneratableMaterialsSnapshot() {
        return Collections.unmodifiableSet(EnumSet.copyOf(generatableMaterials));
    }

    public synchronized String getMaterialDisplayName(Material material) {
        if (material == null) {
            return "&7Nieznany";
        }
        String custom = materialNames.get(material);
        if (custom != null) {
            return custom;
        }
        String[] parts = material.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder("&f");
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.isEmpty()) {
                continue;
            }
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    public synchronized List<String> formatTierDropsLore(int tier) {
        GeneratorTierConfig config = getTierConfig(tier);
        if (config == null || config.weights().isEmpty() || config.totalWeight() <= 0.0) {
            return List.of("  &8• &7Bruk&8: &e100%");
        }
        List<String> lines = new ArrayList<>(config.weights().size());
        for (Map.Entry<Material, Double> entry : config.weights().entrySet()) {
            double percent = (entry.getValue() / config.totalWeight()) * 100.0;
            lines.add("  &8• " + getMaterialDisplayName(entry.getKey()) + "&8: &e" + formatPercent(percent) + "%");
        }
        return lines;
    }

    private static String formatPercent(double percent) {
        if (Math.abs(percent - Math.rint(percent)) < 0.005) {
            return String.format(Locale.US, "%.0f", percent);
        }
        return String.format(Locale.US, "%.1f", percent);
    }
}
