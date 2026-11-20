package pl.nauka.aradium.modules.customitems;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Definicja stawianego specjalnego generatora bruku lub rud wczytywana z custom-items.yml.
 */
public record GeneratorDefinition(
        String id,
        String name,
        Material material,
        int regenDelayTicks,
        List<String> lore,
        Map<Material, Double> drops,
        double totalWeight,
        RecipeDefinition recipe
) {

    public GeneratorDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(material, "material");
        id = id.toLowerCase(Locale.ROOT);
        name = name != null ? name : "&7Generator";
        regenDelayTicks = Math.max(1, regenDelayTicks);
        lore = lore != null ? List.copyOf(lore) : List.of();
        drops = drops != null ? Collections.unmodifiableMap(new LinkedHashMap<>(drops)) : Map.of(Material.COBBLESTONE, 100.0);
        if (totalWeight <= 0.0) {
            double computed = 0.0;
            for (double w : drops.values()) {
                computed += Math.max(0.0, w);
            }
            totalWeight = computed > 0.0 ? computed : 100.0;
        }
    }

    /**
     * Losuje materiał na podstawie wartości z przedziału [0.0, 1.0).
     */
    public Material rollBlock(double randomZeroToOne) {
        if (drops.isEmpty() || totalWeight <= 0.0) {
            return Material.COBBLESTONE;
        }
        double clamped = Math.max(0.0, Math.min(0.999999999, randomZeroToOne));
        double target = clamped * totalWeight;
        double cumulative = 0.0;
        Material lastMaterial = Material.COBBLESTONE;
        for (Map.Entry<Material, Double> entry : drops.entrySet()) {
            lastMaterial = entry.getKey();
            cumulative += entry.getValue();
            if (target < cumulative) {
                return entry.getKey();
            }
        }
        return lastMaterial;
    }

    public List<String> formatDropsLore(Map<Material, String> materialNames) {
        List<String> lines = new ArrayList<>(drops.size());
        for (Map.Entry<Material, Double> entry : drops.entrySet()) {
            double percent = (entry.getValue() / totalWeight) * 100.0;
            String label = materialNames != null
                    ? materialNames.getOrDefault(entry.getKey(), formatFallbackMaterialName(entry.getKey()))
                    : formatFallbackMaterialName(entry.getKey());
            lines.add("  &8• " + label + "&8: &e" + formatPercent(percent) + "%");
        }
        return lines;
    }

    private static String formatPercent(double percent) {
        if (Math.abs(percent - Math.rint(percent)) < 0.005) {
            return String.format(Locale.US, "%.0f", percent);
        }
        return String.format(Locale.US, "%.1f", percent);
    }

    public static String formatFallbackMaterialName(Material material) {
        if (material == null) {
            return "&7Nieznany";
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
}
