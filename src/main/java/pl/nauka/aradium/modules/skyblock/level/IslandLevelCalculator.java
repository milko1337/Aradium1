package pl.nauka.aradium.modules.skyblock.level;

import org.bukkit.Bukkit;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import pl.nauka.aradium.AradiumPlugin;
import pl.nauka.aradium.modules.skyblock.gui.IslandTopGUI;
import pl.nauka.aradium.modules.skyblock.island.Island;
import pl.nauka.aradium.modules.skyblock.island.IslandManager;
import pl.nauka.aradium.utils.ChatUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Asynchroniczny kalkulator poziomu wyspy korzystający z ChunkSnapshot (zero obciążenia ticku serwera)
 * oraz przyrostowego odświeżania punktów przy zmianie pojedynczych bloków.
 */
public class IslandLevelCalculator {

    public record ScanResult(double totalPoints, double calculatedLevel, int spawnerCount, int scannedChunks) {
    }

    private final AradiumPlugin plugin;
    private final IslandManager islandManager;
    private final BlockValueManager blockValueManager;
    private final IslandTopCache topCache;
    private final Map<UUID, Long> lastScanMillis = new ConcurrentHashMap<>();

    public IslandLevelCalculator(
            AradiumPlugin plugin,
            IslandManager islandManager,
            BlockValueManager blockValueManager,
            IslandTopCache topCache
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.islandManager = Objects.requireNonNull(islandManager, "islandManager");
        this.blockValueManager = Objects.requireNonNull(blockValueManager, "blockValueManager");
        this.topCache = Objects.requireNonNull(topCache, "topCache");
    }

    public static double computeLevelFromCounts(
            Map<Material, Integer> counts,
            Map<Material, Double> values,
            double pointsPerLevel
    ) {
        if (counts == null || counts.isEmpty() || values == null || values.isEmpty()) {
            return 0.0;
        }
        double total = 0.0;
        for (Map.Entry<Material, Integer> entry : counts.entrySet()) {
            int qty = entry.getValue() != null ? entry.getValue() : 0;
            if (qty <= 0) {
                continue;
            }
            Double pts = values.get(entry.getKey());
            if (pts != null && pts > 0.0) {
                total += pts * qty;
            }
        }
        double safePerLevel = Math.max(0.01, pointsPerLevel);
        return Math.round((total / safePerLevel) * 100.0) / 100.0;
    }

    public int getCooldownSeconds() {
        return Math.max(1, plugin.getConfig().getInt("skyblock.level_scan_cooldown_seconds", 10));
    }

    public long getRemainingCooldownSeconds(UUID islandId) {
        if (islandId == null) {
            return 0L;
        }
        Long last = lastScanMillis.get(islandId);
        if (last == null) {
            return 0L;
        }
        long elapsedMs = System.currentTimeMillis() - last;
        long cooldownMs = getCooldownSeconds() * 1000L;
        if (elapsedMs >= cooldownMs) {
            return 0L;
        }
        return Math.max(1L, (cooldownMs - elapsedMs + 999L) / 1000L);
    }

