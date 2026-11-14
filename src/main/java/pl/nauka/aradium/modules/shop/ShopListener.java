package pl.nauka.aradium.modules.shop;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

/**
 * Listener GUI sklepu z kategoryczną ochroną Anti-Dupe (blokada przenoszenia i wkładania przedmiotów).
 */
public class ShopListener implements Listener {

    private final ShopModule module;

    public ShopListener(ShopModule module) {
        this.module = module;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory topInventory = event.getView().getTopInventory();
        if (!(topInventory.getHolder() instanceof ShopInventoryHolder holder)) {
            return;
        }

        // Kategoryczny zakaz wyciągania i wkładania przedmiotów (także Shift-Click, 1-9, double click).
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player) || !module.isEnabled()) {
            return;
        }

        Inventory clickedInventory = event.getClickedInventory();
        if (clickedInventory == null || !clickedInventory.equals(topInventory)) {
            return;
        }

        module.getShopGUI().handleClick(player, holder.getCategoryId(), event.getSlot(), event.getClick());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ShopInventoryHolder) {
            event.setCancelled(true);
        }
    }
}
