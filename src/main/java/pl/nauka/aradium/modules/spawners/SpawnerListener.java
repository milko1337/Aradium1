package pl.nauka.aradium.modules.spawners;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.SpawnerSpawnEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import pl.nauka.aradium.AradiumPlugin;
import pl.nauka.aradium.utils.ChatUtils;

import java.util.Objects;

/**
 * Listener obsługujący otwieranie GUI spawnerów (Shift + PPM), Silk Touch,
 * stawianie spawnerów z pełnym stanem PDC oraz ochronę Anti-Dupe w obu oknach GUI.
 */
public class SpawnerListener implements Listener {

    private final AradiumPlugin plugin;
    private final SpawnerModule module;

    public SpawnerListener(AradiumPlugin plugin, SpawnerModule module) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.module = Objects.requireNonNull(module, "module");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawnerInteract(PlayerInteractEvent event) {
        if (!module.isEnabled()
                || event.getAction() != Action.RIGHT_CLICK_BLOCK
                || event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Block clicked = event.getClickedBlock();
        Player player = event.getPlayer();
        if (clicked == null || clicked.getType() != Material.SPAWNER || !player.isSneaking()) {
            return;
        }

        if (!(clicked.getState() instanceof CreatureSpawner spawner)) {
            return;
        }

        event.setCancelled(true);

        SpawnerDataManager dataManager = module.getDataManager();
        if (!dataManager.hasInitializedState(spawner)) {
            dataManager.applyStateToBlock(spawner, dataManager.getSpawnerState(spawner), module);
        }

        module.getSpawnerGUI().openSpawnerGUI(player, spawner);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpawnerBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!module.isEnabled() || block.getType() != Material.SPAWNER) {
            return;
        }

        module.getSpawnerGUI().closeViewersForBlock(block);
        if (module.getAirSpawnTask() != null) {
            module.getAirSpawnTask().untrackSpawner(block);
        }

        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }

        ItemStack tool = player.getInventory().getItemInMainHand();
        boolean silkTouchActive = !module.isRequireSilkTouch() || isSilkTouchPickaxe(tool);
        if (!silkTouchActive) {
            player.sendMessage(ChatUtils.getMessage(
                    plugin.getConfig(),
                    "messages.spawner_broken_no_silk",
                    "&cZniszczyłeś spawner bez Jedwabnego Dotyku (Silk Touch) — spawner przepadł!"
            ));
            return;
        }

        if (!(block.getState() instanceof CreatureSpawner spawner)) {
            return;
        }

        SpawnerDataManager dataManager = module.getDataManager();
        SpawnerState state = dataManager.getSpawnerState(spawner);

        event.setExpToDrop(0);
        event.setDropItems(false);

        ItemStack dropped = dataManager.transferBlockToItem(spawner, module);
        block.getWorld().dropItemNaturally(block.getLocation(), dropped);

        player.sendMessage(ChatUtils.getMessage(
                        plugin.getConfig(),
                        "messages.spawner_mined_silk",
                        "&aWydobyto spawner: &e%entity% &8(&fPoziom %tier%&8)&a!"
                )
                .replace("%entity%", module.formatMobDisplayName(state.activeEntity()))
                .replace("%tier%", String.valueOf(state.legacyOverallTier())));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpawnerPlace(BlockPlaceEvent event) {
        Block blockPlaced = event.getBlockPlaced();
        if (!module.isEnabled() || blockPlaced.getType() != Material.SPAWNER) {
            return;
        }

        if (!(blockPlaced.getState() instanceof CreatureSpawner placedSpawner)) {
            return;
        }

        SpawnerDataManager dataManager = module.getDataManager();
        dataManager.transferItemToBlock(event.getItemInHand(), placedSpawner, module);
        SpawnerState applied = dataManager.getSpawnerState(placedSpawner);

        event.getPlayer().sendMessage(ChatUtils.getMessage(
                        plugin.getConfig(),
                        "messages.spawner_placed",
                        "&aPostawiono spawner: &e%entity% &8(&fPoziom %tier%&8)&a!"
                )
                .replace("%entity%", module.formatMobDisplayName(applied.activeEntity()))
                .replace("%tier%", String.valueOf(applied.legacyOverallTier())));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawnerSpawn(SpawnerSpawnEvent event) {
        if (!module.isEnabled() || module.getAirSpawnTask() == null) {
            return;
        }
        CreatureSpawner spawner = event.getSpawner();
        if (spawner != null) {
            module.getAirSpawnTask().resetCooldownOnVanillaSpawn(spawner);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!module.isEnabled() || module.getAirSpawnTask() == null) {
            return;
        }
        module.getAirSpawnTask().scanChunk(event.getChunk(), module.getDataManager());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent event) {
        if (module.getAirSpawnTask() != null) {
            module.getAirSpawnTask().untrackChunk(event.getChunk());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (module.isEnabled()) {
            event.blockList().removeIf(b -> b.getType() == Material.SPAWNER);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (module.isEnabled()) {
            event.blockList().removeIf(b -> b.getType() == Material.SPAWNER);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory topInventory = event.getView().getTopInventory();
        if (!(topInventory.getHolder() instanceof SpawnerUpgradeHolder holder)) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player) || !module.isEnabled()) {
            return;
        }

        Inventory clickedInventory = event.getClickedInventory();
        if (clickedInventory == null || !clickedInventory.equals(topInventory)) {
            return;
        }

        module.getSpawnerGUI().handleInventoryClick(player, holder, event.getSlot());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof SpawnerUpgradeHolder) {
            event.setCancelled(true);
        }
    }

    public static boolean isSilkTouchPickaxe(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }
        return item.getType().name().endsWith("_PICKAXE")
                && item.getEnchantmentLevel(Enchantment.SILK_TOUCH) > 0;
    }
}
