package pl.nauka.aradium.modules.shop;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Definicja kategorii sklepu GUI wraz z jej układem, formatowaniem lore oraz listą przedmiotów.
 */
public record ShopCategory(
        String id,
        String title,
        int size,
        int backButtonSlot,
        String parentShopId,
        Map<String, String> actionLoreFormat,
        Map<Integer, ShopItem> itemsBySlot
) {
    public static final Map<String, String> DEFAULT_ACTION_LORE_FORMAT = Map.of(
            "buy-enabled", "&aLPM &8- &7Kup 1 szt. za &e%buy_1%$",
            "buy-stack-enabled", "&aShift+LPM &8- &7Kup 64 szt. za &e%buy_64%$",
            "buy-disabled", "&cKupno tego przedmiotu jest wyłączone",
            "sell-enabled", "&ePPM &8- &7Sprzedaj 1 szt. za &a%sell_1%$",
            "sell-all-enabled", "&6ŚPM (Kółko) &8- &7Sprzedaj wszystko z ekwipunku",
            "sell-disabled", "&cSprzedaż tego przedmiotu jest wyłączona"
    );

    public ShopCategory {
        Objects.requireNonNull(id, "id");
        title = title != null ? title : "&8Sklep: " + id;
        size = normalizeInventorySize(size);
        parentShopId = (parentShopId != null && !parentShopId.isBlank()) ? parentShopId : null;

        Map<String, String> mergedFormat = new LinkedHashMap<>(DEFAULT_ACTION_LORE_FORMAT);
        if (actionLoreFormat != null) {
            mergedFormat.putAll(actionLoreFormat);
        }
        actionLoreFormat = Collections.unmodifiableMap(mergedFormat);
        itemsBySlot = itemsBySlot != null ? Collections.unmodifiableMap(new LinkedHashMap<>(itemsBySlot)) : Map.of();
    }

    public static int normalizeInventorySize(int rawSize) {
        if (rawSize <= 9) {
            return 9;
        }
        if (rawSize >= 54) {
            return 54;
        }
        return ((rawSize + 8) / 9) * 9;
    }
}
