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
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.ItemStack;
import pl.nauka.aradium.AradiumPlugin;
import pl.nauka.aradium.modules.skyblock.SkyBlockModule;
import pl.nauka.aradium.modules.skyblock.island.Island;
import pl.nauka.aradium.modules.skyblock.island.IslandManager;
import pl.nauka.aradium.modules.skyblock.upgrades.CobbleGeneratorManager;
import pl.nauka.aradium.modules.skyblock.upgrades.IslandUpgradeType;
import pl.nauka.aradium.utils.ChatUtils;

import java.util.Collection;
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
        CobbleGeneratorManager generatorManager = module.getCobbleGeneratorManager();
        boolean isGeneratorBlock = isCobbleGeneratorMaterial(brokenMaterial)
                || (generatorManager != null && generatorManager.isGeneratableMaterial(brokenMaterial));
        if (!module.isEnabled() || !wasGenerated || !isGeneratorBlock) {
            return;
        }

        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE || !event.isDropItems()) {
            return;
        }

        ItemStack tool = player.getInventory().getItemInMainHand();
        boolean hasSilkTouch = tool.getEnchantmentLevel(Enchantment.SILK_TOUCH) > 0;
        Collection<ItemStack> naturalDrops = block.getDrops(tool, player);
        if (naturalDrops.isEmpty()) {
            return;
        }

        Optional<Island> islandOpt = module.getIslandManager().getIslandAt(block.getLocation());
        if (islandOpt.isEmpty()) {
            return;
        }

        int yieldMultiplier = islandOpt.get().getCobbleGeneratorYield();
        int extraMultiplier = computeExtraCobbleDrops(yieldMultiplier, true, hasSilkTouch);
        if (extraMultiplier <= 0) {
            return;
        }

        Location dropLoc = block.getLocation().add(0.5, 0.5, 0.5);
        for (ItemStack naturalDrop : naturalDrops) {
            if (naturalDrop == null || naturalDrop.getType().isAir() || naturalDrop.getAmount() <= 0) {
                continue;
            }
            int remainingExtra = naturalDrop.getAmount() * extraMultiplier;
            int maxStack = Math.max(1, naturalDrop.getMaxStackSize());
            while (remainingExtra > 0) {
                int batch = Math.min(remainingExtra, maxStack);
                ItemStack extra = naturalDrop.clone();
                extra.setAmount(batch);
                block.getWorld().dropItemNaturally(dropLoc, extra);
                remainingExtra -= batch;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCobbleGeneratorForm(BlockFormEvent event) {
        if (!module.isEnabled()) {
            return;
        }
        Material formedType = event.getNewState().getType();
        if (!isCobbleGeneratorMaterial(formedType)) {
            return;
        }
        Block block = event.getBlock();
        Optional<Island> islandOpt = module.getIslandManager().getIslandAt(block.getLocation());
        if (islandOpt.isEmpty()) {
            return;
        }

        Island island = islandOpt.get();
        Material resultMaterial = formedType;
        CobbleGeneratorManager generatorManager = module.getCobbleGeneratorManager();
        if (generatorManager != null) {
            int genTier = island.getUpgradeTier(IslandUpgradeType.COBBLE_GENERATOR_YIELD);
            resultMaterial = generatorManager.rollBlockForTier(genTier);
            if (resultMaterial != formedType) {
                event.getNewState().setType(resultMaterial);
            }
        }

        generatedCobbleBlocks.add(GeneratorBlockKey.fromBlock(block));
        if (module.getLevelCalculator() != null) {
            module.getLevelCalculator().adjustLevelForBlockChange(island, resultMaterial, true);
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

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCropHarvestDoubleDrop(BlockDropItemEvent event) {
        if (!module.isEnabled()) {
            return;
        }
        if (!(event.getBlockState().getBlockData() instanceof Ageable ageable)
                || ageable.getAge() < ageable.getMaximumAge()) {
            return;
        }
        Optional<Island> islandOpt = module.getIslandManager().getIslandAt(event.getBlock().getLocation());
        if (islandOpt.isEmpty()) {
            return;
        }
        int chancePercent = islandOpt.get().getDoubleCropDropChancePercent();
        if (!shouldTriggerPercentBonus(chancePercent, ThreadLocalRandom.current().nextInt(100))) {
            return;
        }
        for (Item itemEntity : event.getItems()) {
            ItemStack stack = itemEntity.getItemStack();
            if (stack.getType().isAir() || stack.getAmount() <= 0 || stack.hasItemMeta()) {
                continue;
            }
            int doubled = Math.min(Math.max(1, stack.getMaxStackSize()), stack.getAmount() * 2);
            stack.setAmount(doubled);
            itemEntity.setItemStack(stack);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onIslandMobDeathBonus(EntityDeathEvent event) {
        if (!module.isEnabled()) {
            return;
        }
        if (event.getEntity() instanceof Player || event.getEntity() instanceof ArmorStand) {
            return;
        }
        Optional<Island> islandOpt = module.getIslandManager().getIslandAt(event.getEntity().getLocation());
        if (islandOpt.isEmpty()) {
            return;
        }
        int bonusPercent = islandOpt.get().getMobDropBonusPercent();
        if (bonusPercent <= 0) {
            return;
        }

        event.setDroppedExp(computeBoostedMobExp(event.getDroppedExp(), bonusPercent));

        if (!shouldTriggerPercentBonus(bonusPercent, ThreadLocalRandom.current().nextInt(100))) {
            return;
        }
        for (ItemStack drop : event.getDrops()) {
            if (!isEligibleMobDropForBonus(drop)) {
                continue;
            }
            int doubled = Math.min(Math.max(1, drop.getMaxStackSize()), drop.getAmount() * 2);
            drop.setAmount(doubled);
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

    public static boolean shouldTriggerPercentBonus(int chancePercent, int rollZeroTo99) {
        if (chancePercent <= 0) {
            return false;
        }
        if (chancePercent >= 100) {
            return true;
        }
        return rollZeroTo99 >= 0 && rollZeroTo99 < chancePercent;
    }

    public static int computeBoostedMobExp(int baseExp, int bonusPercent) {
        if (baseExp <= 0 || bonusPercent <= 0) {
            return Math.max(0, baseExp);
        }
        return (int) Math.round(baseExp * (1.0 + (bonusPercent / 100.0)));
    }

    public static boolean isEligibleMobDropForBonus(ItemStack stack) {
        if (stack == null) {
            return false;
        }
        return isEligibleMobDropForBonus(stack.getType(), stack.getAmount(), stack.hasItemMeta());
    }

    public static boolean isEligibleMobDropForBonus(Material mat, int amount, boolean hasItemMeta) {
        if (mat == null
                || mat == Material.AIR
                || mat == Material.CAVE_AIR
                || mat == Material.VOID_AIR
                || amount <= 0
                || hasItemMeta) {
            return false;
        }
        String name = mat.name();
        if (name.endsWith("_SWORD")
                || name.endsWith("_AXE")
                || name.endsWith("_PICKAXE")
                || name.endsWith("_SHOVEL")
                || name.endsWith("_HOE")
                || name.endsWith("_HELMET")
                || name.endsWith("_CHESTPLATE")
                || name.endsWith("_LEGGINGS")
                || name.endsWith("_BOOTS")) {
            return false;
        }
        return mat != Material.SHIELD
                && mat != Material.BOW
                && mat != Material.CROSSBOW
                && mat != Material.TRIDENT
                && mat != Material.ELYTRA
                && mat != Material.TOTEM_OF_UNDYING
                && mat != Material.SPAWNER;
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
