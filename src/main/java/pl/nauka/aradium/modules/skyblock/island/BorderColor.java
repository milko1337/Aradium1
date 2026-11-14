package pl.nauka.aradium.modules.skyblock.island;

import org.bukkit.Material;

import java.util.Locale;

/**
 * Kolory granicy WorldBorder wyspy wybierane przez gracza w GUI.
 */
public enum BorderColor {
    BLUE("&bNiebieski", Material.BLUE_STAINED_GLASS_PANE),
    GREEN("&aZielony", Material.LIME_STAINED_GLASS_PANE),
    RED("&cCzerwony", Material.RED_STAINED_GLASS_PANE);

    private final String displayName;
    private final Material icon;

    BorderColor(String displayName, Material icon) {
        this.displayName = displayName;
        this.icon = icon;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Material getIcon() {
        return icon;
    }

    public BorderColor next() {
        BorderColor[] values = values();
        return values[(this.ordinal() + 1) % values.length];
    }

    public static BorderColor parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return BLUE;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return BLUE;
        }
    }
}
