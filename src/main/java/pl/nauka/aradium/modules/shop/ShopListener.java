package pl.nauka.aradium.modules.shop;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

/**
 * Listener GUI sklepu obsługujący kategorie, ekrany wyboru ilości, /sellgui
 * oraz kategoryczną ochronę Anti-Dupe (blokada wyciągania przycisków UI).
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

        if (holder.getScreenType() == ShopInventoryHolder.ScreenType.SELL_GUI) {
            handleSellGuiClick(event, holder, topInventory);
            return;
        }

        // Kategoryczny zakaz wyciągania i wkładania przedmiotów w menu sklepu (także Shift-Click, 1-9, double click).
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player) || !module.isEnabled()) {
            return;
        }

        Inventory clickedInventory = event.getClickedInventory();
        if (clickedInventory == null || !clickedInventory.equals(topInventory)) {
            return;
        }

        module.getShopGUI().handleClick(player, holder, event.getSlot(), event.getClick());
    }

    private void handleSellGuiClick(InventoryClickEvent event, ShopInventoryHolder holder, Inventory topInventory) {
        if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
            event.setCancelled(true);
            return;
        }

        Inventory clickedInventory = event.getClickedInventory();
        if (clickedInventory == null || !clickedInventory.equals(topInventory)) {
            return;
        }

        if (event.getSlot() >= ShopGUI.SELL_GUI_CONTENT_LIMIT) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player && module.isEnabled()) {
                module.getShopGUI().handleClick(player, holder, event.getSlot(), event.getClick());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory topInventory = event.getView().getTopInventory();
        if (!(topInventory.getHolder() instanceof ShopInventoryHolder holder)) {
            return;
        }

        if (holder.getScreenType() != ShopInventoryHolder.ScreenType.SELL_GUI) {
            event.setCancelled(true);
            return;
        }

        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot >= ShopGUI.SELL_GUI_CONTENT_LIMIT && rawSlot < topInventory.getSize()) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        Inventory topInventory = event.getView().getTopInventory();
        if (!(topInventory.getHolder() instanceof ShopInventoryHolder holder)
                || holder.getScreenType() != ShopInventoryHolder.ScreenType.SELL_GUI) {
            return;
        }
        if (event.getPlayer() instanceof Player player && module.getShopGUI() != null) {
            module.getShopGUI().processSellGuiClose(player, topInventory);
        }
    }
}
