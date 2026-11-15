package pl.nauka.aradium.modules.spawners;

import org.bukkit.Material;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Definicja pojedynczego poziomu ulepszenia w ramach jednej ze ścieżek spawnera.
 */
public record SpawnerTrackLevelDefinition(
        SpawnerUpgradeTrack track,
        int level,
        String displayName,
        double upgradeCost,
        Map<Material, Integer> requiredItems,
        int minSpawnDelay,
        int maxSpawnDelay,
        int spawnCount,
        int maxNearbyEntities,
        int requiredPlayerRange,
        boolean ignoreLight,
        List<String> lore
) {
    public SpawnerTrackLevelDefinition {
        Objects.requireNonNull(track, "track");
        requiredItems = requiredItems != null ? Map.copyOf(requiredItems) : Map.of();
        lore = lore != null ? List.copyOf(lore) : List.of();
        if (minSpawnDelay > maxSpawnDelay) {
            int tmp = minSpawnDelay;
            minSpawnDelay = maxSpawnDelay;
            maxSpawnDelay = tmp;
        }
    }
}
