package pl.nauka.aradium.modules.shop;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Definicja kategorii sklepu GUI wraz z układem, formatowaniem lore oraz wielostronicową listą przedmiotów.
 */
public record ShopCategory(
        String id,
        String title,
        int size,
        int backButtonSlot,
        String parentShopId,
        Map<String, String> actionLoreFormat,
        List<Map<Integer, ShopItem>> pages
) {
    public static final Map<String, String> DEFAULT_ACTION_LORE_FORMAT = Map.of(
            "buy-enabled", "&aLPM &8- &7Kup (wybór ilości), od &e%buy_1%$",
            "buy-stack-enabled", "&aShift+LPM &8- &7Szybko kup 64 szt. za &e%buy_64%$",
            "buy-disabled", "&cKupno tego przedmiotu jest wyłączone",
            "sell-enabled", "&ePPM &8- &7Sprzedaj (wybór ilości), po &a%sell_1%$",
            "sell-all-enabled", "&6Shift+PPM / ŚPM &8- &7Sprzedaj wszystko z ekwipunku",
            "sell-disabled", "&cSprzedaż tego przedmiotu jest wyłączona"
    );

    public ShopCategory(
            String id,
            String title,
            int size,
            int backButtonSlot,
            String parentShopId,
            Map<String, String> actionLoreFormat,
            Map<Integer, ShopItem> itemsBySlot
    ) {
        this(
                id,
                title,
                size,
                backButtonSlot,
                parentShopId,
                actionLoreFormat,
                List.of(itemsBySlot != null ? itemsBySlot : Map.of())
        );
    }

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

        if (pages == null || pages.isEmpty()) {
            pages = List.of(Map.of());
        } else {
            List<Map<Integer, ShopItem>> normalizedPages = new ArrayList<>(pages.size());
            for (Map<Integer, ShopItem> pageMap : pages) {
                if (pageMap == null || pageMap.isEmpty()) {
                    normalizedPages.add(Map.of());
                } else {
                    normalizedPages.add(Collections.unmodifiableMap(new LinkedHashMap<>(pageMap)));
                }
            }
            pages = Collections.unmodifiableList(normalizedPages);
        }
    }

    /**
     * Zwraca mapę slotów z pierwszej strony kategorii (kompatybilność wsteczna).
     */
    public Map<Integer, ShopItem> itemsBySlot() {
        return pages.getFirst();
    }

    public int pageCount() {
        return pages.size();
    }

    public int normalizePage(int page) {
        if (page <= 0) {
            return 0;
        }
        return Math.min(page, pages.size() - 1);
    }

    public Map<Integer, ShopItem> pageItems(int page) {
        return pages.get(normalizePage(page));
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