    public void recalculateIslandAsync(Island island, Consumer<ScanResult> callback) {
        if (island == null) {
            return;
        }
        World world = Bukkit.getWorld(island.getWorldName());
        if (world == null) {
            if (callback != null) {
                callback.accept(new ScanResult(0.0, island.getIslandLevel(), island.getPlacedSpawnersCount(), 0));
            }
            return;
        }

        lastScanMillis.put(island.getIslandId(), System.currentTimeMillis());

        int radius = island.getBorderRadius();
        int minX = island.getCenterX() - radius;
        int maxX = island.getCenterX() + radius;
        int minZ = island.getCenterZ() - radius;
        int maxZ = island.getCenterZ() + radius;

        int minChunkX = Math.floorDiv(minX, 16);
        int maxChunkX = Math.floorDiv(maxX, 16);
        int minChunkZ = Math.floorDiv(minZ, 16);
        int maxChunkZ = Math.floorDiv(maxZ, 16);

        // Snapshoty chunków musimy pobrać na głównym wątku — samo zliczanie idzie w pełni asynchronicznie.
        List<ChunkSnapshot> snapshots = new ArrayList<>();
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                if (!world.isChunkLoaded(cx, cz)) {
                    world.loadChunk(cx, cz);
                }
                snapshots.add(world.getChunkAt(cx, cz).getChunkSnapshot(false, false, false));
            }
        }

        int minHeight = world.getMinHeight();
        int maxHeight = world.getMaxHeight();
        Map<Material, Double> values = blockValueManager.getBlockValuesSnapshot();
        double pointsPerLevel = blockValueManager.getPointsPerLevel();

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            double totalPoints = 0.0;
            int spawners = 0;
            int sectionCount = Math.max(0, (maxHeight - minHeight) >> 4);

            for (ChunkSnapshot snapshot : snapshots) {
                int chunkWorldX = snapshot.getX() << 4;
                int chunkWorldZ = snapshot.getZ() << 4;
                int localMinX = Math.max(0, minX - chunkWorldX);
                int localMaxX = Math.min(15, maxX - chunkWorldX);
                int localMinZ = Math.max(0, minZ - chunkWorldZ);
                int localMaxZ = Math.min(15, maxZ - chunkWorldZ);

                for (int sec = 0; sec < sectionCount; sec++) {
                    if (snapshot.isSectionEmpty(sec)) {
                        continue;
                    }
                    int secMinY = minHeight + (sec << 4);
                    int secMaxY = Math.min(maxHeight - 1, secMinY + 15);
                    for (int y = secMinY; y <= secMaxY; y++) {
                        for (int x = localMinX; x <= localMaxX; x++) {
                            for (int z = localMinZ; z <= localMaxZ; z++) {
                                Material mat = snapshot.getBlockType(x, y, z);
                                if (mat == Material.SPAWNER) {
                                    spawners++;
                                }
                                Double pts = values.get(mat);
                                if (pts != null && pts > 0.0) {
                                    totalPoints += pts;
                                }
                            }
                        }
                    }
                }
            }

            double finalPoints = totalPoints;
            int finalSpawners = spawners;
            double level = Math.round((finalPoints / Math.max(0.01, pointsPerLevel)) * 100.0) / 100.0;
            ScanResult result = new ScanResult(finalPoints, level, finalSpawners, snapshots.size());

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                island.setIslandLevel(result.calculatedLevel());
                island.setPlacedSpawnersCount(result.spawnerCount());
                islandManager.saveIsland(island);
                topCache.refresh(islandManager.getAllIslands());
                if (callback != null) {
                    callback.accept(result);
                }
            });
        });
    }

    public void requestScan(Player player, Island island) {
        if (player == null || island == null) {
            return;
        }
        long remaining = getRemainingCooldownSeconds(island.getIslandId());
        if (remaining > 0L) {
            player.sendMessage(ChatUtils.getMessage(
                            plugin.getConfig(),
                            "messages.island_level_cooldown",
                            "&cMusisz odczekać jeszcze &e%seconds%s &cprzed kolejnym pełnym skanowaniem wyspy!"
                    )
                    .replace("%seconds%", String.valueOf(remaining)));
            return;
        }

        player.sendMessage(ChatUtils.getMessage(
                plugin.getConfig(),
                "messages.island_level_calculating",
                "&eTrwa asynchroniczne obliczanie poziomu Twojej wyspy..."
        ));

        recalculateIslandAsync(island, result -> {
            if (!player.isOnline()) {
                return;
            }
            player.sendMessage(ChatUtils.getMessage(
                            plugin.getConfig(),
                            "messages.island_level_calculated",
                            "&aPoziom Twojej wyspy wynosi: &6%level% &8(&7Spawnery: &f%spawners%/%max_spawners%&8)&a!"
                    )
                    .replace("%level%", IslandTopGUI.formatLevel(result.calculatedLevel()))
                    .replace("%spawners%", String.valueOf(result.spawnerCount()))
                    .replace("%max_spawners%", String.valueOf(island.getSpawnerLimit())));
        });
    }

    public void adjustLevelForBlockChange(Island island, Material material, boolean placed) {
        if (island == null || material == null) {
            return;
        }
        double points = blockValueManager.getBlockPoints(material);
        if (points <= 0.0) {
            return;
        }
        double deltaLevel = points / Math.max(0.01, blockValueManager.getPointsPerLevel());
        double next = island.getIslandLevel() + (placed ? deltaLevel : -deltaLevel);
        island.setIslandLevel(Math.max(0.0, Math.round(next * 100.0) / 100.0));
        topCache.refresh(islandManager.getAllIslands());
    }
}
