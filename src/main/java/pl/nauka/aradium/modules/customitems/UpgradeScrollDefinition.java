package pl.nauka.aradium.modules.customitems;

import org.bukkit.Material;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Definicja zwoju lub runy ulepszającej bronie, narzędzia bądź zbroje.
 */
public record UpgradeScrollDefinition(
        String id,
        String name,
        Material material,
        TargetCategory targetCategory,
        UpgradeMode upgradeMode,
        String enchantmentKey,
        int maxLevel,
        Set<String> blockedEnchantments,
        List<String> lore,
        RecipeDefinition recipe
) {

    public enum TargetCategory {
        WEAPON,
        TOOL,
        ARMOR,
        BREAKABLE,
        MAGIC_ARMOR;

        public static TargetCategory parse(String raw, TargetCategory fallback) {
            if (raw == null || raw.isBlank()) {
                return fallback;
            }
            try {
                return valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return fallback;
            }
        }
    }

    public enum UpgradeMode {
        ENCHANTMENT,
        ARMOR_TIER;

        public static UpgradeMode parse(String raw, UpgradeMode fallback) {
            if (raw == null || raw.isBlank()) {
                return fallback;
            }
            try {
                return valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return fallback;
            }
        }
    }

    public record ScrollEvalResult(
            boolean success,
            int previousLevel,
            int nextLevel,
            String errorMessage
    ) {
    }

    public UpgradeScrollDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(material, "material");
        id = id.toLowerCase(Locale.ROOT);
        name = name != null ? name : id;
        targetCategory = targetCategory != null ? targetCategory : TargetCategory.BREAKABLE;
        upgradeMode = upgradeMode != null ? upgradeMode : UpgradeMode.ENCHANTMENT;
        enchantmentKey = (enchantmentKey != null && !enchantmentKey.isBlank())
                ? enchantmentKey.trim().toUpperCase(Locale.ROOT)
                : null;
        maxLevel = Math.max(1, maxLevel);
        blockedEnchantments = blockedEnchantments != null
                ? Collections.unmodifiableSet(new LinkedHashSet<>(blockedEnchantments))
                : Set.of();
        lore = lore != null ? List.copyOf(lore) : List.of();
    }

    public boolean isCompatibleMaterial(Material targetMaterial) {
        if (RecipeDefinition.isAirMaterial(targetMaterial)) {
            return false;
        }
        String matName = targetMaterial.name();
        boolean isSwordOrWeapon = matName.endsWith("_SWORD")
                || matName.endsWith("_AXE")
                || targetMaterial == Material.TRIDENT
                || targetMaterial == Material.MACE;
        boolean isTool = matName.endsWith("_PICKAXE")
                || matName.endsWith("_AXE")
                || matName.endsWith("_SHOVEL")
                || matName.endsWith("_HOE")
                || targetMaterial == Material.SHEARS;
        boolean isArmor = matName.endsWith("_HELMET")
                || matName.endsWith("_CHESTPLATE")
                || matName.endsWith("_LEGGINGS")
                || matName.endsWith("_BOOTS");

        return switch (targetCategory) {
            case WEAPON -> isSwordOrWeapon;
            case TOOL -> isTool;
            case ARMOR -> isArmor || targetMaterial == Material.ELYTRA;
            case BREAKABLE -> isSwordOrWeapon
                    || isTool
                    || isArmor
                    || targetMaterial == Material.ELYTRA
                    || targetMaterial == Material.BOW
                    || targetMaterial == Material.CROSSBOW
                    || targetMaterial == Material.FISHING_ROD
                    || targetMaterial == Material.FLINT_AND_STEEL
                    || targetMaterial == Material.SHIELD
                    || targetMaterial == Material.BRUSH;
            case MAGIC_ARMOR -> isArmor;
        };
    }

    public ScrollEvalResult evaluateEnchantmentUpgrade(
            Material targetMaterial,
            int currentLevel,
            boolean hasBlockedConflict
    ) {
        if (upgradeMode != UpgradeMode.ENCHANTMENT) {
            return new ScrollEvalResult(false, currentLevel, currentLevel, "&cTen zwój nie obsługuje zaklęć.");
        }
        if (!isCompatibleMaterial(targetMaterial)) {
            return new ScrollEvalResult(false, currentLevel, currentLevel, "&cTego zwoju nie można nałożyć na ten przedmiot!");
        }
        if (hasBlockedConflict) {
            return new ScrollEvalResult(false, currentLevel, currentLevel, "&cPrzedmiot posiada wykluczające się zaklęcie!");
        }
        int safeCurrent = Math.max(0, currentLevel);
        if (safeCurrent >= maxLevel) {
            return new ScrollEvalResult(false, safeCurrent, safeCurrent,
                    "&eTen przedmiot osiągnął już maksymalny poziom ulepszenia (&6" + maxLevel + "&e)!");
        }
        return new ScrollEvalResult(true, safeCurrent, safeCurrent + 1, null);
    }
}
