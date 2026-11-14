package pl.nauka.aradium.modules.armor;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.GrindstoneInventory;
import org.bukkit.inventory.ItemStack;
import pl.nauka.aradium.AradiumPlugin;

/**
 * Listener zdarzeń zbroi oparty o Paper PlayerArmorChangeEvent (brak pętli tickujących)
 * oraz obsługa specjalnych elementów zbroi (np. Buty lotnika).
 */
public class ArmorListener implements Listener {

    private final AradiumPlugin plugin;
    private final ArmorModule module;

    public ArmorListener(AradiumPlugin plugin, ArmorModule module) {
        this.plugin = plugin;
        this.module = module;
    }

    @EventHandler
    public void onArmorChange(PlayerArmorChangeEvent event) {
        Player player = event.getPlayer();
        module.evaluatePlayerArmor(player, event.getSlot(), event.getNewItem());
        scheduleArmorSync(player, true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            scheduleArmorSync(player, false);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            scheduleArmorSync(player, false);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDropItem(PlayerDropItemEvent event) {
        ItemStack dropped = event.getItemDrop().getItemStack();
        if (module.updateItemEquippedLore(dropped, 0)) {
            event.getItemDrop().setItemStack(dropped);
        }
        scheduleArmorSync(event.getPlayer(), true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) {
            return;
        }
        if (event.getEntity() instanceof Player player && module.shouldCancelFallDamage(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        module.evaluatePlayerArmor(event.getPlayer());
    }

    @EventHandler
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        module.evaluatePlayerArmor(player);
        scheduleArmorSync(player, true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        scheduleArmorSync(event.getPlayer(), true);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && module.isEnabled()) {
                module.refreshPlayerArmor(player);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        module.handlePlayerQuit(event.getPlayer());
    }

    @EventHandler
    public void onPrepareGrindstone(PrepareGrindstoneEvent event) {
        GrindstoneInventory inventory = event.getInventory();
        if (module.isMagicArmor(inventory.getUpperItem()) || module.isMagicArmor(inventory.getLowerItem())) {
            event.setResult(null);
        }
    }

    private void scheduleArmorSync(Player player, boolean refreshBoots) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || !module.isEnabled()) {
                return;
            }
            module.syncPlayerArmorLore(player);
            if (refreshBoots) {
                module.evaluateSpecialBoots(player);
            }
        });
    }
}
