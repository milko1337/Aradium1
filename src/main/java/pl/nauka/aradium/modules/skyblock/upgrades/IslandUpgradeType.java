package pl.nauka.aradium.modules.skyblock.upgrades;

import org.bukkit.Material;

import java.util.Locale;

/**
 * Dostępne typy ulepszeń wyspy SkyBlock kupowane w GUI za walutę serwerową.
 */
public enum IslandUpgradeType {
    WORLD_BORDER("world_border", "&b&lRozmiar Granicy", Material.BEACON, "kratek promienia"),
    SPAWNER_LIMIT("spawner_limit", "&d&lLimit Spawnerów", Material.SPAWNER, "spawnerów"),
    CROP_GROWTH("crop_growth", "&a&lWzrost Roślin", Material.WHEAT, "% bonusu wzrostu"),
    MEMBER_LIMIT("member_limit", "&e&lLimit Członków", Material.PLAYER_HEAD, "członków");

    private final String key;
    private final String displayName;
    private final Material icon;
    private final String unitLabel;

    IslandUpgradeType(String key, String displayName, Material icon, String unitLabel) {
        this.key = key;
        this.displayName = displayName;
        this.icon = icon;
        this.unitLabel = unitLabel;
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

    public String getUnitLabel() {
        return unitLabel;
    }

    public String formatValue(int value) {
        return unitLabel.startsWith("%") ? value + unitLabel : value + " " + unitLabel;
    }

    public static IslandUpgradeType parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        for (IslandUpgradeType type : values()) {
            if (type.name().equals(normalized) || type.key.equalsIgnoreCase(raw.trim())) {
                return type;
            }
        }
        return null;
    }
}
