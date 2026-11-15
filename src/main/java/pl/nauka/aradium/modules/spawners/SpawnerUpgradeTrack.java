package pl.nauka.aradium.modules.spawners;

import org.bukkit.Material;

/**
 * Cztery niezależne ścieżki ulepszeń spawnera zapisywane w PDC oraz spawners.yml.
 */
public enum SpawnerUpgradeTrack {

    DELAY("delay", "&eCzas respu", Material.CLOCK, 19, 1),
    AMOUNT("amount", "&aIlość mobów", Material.SPAWNER, 20, 1),
    RANGE("range", "&bZasięg gracza", Material.SPYGLASS, 21, 1),
    LIGHT_IMMUNITY("light-immunity", "&dIgnorowanie światła", Material.SOUL_LANTERN, 22, 0);

    private final String configKey;
    private final String displayName;
    private final Material icon;
    private final int guiSlot;
    private final int defaultLevel;

    SpawnerUpgradeTrack(String configKey, String displayName, Material icon, int guiSlot, int defaultLevel) {
        this.configKey = configKey;
        this.displayName = displayName;
        this.icon = icon;
        this.guiSlot = guiSlot;
        this.defaultLevel = defaultLevel;
    }

    public String configKey() {
        return configKey;
    }

    public String displayName() {
        return displayName;
    }

    public Material icon() {
        return icon;
    }

    public int guiSlot() {
        return guiSlot;
    }

    public int defaultLevel() {
        return defaultLevel;
    }
}
