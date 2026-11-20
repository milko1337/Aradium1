package pl.nauka.aradium.modules.customitems;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Definicja własnej receptury craftingu ({@link org.bukkit.inventory.ShapedRecipe}).
 */
public record RecipeDefinition(
        boolean enabled,
        List<String> shape,
        Map<Character, Material> ingredients
) {

    public RecipeDefinition {
        shape = shape != null ? List.copyOf(shape) : List.of();
        ingredients = ingredients != null ? Collections.unmodifiableMap(new LinkedHashMap<>(ingredients)) : Map.of();
    }

    public boolean isValid() {
        if (!enabled || shape.isEmpty() || shape.size() > 3 || ingredients.isEmpty()) {
            return false;
        }
        int expectedLength = -1;
        for (String row : shape) {
            if (row == null || row.isEmpty() || row.length() > 3) {
                return false;
            }
            if (expectedLength < 0) {
                expectedLength = row.length();
            } else if (row.length() != expectedLength) {
                return false;
            }
            for (int i = 0; i < row.length(); i++) {
                char symbol = row.charAt(i);
                if (symbol != ' ' && !ingredients.containsKey(symbol)) {
                    return false;
                }
            }
        }
        return true;
    }

    public static boolean isAirMaterial(Material material) {
        return material == null
                || material == Material.AIR
                || material == Material.CAVE_AIR
                || material == Material.VOID_AIR;
    }

    public static RecipeDefinition parse(ConfigurationSection section, String context, Logger logger) {
        if (section == null || !section.getBoolean("enabled", true)) {
            return null;
        }
        List<String> rawShape = section.getStringList("shape");
        if (rawShape.isEmpty() || rawShape.size() > 3) {
            if (logger != null) {
                logger.warning("Nieprawidłowy kształt receptury ('shape') dla " + context + ".");
            }
            return null;
        }

        ConfigurationSection ingredientsSec = section.getConfigurationSection("ingredients");
        if (ingredientsSec == null) {
            if (logger != null) {
                logger.warning("Brak sekcji 'ingredients' w recepturze dla " + context + ".");
            }
            return null;
        }

        Map<Character, Material> parsedIngredients = new LinkedHashMap<>();
        for (String key : ingredientsSec.getKeys(false)) {
            if (key == null || key.length() != 1) {
                continue;
            }
            String matName = ingredientsSec.getString(key, "");
            Material material = Material.matchMaterial(matName.trim().toUpperCase(Locale.ROOT));
            if (isAirMaterial(material) || material.isLegacy()
                    || (Bukkit.getServer() != null && !material.isItem())) {
                if (logger != null) {
                    logger.warning("Nieprawidłowy składnik '" + matName + "' w recepturze dla " + context + ".");
                }
                return null;
            }
            parsedIngredients.put(key.charAt(0), material);
        }

        List<String> normalizedShape = new ArrayList<>(rawShape.size());
        for (String row : rawShape) {
            normalizedShape.add(row != null ? row : "");
        }

        RecipeDefinition definition = new RecipeDefinition(true, normalizedShape, parsedIngredients);
        if (!definition.isValid()) {
            if (logger != null) {
                logger.warning("Receptura dla " + context + " nie przeszła walidacji kształtu i składników.");
            }
            return null;
        }
        return definition;
    }
}
