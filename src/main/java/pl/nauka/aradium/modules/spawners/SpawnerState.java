package pl.nauka.aradium.modules.spawners;

import org.bukkit.entity.EntityType;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Pełny stan ulepszeń oraz odblokowanych mobów pojedynczego spawnera (przechowywany w PDC).
 */
public record SpawnerState(
        EntityType activeEntity,
        Set<EntityType> unlockedEntities,
        int delayLevel,
        int amountLevel,
        int rangeLevel,
        int lightLevel,
        int groundLevel
) {
    public SpawnerState(
            EntityType activeEntity,
            Set<EntityType> unlockedEntities,
            int delayLevel,
            int amountLevel,
            int rangeLevel,
            int lightLevel
    ) {
        this(activeEntity, unlockedEntities, delayLevel, amountLevel, rangeLevel, lightLevel, 0);
    }

    public SpawnerState {
        if (activeEntity == null || SpawnerModule.FORBIDDEN_BOSS_ENTITIES.contains(activeEntity)) {
            activeEntity = EntityType.PIG;
        }
        Set<EntityType> cleaned = new LinkedHashSet<>();
        cleaned.add(EntityType.PIG);
        if (unlockedEntities != null) {
            for (EntityType entity : unlockedEntities) {
                if (entity != null && !SpawnerModule.FORBIDDEN_BOSS_ENTITIES.contains(entity)) {
                    cleaned.add(entity);
                }
            }
        }
        cleaned.add(activeEntity);
        unlockedEntities = Collections.unmodifiableSet(cleaned);
        delayLevel = Math.max(1, delayLevel);
        amountLevel = Math.max(1, amountLevel);
        rangeLevel = Math.max(1, rangeLevel);
        lightLevel = Math.max(0, lightLevel);
        groundLevel = Math.max(0, groundLevel);
    }

    public static SpawnerState defaultPig() {
        return new SpawnerState(EntityType.PIG, Set.of(EntityType.PIG), 1, 1, 1, 0, 0);
    }

    public int getLevel(SpawnerUpgradeTrack track) {
        return switch (track) {
            case DELAY -> delayLevel;
            case AMOUNT -> amountLevel;
            case RANGE -> rangeLevel;
            case LIGHT_IMMUNITY -> lightLevel;
            case GROUND_IMMUNITY -> groundLevel;
        };
    }

    public SpawnerState withTrackLevel(SpawnerUpgradeTrack track, int newLevel) {
        return switch (track) {
            case DELAY -> new SpawnerState(activeEntity, unlockedEntities, newLevel, amountLevel, rangeLevel, lightLevel, groundLevel);
            case AMOUNT -> new SpawnerState(activeEntity, unlockedEntities, delayLevel, newLevel, rangeLevel, lightLevel, groundLevel);
            case RANGE -> new SpawnerState(activeEntity, unlockedEntities, delayLevel, amountLevel, newLevel, lightLevel, groundLevel);
            case LIGHT_IMMUNITY -> new SpawnerState(activeEntity, unlockedEntities, delayLevel, amountLevel, rangeLevel, newLevel, groundLevel);
            case GROUND_IMMUNITY -> new SpawnerState(activeEntity, unlockedEntities, delayLevel, amountLevel, rangeLevel, lightLevel, newLevel);
        };
    }

    public SpawnerState withActiveEntity(EntityType newEntity) {
        EntityType resolved = (newEntity == null || SpawnerModule.FORBIDDEN_BOSS_ENTITIES.contains(newEntity))
                ? EntityType.PIG
                : newEntity;
        Set<EntityType> updated = new LinkedHashSet<>(unlockedEntities);
        updated.add(resolved);
        return new SpawnerState(resolved, updated, delayLevel, amountLevel, rangeLevel, lightLevel, groundLevel);
    }

    public boolean isUnlocked(EntityType entityType) {
        return entityType != null && unlockedEntities.contains(entityType);
    }

    public boolean isLightImmune() {
        return lightLevel >= 1;
    }

    public boolean isGroundImmune() {
        return groundLevel >= 1;
    }

    public int legacyOverallTier() {
        return Math.max(delayLevel, Math.max(amountLevel, rangeLevel));
    }
}
