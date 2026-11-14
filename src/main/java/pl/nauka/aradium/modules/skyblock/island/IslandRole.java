package pl.nauka.aradium.modules.skyblock.island;

import java.util.List;
import java.util.Locale;

/**
 * Role gracza w obrębie wyspy SkyBlock (uprawnienia hierarchiczne i konfigurowalne w GUI).
 */
public enum IslandRole {
    VISITOR(0, "&7Odwiedzający"),
    TRUSTED(1, "&eZaufany &8(&7Tymczasowy&8)"),
    MEMBER(2, "&aCzłonek"),
    CO_OWNER(3, "&6Współwłaściciel"),
    OWNER(4, "&cWłaściciel");

    private final int priority;
    private final String displayName;

    IslandRole(int priority, String displayName) {
        this.priority = priority;
        this.displayName = displayName;
    }

    public int getPriority() {
        return priority;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isAtLeast(IslandRole other) {
        return other != null && this.priority >= other.priority;
    }

    public static List<IslandRole> configurableRoles() {
        return List.of(TRUSTED, MEMBER, CO_OWNER);
    }

    public static IslandRole parse(String raw) {
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
