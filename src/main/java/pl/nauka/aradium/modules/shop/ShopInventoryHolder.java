package pl.nauka.aradium.modules.shop;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.Objects;

/**
 * Dedykowany InventoryHolder identyfikujący GUI sklepu Aradium (Anti-Dupe).
 */
public class ShopInventoryHolder implements InventoryHolder {

    private final String categoryId;
    private Inventory inventory;

    public ShopInventoryHolder(String categoryId) {
        this.categoryId = Objects.requireNonNull(categoryId, "categoryId");
    }

    public String getCategoryId() {
        return categoryId;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
