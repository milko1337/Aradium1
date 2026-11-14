package pl.nauka.aradium.modules.armor;

import org.bukkit.Material;

import java.util.Locale;

/**
 * Reprezentuje jedną z czterech części zestawu zbroi.
 */
public enum ArmorPieceType {
    HELMET("HELMET"),
    CHESTPLATE("CHESTPLATE"),
    LEGGINGS("LEGGINGS"),
    BOOTS("BOOTS");

    private final String materialSuffix;

    ArmorPieceType(String materialSuffix) {
        this.materialSuffix = materialSuffix;
    }

    public String getMaterialSuffix() {
        return materialSuffix;
    }

    public Material resolveMaterial(String materialPrefix) {
        if (materialPrefix == null || materialPrefix.isBlank()) {
            return null;
        }
        return Material.matchMaterial(materialPrefix.trim().toUpperCase(Locale.ROOT) + "_" + materialSuffix);
    }

    public static ArmorPieceType fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
