package pl.nauka.aradium.modules.customitems;

import org.bukkit.Material;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Definicja zwoju lub runy nadającej nietypowe mechaniczne ulepszenia ekwipunku
 * (m.in. kopanie 2x2/3x3, Hutnik, Drwal, Żniwiarz 3x3, Wampiryzm, Piorun, Double Jump, Awans Tieru Zbroi).
 */
public record UpgradeScrollDefinition(
        String id,
        String name,
        String displayTag,
        Material material,
        TargetCategory targetCategory,
        CustomUpgradeAbility ability,
        double value,
        int maxLevel,
        Set<CustomUpgradeAbility> incompatibleAbilities,
        List<String> lore
) {

    public enum TargetCategory {
        WEAPON,
        PICKAXE_OR_SHOVEL,
        AXE,
        HOE,
        BOOTS,
        MAGIC_ARMOR,
        ANY_TOOL,
        ANY_ARMOR;

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

    public enum CustomUpgradeAbility {
        MINING_2X2("Kopanie 2x2"),
        MINING_3X3("Kopanie 3x3"),
        AUTO_SMELT("Hutnik (Auto-Smelt)"),
        TREE_CAPITATOR("Drwal"),
        HARVESTER_3X3("Żniwiarz 3x3"),
        VAMPIRISM("Wampiryzm (1/3 HP)"),
        THUNDER_STRIKE("Uderzenie Pioruna"),
        DOUBLE_JUMP("Podwójny Skok"),
        ARMOR_TIER("Awans Tieru Zbroi");

        private final String label;

        CustomUpgradeAbility(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }

        public static CustomUpgradeAbility parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return null;
            }
            try {
                return valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
    }

    public record ScrollEvalResult(
            boolean success,
            String errorMessage
    ) {
    }

    public UpgradeScrollDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(ability, "ability");
        id = id.toLowerCase(Locale.ROOT);
        name = name != null ? name : id;
        displayTag = (displayTag != null && !displayTag.isBlank())
                ? displayTag
                : "&d✦ Ulepszenie: &f" + ability.getLabel();
        targetCategory = targetCategory != null ? targetCategory : TargetCategory.ANY_TOOL;
        value = Double.isFinite(value) ? value : 1.0;
        maxLevel = Math.max(1, maxLevel);
        incompatibleAbilities = incompatibleAbilities != null
                ? Collections.unmodifiableSet(new LinkedHashSet<>(incompatibleAbilities))
                : Set.of();
        lore = lore != null ? List.copyOf(lore) : List.of();
    }

    public boolean isCompatibleMaterial(Material targetMaterial) {
        if (RecipeDefinition.isAirMaterial(targetMaterial)) {
            return false;
        }
        String matName = targetMaterial.name();
        boolean isWeapon = matName.endsWith("_SWORD")
                || matName.endsWith("_AXE")
                || targetMaterial == Material.TRIDENT
                || targetMaterial == Material.MACE;
        boolean isPickaxeOrShovel = matName.endsWith("_PICKAXE") || matName.endsWith("_SHOVEL");
        boolean isAxe = matName.endsWith("_AXE");
        boolean isHoe = matName.endsWith("_HOE");
        boolean isBoots = matName.endsWith("_BOOTS");
        boolean isArmor = matName.endsWith("_HELMET")
                || matName.endsWith("_CHESTPLATE")
                || matName.endsWith("_LEGGINGS")
                || isBoots;

        return switch (targetCategory) {
            case WEAPON -> isWeapon;
            case PICKAXE_OR_SHOVEL -> isPickaxeOrShovel;
            case AXE -> isAxe;
            case HOE -> isHoe;
            case BOOTS -> isBoots;
            case MAGIC_ARMOR -> isArmor;
            case ANY_TOOL -> isPickaxeOrShovel || isAxe || isHoe;
            case ANY_ARMOR -> isArmor || targetMaterial == Material.ELYTRA;
        };
    }

    public ScrollEvalResult evaluateApplication(Material targetMaterial, Set<CustomUpgradeAbility> existingAbilities) {
        if (!isCompatibleMaterial(targetMaterial)) {
            return new ScrollEvalResult(false, "&cTego ulepszenia nie można nałożyć na ten przedmiot!");
        }
        Set<CustomUpgradeAbility> current = existingAbilities != null ? existingAbilities : Set.of();
        if (ability != CustomUpgradeAbility.ARMOR_TIER && current.contains(ability)) {
            return new ScrollEvalResult(false, "&eTen przedmiot posiada już ulepszenie: &f" + ability.getLabel() + "&e!");
        }
        for (CustomUpgradeAbility conflict : incompatibleAbilities) {
            if (current.contains(conflict)) {
                return new ScrollEvalResult(false,
                        "&cTo ulepszenie wyklucza się z obecnym ulepszeniem: &f" + conflict.getLabel() + "&c!");
            }
        }
        return new ScrollEvalResult(true, null);
    }
}
