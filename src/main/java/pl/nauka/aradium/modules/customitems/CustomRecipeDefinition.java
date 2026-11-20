package pl.nauka.aradium.modules.customitems;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Definicja własnej receptury craftingu ładowana i zapisywana w recipes.yml.
 */
public record CustomRecipeDefinition(
        String id,
        String name,
        RecipeCategory category,
        boolean enabled,
        Map<Integer, RecipeSlotIngredient> matrixSlots,
        RecipeResultSpec resultSpec
) {

    public enum RecipeCategory {
        GENERATORS("generators", "&e&lGeneratory", Material.END_STONE,
                "&7Stawiane generatory bruku i rud."),
        SCROLLS("scrolls", "&d&lZwoje i Runy", Material.PAPER,
                "&7Nietypowe ulepszenia narzędzi, broni i zbroi."),
        ARMOR("armor", "&b&lSpecjalne Zbroje", Material.LEATHER_BOOTS,
                "&7Unikalne elementy pancerza oraz Buty Lotnika."),
        MAGIC("magic", "&c&lPrzedmioty Magiczne", Material.BLAZE_POWDER,
                "&7Artefakty i magiczne przedmioty."),
        MISC("misc", "&a&lRóżności", Material.SOUL_TORCH,
                "&7Pochodnia Anty-Mob i pozostałe receptury.");

        private final String key;
        private final String displayName;
        private final Material icon;
        private final String description;

        RecipeCategory(String key, String displayName, Material icon, String description) {
            this.key = key;
            this.displayName = displayName;
            this.icon = icon;
            this.description = description;
        }

        public String getKey() {
            return key;
        }

        public String getDisplayName() {
            return displayName;
        }

        public Material getIcon() {
            return icon;
        }

        public String getDescription() {
            return description;
        }

        public RecipeCategory next() {
            RecipeCategory[] values = values();
            return values[(ordinal() + 1) % values.length];
        }

        public static RecipeCategory parse(String raw, RecipeCategory fallback) {
            if (raw == null || raw.isBlank()) {
                return fallback;
            }
            String norm = raw.trim().toUpperCase(Locale.ROOT);
            for (RecipeCategory cat : values()) {
                if (cat.name().equals(norm) || cat.key.equalsIgnoreCase(norm)) {
                    return cat;
                }
            }
            return fallback;
        }
    }

    /**
     * Pojedynczy składnik siatki 3x3 (sloty 0..8): zwykły materiał, przedmiot z PDC lub zapisany ItemStack.
     */
    public record RecipeSlotIngredient(
            Material material,
            ItemStack exactStack,
            String customItemId
    ) {
        public RecipeSlotIngredient {
            Objects.requireNonNull(material, "material");
            customItemId = (customItemId != null && !customItemId.isBlank())
                    ? customItemId.trim().toLowerCase(Locale.ROOT)
                    : null;
        }

        public boolean hasExactItemConstraint() {
            return exactStack != null || customItemId != null;
        }
    }

    /**
     * Specyfikacja wyniku receptury (custom-item / special-armor / magic-item / ItemStack / waniliowy materiał).
     */
    public record RecipeResultSpec(
            String customItemId,
            String specialArmorId,
            String magicItemId,
            int magicItemLevel,
            Material material,
            int amount,
            String displayName,
            ItemStack exactResult
    ) {
        public RecipeResultSpec {
            customItemId = normalizeOptional(customItemId);
            specialArmorId = normalizeOptional(specialArmorId);
            magicItemId = normalizeOptional(magicItemId);
            magicItemLevel = Math.max(1, Math.min(255, magicItemLevel));
            amount = Math.max(1, Math.min(64, amount));
        }

        private static String normalizeOptional(String s) {
            return (s != null && !s.isBlank()) ? s.trim().toLowerCase(Locale.ROOT) : null;
        }
    }

    public CustomRecipeDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(resultSpec, "resultSpec");
        id = id.trim().toLowerCase(Locale.ROOT);
        name = (name != null && !name.isBlank()) ? name : id;
        category = category != null ? category : RecipeCategory.MISC;
        matrixSlots = matrixSlots != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(matrixSlots))
                : Map.of();
    }

    public boolean isValidMatrix() {
        if (matrixSlots.isEmpty()) {
            return false;
        }
        for (Integer slot : matrixSlots.keySet()) {
            if (slot == null || slot < 0 || slot > 8) {
                return false;
            }
        }
        return resultSpec.material() != null
                || resultSpec.customItemId() != null
                || resultSpec.specialArmorId() != null
                || resultSpec.magicItemId() != null
                || resultSpec.exactResult() != null;
    }
}
