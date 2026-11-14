package pl.nauka.aradium.modules.shop;

import org.bukkit.Material;

import java.util.List;
import java.util.Objects;

/**
 * Definicja pojedynczej pozycji w kategorii sklepu GUI.
 */
public record ShopItem(
        String id,
        int slot,
        Material material,
        String name,
        List<String> lore,
        double buyPrice,
        double sellPrice,
        String targetShop
) {
    public ShopItem {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(material, "material");
        name = name != null ? name : material.name();
        lore = lore != null ? List.copyOf(lore) : List.of();
        targetShop = (targetShop != null && !targetShop.isBlank()) ? targetShop : null;
    }

    public boolean canBuy() {
        return Double.isFinite(buyPrice) && buyPrice >= 0.0;
    }

    public boolean canSell() {
        return Double.isFinite(sellPrice) && sellPrice >= 0.0;
    }

    public boolean isNavigationItem() {
        return targetShop != null;
    }
}
