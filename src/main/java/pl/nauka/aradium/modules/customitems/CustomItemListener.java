package pl.nauka.aradium.modules.customitems;

import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import pl.nauka.aradium.AradiumPlugin;
import pl.nauka.aradium.core.PluginModule;

import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 * Listener obsługujący stawiane generatory, pochodnię anty-mob,
 * ochronę bloków specjalnych oraz nakładanie zwojów/run ulepszeń.
 */
public class CustomItemListener implements Listener {

    private final AradiumPlugin plugin;
    private final CustomItemModule module;

    public CustomItemListener(AradiumPlugin plugin, CustomItemModule module) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.module = Objects.requireNonNull(module, "module");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCustomBlockPlace(BlockPlaceEvent event) {
        if (!module.isEnabled()) {
            return;
        }
        ItemStack handItem = event.getItemInHand();
        Block blockPlaced = event.getBlockPlaced();

        String generatorId = module.getGeneratorId(handItem);
        if (generatorId != null) {
            GeneratorDefinition gen = module.getGenerator(generatorId);
            if (gen != null && blockPlaced.getType() == gen.material()) {
                module.registerPlacedGenerator(blockPlaced, generatorId);
            }
            return;
        }

        Integer torchRadius = module.getAntiMobTorchRadius(handItem);
        AntiMobTorchDefinition torchDef = module.getAntiMobTorch();
        if (torchRadius != null && torchDef != null && torchDef.isPlacedTorchMaterial(blockPlaced.getType())) {
            module.registerPlacedAntiMobTorch(blockPlaced, torchRadius);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCustomBlockBreak(BlockBreakEvent event) {
        if (!module.isEnabled()) {
            return;
        }
        Block block = event.getBlock();
        Player player = event.getPlayer();

        String genId = module.getPlacedGeneratorAt(block);
        if (genId != null) {
            module.unregisterPlacedGenerator(block);
            event.setDropItems(false);
            if (player.getGameMode() != GameMode.CREATIVE) {
                ItemStack drop = module.createGeneratorItem(genId, 1);
                CustomItemModule.dropItemAtBlock(block, drop);
            }
            return;
        }

        if (module.hasPlacedAntiMobTorchAt(block)) {
            module.unregisterPlacedAntiMobTorch(block);
            event.setDropItems(false);
            if (player.getGameMode() != GameMode.CREATIVE) {
                ItemStack drop = module.createAntiMobTorchItem(1);
                CustomItemModule.dropItemAtBlock(block, drop);
            }
            return;
        }

        CustomItemModule.BlockPos belowPos = CustomItemModule.BlockPos.fromBlock(block).below();
        if (module.getPlacedGeneratorAtPos(belowPos) != null) {
            module.scheduleGeneratorBlockSpawn(belowPos);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (!module.isEnabled()) {
            return;
        }
        if (containsProtectedBlock(event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (!module.isEnabled()) {
            return;
        }
        if (containsProtectedBlock(event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (!module.isEnabled()) {
            return;
        }
        removeProtectedBlocksFromExplosion(event.blockList());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (!module.isEnabled()) {
            return;
        }
        removeProtectedBlocksFromExplosion(event.blockList());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!module.isEnabled()) {
            return;
        }
        var loc = event.getLocation();
        if (loc.getWorld() == null) {
            return;
        }
        boolean blocked = module.shouldBlockMobSpawn(
                CustomItemModule.isHostileEntity(event.getEntity()),
                event.getSpawnReason(),
                loc.getWorld().getName(),
                loc.getBlockX(),
                loc.getBlockY(),
                loc.getBlockZ()
        );
        if (blocked) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryApplyScroll(InventoryClickEvent event) {
        if (!module.isEnabled() || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT) {
            return;
        }
        if (!(event.getClickedInventory() instanceof PlayerInventory)) {
            return;
        }

        ItemStack cursor = event.getView().getCursor();
        ItemStack target = event.getCurrentItem();
        if (cursor == null || cursor.getType().isAir() || target == null || target.getType().isAir()) {
            return;
        }
        if (module.getScrollId(cursor) == null) {
            return;
        }

        event.setCancelled(true);
        CustomItemModule.ScrollApplyOutcome outcome = module.applyUpgradeScroll(cursor, target);
        if (!outcome.applied()) {
            if (outcome.message() != null) {
                player.sendMessage(outcome.message());
            }
            return;
        }

        event.setCurrentItem(outcome.updatedTarget());
        consumeOneCursorItem(event, cursor);
        if (outcome.message() != null) {
            player.sendMessage(outcome.message());
        }
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.7f, 1.25f);
        syncArmorLoreLater(player);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onShiftRightClickApplyScroll(PlayerInteractEvent event) {
        if (!module.isEnabled() || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.isSneaking()) {
            return;
        }

        PlayerInventory inventory = player.getInventory();
        ItemStack offHand = inventory.getItemInOffHand();
        if (offHand.getType().isAir() || module.getScrollId(offHand) == null) {
            return;
        }

        ItemStack mainHand = inventory.getItemInMainHand();
        if (mainHand.getType().isAir()) {
            return;
        }

        event.setCancelled(true);
        CustomItemModule.ScrollApplyOutcome outcome = module.applyUpgradeScroll(offHand, mainHand);
        if (!outcome.applied()) {
            if (outcome.message() != null) {
                player.sendMessage(outcome.message());
            }
            return;
        }

        inventory.setItemInMainHand(outcome.updatedTarget());
        if (offHand.getAmount() <= 1) {
            inventory.setItemInOffHand(null);
        } else {
            offHand.setAmount(offHand.getAmount() - 1);
            inventory.setItemInOffHand(offHand);
        }

        if (outcome.message() != null) {
            player.sendMessage(outcome.message());
        }
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.7f, 1.25f);
        syncArmorLoreLater(player);
    }

    private void syncArmorLoreLater(Player player) {
        plugin.getArmorModule()
                .filter(PluginModule::isEnabled)
                .ifPresent(armorModule -> plugin.getServer().getScheduler().runTask(plugin, () -> armorModule.evaluatePlayerArmor(player)));
    }

    private boolean containsProtectedBlock(List<Block> blocks) {
        for (Block block : blocks) {
            if (module.isProtectedCustomBlock(block)) {
                return true;
            }
        }
        return false;
    }

    private void removeProtectedBlocksFromExplosion(List<Block> blockList) {
        Iterator<Block> it = blockList.iterator();
        while (it.hasNext()) {
            if (module.isProtectedCustomBlock(it.next())) {
                it.remove();
            }
        }
    }

    private static void consumeOneCursorItem(InventoryClickEvent event, ItemStack cursor) {
        if (cursor.getAmount() <= 1) {
            event.getView().setCursor(null);
        } else {
            ItemStack updated = cursor.clone();
            updated.setAmount(cursor.getAmount() - 1);
            event.getView().setCursor(updated);
        }
    }
}
