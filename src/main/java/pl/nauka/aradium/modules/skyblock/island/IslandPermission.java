package pl.nauka.aradium.modules.skyblock.island;

import org.bukkit.Material;

import java.util.EnumMap;
import java.util.Locale;

/**
 * Granularne flagi uprawnień konfigurowane w menu Role GUI dla ról TRUSTED, MEMBER oraz CO_OWNER.
 */
public enum IslandPermission {
    BREAK("Niszczenie bloków", "Pozwala niszczyć bloki na wyspie", Material.IRON_PICKAXE),
    PLACE("Stawianie bloków", "Pozwala stawiać bloki na wyspie", Material.GRASS_BLOCK),
    CONTAINERS("Otwieranie skrzyń", "Pozwala otwierać skrzynie, piece i beczki", Material.CHEST),
    INTERACT("Interakcja", "Pozwala używać drzwi, przycisków i dźwigni", Material.OAK_DOOR),
    SPAWNERS("Obsługa spawnerów", "Pozwala niszczyć, stawiać i ulepszać spawnery", Material.SPAWNER),
    INVITE("Zapraszanie graczy", "Pozwala zapraszać graczy oraz nadawać Trust", Material.PLAYER_HEAD),
    SETHOME("Ustawianie domu i warpów", "Pozwala zmieniać punkt /is home i warpy", Material.RED_BED);

    private final String displayName;
    private final String description;
    private final Material icon;

    IslandPermission(String displayName, String description, Material icon) {
        this.displayName = displayName;
        this.description = description;
        this.icon = icon;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public Material getIcon() {
        return icon;
    }

    public static EnumMap<IslandPermission, Boolean> defaultPermissionsFor(IslandRole role) {
        EnumMap<IslandPermission, Boolean> defaults = new EnumMap<>(IslandPermission.class);
        for (IslandPermission permission : values()) {
            boolean allowed = switch (role) {
                case OWNER, CO_OWNER -> true;
                case MEMBER -> permission == BREAK || permission == PLACE || permission == CONTAINERS || permission == INTERACT;
                case TRUSTED -> permission == BREAK || permission == PLACE || permission == INTERACT;
                case VISITOR -> false;
            };
            defaults.put(permission, allowed);
        }
        return defaults;
    }

    public static IslandPermission parse(String raw) {
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
