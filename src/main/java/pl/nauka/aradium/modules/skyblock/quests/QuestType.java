package pl.nauka.aradium.modules.skyblock.quests;

import java.util.Locale;

/**
 * Typy celów obsługiwane przez system współdzielonych misji wyspy.
 */
public enum QuestType {
    BLOCK_BREAK,
    COLLECT,
    SMELT,
    CRAFT,
    BLOCK_AND_CRAFT,
    VILLAGER_TRADE,
    BREW,
    BALANCE_REACH,
    ISLAND_LEVEL,
    ENTITY_KILL,
    FISH_CATCH;

    public boolean matchesAction(QuestType action) {
        if (action == null) {
            return false;
        }
        if (this == action) {
            return true;
        }
        return this == BLOCK_AND_CRAFT
                && (action == BLOCK_BREAK || action == CRAFT || action == SMELT);
    }

    public static QuestType parse(String raw) {
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
