package pl.nauka.aradium.modules.skyblock.level;

import pl.nauka.aradium.modules.skyblock.island.Island;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Pamięć podręczna rankingu TOP wysp na żywo dla /is top oraz IslandTopGUI.
 */
public class IslandTopCache {

    private volatile List<Island> cachedTop = Collections.emptyList();
    private volatile Map<UUID, Integer> rankByIslandId = Collections.emptyMap();

    public void refresh(Collection<Island> islands) {
        List<Island> sorted = computeTop(islands, 0);
        Map<UUID, Integer> ranks = new HashMap<>(sorted.size());
        int rank = 1;
        for (Island island : sorted) {
            ranks.put(island.getIslandId(), rank++);
        }
        this.cachedTop = sorted;
        this.rankByIslandId = Collections.unmodifiableMap(ranks);
    }

    public List<Island> getTopIslands(int limit) {
        List<Island> current = this.cachedTop;
        if (limit <= 0 || limit >= current.size()) {
            return current;
        }
        return current.subList(0, limit);
    }

    public int getIslandRank(UUID islandId) {
        if (islandId == null) {
            return -1;
        }
        return rankByIslandId.getOrDefault(islandId, -1);
    }

    public static List<Island> computeTop(Collection<Island> islands, int limit) {
        if (islands == null || islands.isEmpty()) {
            return Collections.emptyList();
        }
        var stream = islands.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingDouble(Island::getIslandLevel).reversed()
                        .thenComparingLong(Island::getCreatedAt));
        if (limit > 0) {
            stream = stream.limit(limit);
        }
        return stream.toList();
    }
}
