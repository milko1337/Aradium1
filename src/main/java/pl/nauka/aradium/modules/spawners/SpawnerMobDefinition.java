package pl.nauka.aradium.modules.spawners;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.util.Objects;

/**
 * Definicja typu moba dostępnego do wykupienia i przełączania w menu spawnera.
 */
public record SpawnerMobDefinition(
        EntityType entityType,
        String displayName,
        Material icon,
        double unlockCost
) {
    public SpawnerMobDefinition {
        Objects.requireNonNull(entityType, "entityType");
        Objects.requireNonNull(icon, "icon");
        if (displayName == null || displayName.isBlank()) {
            displayName = SpawnerDataManager.formatEntityName(entityType);
        }
        unlockCost = Math.max(0.0, unlockCost);
    }
}
