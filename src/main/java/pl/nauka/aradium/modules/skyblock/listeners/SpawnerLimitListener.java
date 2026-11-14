package pl.nauka.aradium.modules.skyblock.listeners;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import pl.nauka.aradium.AradiumPlugin;
import pl.nauka.aradium.modules.skyblock.SkyBlockModule;
import pl.nauka.aradium.modules.skyblock.island.Island;
import pl.nauka.aradium.modules.skyblock.island.IslandManager;
import pl.nauka.aradium.utils.ChatUtils;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Listener egzekwujący limit spawnerów na wyspie, przyspieszający wzrost roślin (CROP_GROWTH)
 * oraz aktualizujący poziom wyspy na żywo po postawieniu/zniszczeniu bloków.
 */
public class SpawnerLimitListener implements Listener {

    private final AradiumPlugin plugin;
    private final SkyBlockModule module;

    public SpawnerLimitListener(AradiumPlugin plugin, SkyBlockModule module) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.module = Objects.requireNonNull(module, "module");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawnerPlaceLimitCheck(BlockPlaceEvent event) {
        if (!module.isEnabled()) {
            return;
        }
        Block blockPlaced = event.getBlockPlaced();
        if (blockPlaced.getType() != Material.SPAWNER) {
            return;
        }

        IslandManager islandManager = module.getIslandManager();
        Optional<Island> islandOpt = islandManager.getIslandAt(blockPlaced.getLocation());
        if (islandOpt.isEmpty()) {
            return;
        }

        Island island = islandOpt.get();
        int currentSpawners = countSpawnersOnIsland(island);
        island.setPlacedSpawnersCount(currentSpawners);

        Player player = event.getPlayer();
        if (currentSpawners >= island.getSpawnerLimit() && !player.hasPermission("aradium.admin")) {
            event.setCancelled(true);
            player.sendMessage(ChatUtils.getMessage(
                            plugin.getConfig(),
                            "messages.island_spawner_limit_reached",
                            "&cOsiągnięto limit spawnerów na tej wyspie (&e%current%/%max%&c)! Ulepsz limit w &e/is upgrades&c."
                    )
                    .replace("%current%", String.valueOf(currentSpawners))
                    .replace("%max%", String.valueOf(island.getSpawnerLimit())));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlaceMonitor(BlockPlaceEvent event) {
        if (!module.isEnabled()) {
            return;
        }
        Block block = event.getBlockPlaced();
        Optional<Island> islandOpt = module.getIslandManager().getIslandAt(block.getLocation());
        if (islandOpt.isEmpty()) {
            return;
        }
        Island island = islandOpt.get();
        if (block.getType() == Material.SPAWNER) {
            island.incrementPlacedSpawners(1);
        }
        if (module.getLevelCalculator() != null) {
            module.getLevelCalculator().adjustLevelForBlockChange(island, block.getType(), true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreakMonitor(BlockBreakEvent event) {
        if (!module.isEnabled()) {
            return;
        }
        Block block = event.getBlock();
        Optional<Island> islandOpt = module.getIslandManager().getIslandAt(block.getLocation());
        if (islandOpt.isEmpty()) {
            return;
        }
        Island island = islandOpt.get();
        if (block.getType() == Material.SPAWNER) {
            island.incrementPlacedSpawners(-1);
        }
        if (module.getLevelCalculator() != null) {
            module.getLevelCalculator().adjustLevelForBlockChange(island, block.getType(), false);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onCropGrow(BlockGrowEvent event) {
        if (!module.isEnabled()) {
            return;
        }
        Optional<Island> islandOpt = module.getIslandManager().getIslandAt(event.getBlock().getLocation());
        if (islandOpt.isEmpty()) {
            return;
        }
        int bonusPercent = islandOpt.get().getCropGrowthBonusPercent();
        if (bonusPercent <= 0) {
            return;
        }
        if (!(event.getNewState().getBlockData() instanceof Ageable ageable)) {
            return;
        }
        int maxAge = ageable.getMaximumAge();
        if (ageable.getAge() < maxAge && ThreadLocalRandom.current().nextInt(100) < bonusPercent) {
            ageable.setAge(Math.min(maxAge, ageable.getAge() + 1));
            event.getNewState().setBlockData(ageable);
        }
    }

    public static int countSpawnersOnIsland(Island island) {
        if (island == null) {
            return 0;
        }
        World world = island.getHomeLocation() != null ? island.getHomeLocation().getWorld() : null;
        if (world == null) {
            return island.getPlacedSpawnersCount();
        }
        int radius = island.getBorderRadius();
        int minChunkX = Math.floorDiv(island.getCenterX() - radius, 16);
        int maxChunkX = Math.floorDiv(island.getCenterX() + radius, 16);
        int minChunkZ = Math.floorDiv(island.getCenterZ() - radius, 16);
        int maxChunkZ = Math.floorDiv(island.getCenterZ() + radius, 16);

        int count = 0;
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                if (!world.isChunkLoaded(cx, cz)) {
                    continue;
                }
                for (BlockState tile : world.getChunkAt(cx, cz).getTileEntities(false)) {
                    if (tile instanceof CreatureSpawner && island.containsLocation(tile.getLocation())) {
                        count++;
                    }
                }
            }
        }
        return count;
    }
}
