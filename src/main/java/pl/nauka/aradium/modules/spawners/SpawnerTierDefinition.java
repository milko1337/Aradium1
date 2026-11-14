package pl.nauka.aradium.modules.spawners;

import org.bukkit.Material;

import java.util.List;
import java.util.Map;

/**
 * Definicja pojedynczego tieru spawnera wczytywana z pliku spawners.yml.
 */
public record SpawnerTierDefinition(
        int tier,
        String displayName,
        double upgradeCost,
        Map<Material, Integer> requiredItems,
        int minSpawnDelay,
        int maxSpawnDelay,
        int spawnCount,
        int maxNearbyEntities,
        int requiredPlayerRange,
        List<String> lore
) {
    public SpawnerTierDefinition {
        requiredItems = requiredItems != null ? Map.copyOf(requiredItems) : Map.of();
        lore = lore != null ? List.copyOf(lore) : List.of();
        if (minSpawnDelay > maxSpawnDelay) {
            int tmp = minSpawnDelay;
            minSpawnDelay = maxSpawnDelay;
            maxSpawnDelay = tmp;
        }
    }
}
