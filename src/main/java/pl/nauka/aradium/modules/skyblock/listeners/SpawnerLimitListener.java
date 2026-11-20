package pl.nauka.aradium.modules.skyblock.listeners;

import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.data.Ageable;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.ItemStack;
import pl.nauka.aradium.AradiumPlugin;
import pl.nauka.aradium.modules.skyblock.SkyBlockModule;
import pl.nauka.aradium.modules.skyblock.island.Island;
import pl.nauka.aradium.modules.skyblock.island.IslandManager;
import pl.nauka.aradium.utils.ChatUtils;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Listener egzekwujący limit spawnerów na wyspie, przyspieszający wzrost roślin (CROP_GROWTH),
 * obsługujący mnożnik podstawowego generatora cobla z lawy i wody (COBBLE_GENERATOR_YIELD)
 * z ochroną Anti-Dupe oraz aktualizujący poziom wyspy na żywo.
 */
public class SpawnerLimitListener implements Listener {

    private final AradiumPlugin plugin;
    private final SkyBlockModule module;
    private final Set<GeneratorBlockKey> generatedCobbleBlocks = ConcurrentHashMap.newKeySet();

    public record GeneratorBlockKey(String worldName, int x, int y, int z) {
        public static GeneratorBlockKey fromBlock(Block block) {
            return new GeneratorBlockKey(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
        }
    }

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
        Block block = event.getBlockPlaced();
        generatedCobbleBlocks.remove(GeneratorBlockKey.fromBlock(block));
        if (!module.isEnabled()) {
            return;
        }
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

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCobbleGeneratorBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Material brokenMaterial = block.getType();
        boolean wasGenerated = generatedCobbleBlocks.remove(GeneratorBlockKey.fromBlock(block));
        if (!module.isEnabled() || !wasGenerated || !isCobbleGeneratorMaterial(brokenMaterial)) {
            return;
        }

        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE || !event.isDropItems()) {
            return;
        }

        ItemStack tool = player.getInventory().getItemInMainHand();
        boolean hasSilkTouch = tool != null && tool.getEnchantmentLevel(Enchantment.SILK_TOUCH) > 0;
        if (block.getDrops(tool, player).isEmpty()) {
            return;
        }

        Optional<Island> islandOpt = module.getIslandManager().getIslandAt(block.getLocation());
        if (islandOpt.isEmpty()) {
            return;
        }

        int yieldMultiplier = islandOpt.get().getCobbleGeneratorYield();
        int extraDrops = computeExtraCobbleDrops(yieldMultiplier, true, hasSilkTouch);
        if (extraDrops > 0) {
            Location dropLoc = block.getLocation().add(0.5, 0.5, 0.5);
            block.getWorld().dropItemNaturally(dropLoc, new ItemStack(Material.COBBLESTONE, extraDrops));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCobbleGeneratorForm(BlockFormEvent event) {
        if (!module.isEnabled()) {
            return;
        }
        Material formedType = event.getNewState().getType();
        if (!isCobbleGeneratorMaterial(formedType)) {
            return;
        }
        Block block = event.getBlock();
        if (module.getIslandManager().getIslandAt(block.getLocation()).isPresent()) {
            generatedCobbleBlocks.add(GeneratorBlockKey.fromBlock(block));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        untrackPistonBlocks(event.getBlocks(), event.getDirection());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        untrackPistonBlocks(event.getBlocks(), event.getDirection());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        for (Block block : event.blockList()) {
            generatedCobbleBlocks.remove(GeneratorBlockKey.fromBlock(block));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        for (Block block : event.blockList()) {
            generatedCobbleBlocks.remove(GeneratorBlockKey.fromBlock(block));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent event) {
        if (generatedCobbleBlocks.isEmpty()) {
            return;
        }
        Chunk chunk = event.getChunk();
        String worldName = chunk.getWorld().getName();
        int chunkX = chunk.getX();
        int chunkZ = chunk.getZ();
        generatedCobbleBlocks.removeIf(key ->
                key.worldName().equals(worldName)
                        && (key.x() >> 4) == chunkX
                        && (key.z() >> 4) == chunkZ);
    }

    private void untrackPistonBlocks(List<Block> blocks, BlockFace direction) {
        for (Block moved : blocks) {
            generatedCobbleBlocks.remove(GeneratorBlockKey.fromBlock(moved));
            generatedCobbleBlocks.remove(GeneratorBlockKey.fromBlock(moved.getRelative(direction)));
        }
    }

    public static boolean isCobbleGeneratorMaterial(Material material) {
        return material == Material.COBBLESTONE || material == Material.STONE;
    }

    public static int computeExtraCobbleDrops(
            int yieldMultiplier,
            boolean wasGeneratedByLiquid,
            boolean hasSilkTouch
    ) {
        if (!wasGeneratedByLiquid || hasSilkTouch || yieldMultiplier <= 1) {
            return 0;
        }
        return yieldMultiplier - 1;
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
