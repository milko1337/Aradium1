package pl.nauka.aradium.modules.shop;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.Objects;

/**
 * Dedykowany InventoryHolder identyfikujący wszystkie okna GUI sklepu Aradium (Anti-Dupe).
 */
public class ShopInventoryHolder implements InventoryHolder {

    /**
     * Rodzaje ekranów obsługiwanych w obrębie modułu sklepu.
     */
    public enum ScreenType {
        CATEGORY,
        BUY_SCREEN,
        SELL_SCREEN,
        BUY_STACKS_SCREEN,
        SELL_GUI
    }

    private final String categoryId;
    private final int page;
    private final ScreenType screenType;
    private final ShopItem selectedItem;
    private int selectedAmount;
    private boolean sellProcessed;
    private Inventory inventory;

    public ShopInventoryHolder(String categoryId) {
        this(categoryId, 0, ScreenType.CATEGORY, null, 1);
    }

    public ShopInventoryHolder(String categoryId, int page) {
        this(categoryId, page, ScreenType.CATEGORY, null, 1);
    }

    public ShopInventoryHolder(
            String categoryId,
            int page,
            ScreenType screenType,
            ShopItem selectedItem,
            int selectedAmount
    ) {
        this.categoryId = Objects.requireNonNull(categoryId, "categoryId");
        this.page = Math.max(0, page);
        this.screenType = Objects.requireNonNull(screenType, "screenType");
        this.selectedItem = selectedItem;
        this.selectedAmount = Math.max(1, selectedAmount);
    }

    public String getCategoryId() {
        return categoryId;
    }

    public int getPage() {
        return page;
    }

    public ScreenType getScreenType() {
        return screenType;
    }

    public ShopItem getSelectedItem() {
        return selectedItem;
    }

    public int getSelectedAmount() {
        return selectedAmount;
    }

    public void setSelectedAmount(int selectedAmount) {
        this.selectedAmount = Math.max(1, selectedAmount);
    }

    public boolean isSellProcessed() {
        return sellProcessed;
    }

    public void markSellProcessed() {
        this.sellProcessed = true;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
