package pl.nauka.aradium.modules.shop;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Menedżer ładujący wielostronicowe kategorie sklepu z katalogu shops/*.yml
 * oraz indeksujący najlepsze ceny sprzedaży dla /sellall i /sellgui.
 */
public class ShopManager {

    private final Logger logger;
    private final Map<String, ShopCategory> categories = new LinkedHashMap<>();
    private final Map<Material, ShopItem> bestSellByMaterial = new EnumMap<>(Material.class);

    public ShopManager(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public void loadFromDirectory(File shopsDirectory) {
        categories.clear();
        bestSellByMaterial.clear();

        if (shopsDirectory == null || !shopsDirectory.isDirectory()) {
            logger.warning("Katalog sklepów nie istnieje: " + shopsDirectory);
            return;
        }

        File[] files = shopsDirectory.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null || files.length == 0) {
            logger.warning("Nie znaleziono żadnych plików .yml w katalogu: " + shopsDirectory.getPath());
            return;
        }

        Arrays.sort(files, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        for (File file : files) {
            String fallbackId = stripExtension(file.getName());
            FileConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            ShopCategory category = parseCategory(yaml, fallbackId, logger);
            if (category != null) {
                categories.put(category.id().toLowerCase(Locale.ROOT), category);
            }
        }

        rebuildBestSellIndex();
        logger.info("Załadowano kategorie sklepu: " + categories.size() + " (" + String.join(", ", categories.keySet()) + ").");
    }

    public static ShopCategory parseCategory(FileConfiguration yaml, String fallbackId, Logger logger) {
        if (yaml == null) {
            return null;
        }
        String rawId = yaml.getString("shop.id", fallbackId);
        if (rawId == null || rawId.isBlank()) {
            return null;
        }

        String id = rawId.trim().toLowerCase(Locale.ROOT);
        String title = yaml.getString("shop.title", "&8Sklep: " + id);
        int size = ShopCategory.normalizeInventorySize(yaml.getInt("shop.size", 54));
        int defaultBackSlot = "main".equals(id) ? -1 : (size >= 27 ? size - 1 : 49);
        int backButtonSlot = yaml.getInt("shop.back-button-slot", defaultBackSlot);
        String parentShopId = yaml.getString("shop.parent-shop", "main".equals(id) ? null : "main");

        Map<String, String> actionLoreFormat = new LinkedHashMap<>();
        ConfigurationSection formatSection = yaml.getConfigurationSection("shop.action-lore-format");
        if (formatSection != null) {
            for (String key : formatSection.getKeys(false)) {
                String value = formatSection.getString(key);
                if (value != null) {
                    actionLoreFormat.put(key, value);
                }
            }
        }

        List<Map<Integer, ShopItem>> pages = new ArrayList<>();
        ConfigurationSection pagesSection = yaml.getConfigurationSection("pages");
        if (pagesSection != null) {
            for (String pageKey : pagesSection.getKeys(false)) {
                ConfigurationSection pageSection = pagesSection.getConfigurationSection(pageKey);
                if (pageSection == null) {
                    continue;
                }
                ConfigurationSection pageItemsSection = pageSection.getConfigurationSection("items");
                if (pageItemsSection == null) {
                    pageItemsSection = pageSection;
                }
                appendParsedPages(pageItemsSection, id, size, backButtonSlot, logger, pages);
            }
        } else {
            ConfigurationSection itemsSection = yaml.getConfigurationSection("items");
            if (itemsSection != null) {
                appendParsedPages(itemsSection, id, size, backButtonSlot, logger, pages);
            }
        }

        if (pages.isEmpty()) {
            pages.add(Map.of());
        }

        return new ShopCategory(id, title, size, backButtonSlot, parentShopId, actionLoreFormat, pages);
    }

    private static void appendParsedPages(
            ConfigurationSection itemsSection,
            String categoryId,
            int inventorySize,
            int backButtonSlot,
            Logger logger,
            List<Map<Integer, ShopItem>> targetPages
    ) {
        if (itemsSection == null) {
            return;
        }

        Map<Integer, ShopItem> explicitItems = new LinkedHashMap<>();
        List<ShopItem> autoItems = new ArrayList<>();

        for (String itemId : itemsSection.getKeys(false)) {
            ConfigurationSection itemSection = itemsSection.getConfigurationSection(itemId);
            if (itemSection == null) {
                continue;
            }
            ParsedItemResult parsed = parseItemSection(itemId, itemSection, categoryId, inventorySize, logger);
            if (parsed == null) {
                continue;
            }
            if (parsed.explicitSlot()) {
                explicitItems.put(parsed.item().slot(), parsed.item());
            } else {
                autoItems.add(parsed.item());
            }
        }

        if (explicitItems.isEmpty() && autoItems.isEmpty()) {
            return;
        }

        List<Integer> gridSlots = computeAutoSlots(inventorySize, backButtonSlot);
        Map<Integer, ShopItem> firstPage = new LinkedHashMap<>(explicitItems);
        int autoIndex = 0;

        for (int candidateSlot : gridSlots) {
            if (autoIndex >= autoItems.size()) {
                break;
            }
            if (firstPage.containsKey(candidateSlot)) {
                continue;
            }
            ShopItem base = autoItems.get(autoIndex++);
            firstPage.put(candidateSlot, withSlot(base, candidateSlot));
        }
        targetPages.add(firstPage);

        // Gdy przedmiotów bez jawnego slotu jest więcej niż pojemność siatki 7x4,
        // dzielimy je automatycznie na kolejne strony bez utraty żadnej pozycji.
        while (autoIndex < autoItems.size() && !gridSlots.isEmpty()) {
            Map<Integer, ShopItem> overflowPage = new LinkedHashMap<>();
            for (int candidateSlot : gridSlots) {
                if (autoIndex >= autoItems.size()) {
                    break;
                }
                ShopItem base = autoItems.get(autoIndex++);
                overflowPage.put(candidateSlot, withSlot(base, candidateSlot));
            }
            targetPages.add(overflowPage);
        }
    }

    private static ParsedItemResult parseItemSection(
            String itemId,
            ConfigurationSection itemSection,
            String categoryId,
            int inventorySize,
            Logger logger
    ) {
        boolean hasExplicitSlot = itemSection.contains("slot");
        int slot = itemSection.getInt("slot", -1);
        if (hasExplicitSlot && (slot < 0 || slot >= inventorySize)) {
            if (logger != null) {
                logger.warning("Pominięto pozycję '" + itemId + "' w sklepie '" + categoryId + "' — nieprawidłowy slot: " + slot);
            }
            return null;
        }

        String matName = itemSection.getString("material", "");
        Material material = Material.matchMaterial(matName.trim().toUpperCase(Locale.ROOT));
        if (!isValidShopMaterial(material)) {
            if (logger != null) {
                logger.warning("Pominięto pozycję '" + itemId + "' w sklepie '" + categoryId + "' — nieznany materiał: " + matName);
            }
            return null;
        }

        String name = itemSection.getString("name");
        if (name == null) {
            name = itemSection.getString("displayname", material.name());
        }
        List<String> lore = itemSection.getStringList("lore");
        double buyPrice = readPrice(itemSection, "buy-price", "buy");
        double sellPrice = readPrice(itemSection, "sell-price", "sell");

        String targetShop = itemSection.getString("target-shop");
        if (targetShop == null) {
            targetShop = itemSection.getString("section");
        }
        if (targetShop != null) {
            targetShop = targetShop.trim().toLowerCase(Locale.ROOT);
        }

        int stackSize = itemSection.getInt("stack-size", 1);
        String customItemId = itemSection.getString("custom-item");
        ShopItem item = new ShopItem(
                itemId,
                Math.max(0, slot),
                material,
                name,
                lore,
                buyPrice,
                sellPrice,
                targetShop,
                stackSize,
                customItemId
        );
        return new ParsedItemResult(item, hasExplicitSlot);
    }

    private static double readPrice(ConfigurationSection section, String primaryKey, String fallbackKey) {
        if (section.contains(primaryKey)) {
            return section.getDouble(primaryKey, -1.0);
        }
        if (section.contains(fallbackKey)) {
            return section.getDouble(fallbackKey, -1.0);
        }
        return -1.0;
    }

    private static ShopItem withSlot(ShopItem item, int slot) {
        return new ShopItem(
                item.id(),
                slot,
                item.material(),
                item.name(),
                item.lore(),
                item.buyPrice(),
                item.sellPrice(),
                item.targetShop(),
                item.defaultStackSize(),
                item.customItemId()
        );
    }

    /**
     * Wyznacza sloty wewnętrznej siatki 7x4 (ramka po bokach oraz pasek nawigacji u dołu).
     */
    public static List<Integer> computeAutoSlots(int inventorySize, int backButtonSlot) {
        int rows = Math.max(1, inventorySize / 9);
        List<Integer> slots = new ArrayList<>();
        if (rows >= 3) {
            int lastContentRow = rows >= 4 ? rows - 2 : rows - 1;
            for (int row = 1; row <= lastContentRow; row++) {
                for (int col = 1; col <= 7; col++) {
                    int slot = row * 9 + col;
                    if (slot != backButtonSlot && slot < inventorySize) {
                        slots.add(slot);
                    }
                }
            }
        }
        if (slots.isEmpty()) {
            for (int slot = 0; slot < inventorySize; slot++) {
                if (slot != backButtonSlot) {
                    slots.add(slot);
                }
            }
        }
        return Collections.unmodifiableList(slots);
    }

    private void rebuildBestSellIndex() {
        bestSellByMaterial.clear();
        for (ShopCategory category : categories.values()) {
            for (Map<Integer, ShopItem> page : category.pages()) {
                for (ShopItem item : page.values()) {
                    if (item.isNavigationItem() || item.hasCustomItem() || !item.canSell()) {
                        continue;
                    }
                    ShopItem currentBest = bestSellByMaterial.get(item.material());
                    if (currentBest == null || item.sellPrice() > currentBest.sellPrice()) {
                        bestSellByMaterial.put(item.material(), item);
                    }
                }
            }
        }
    }

    /**
     * Zwraca pozycję o najwyższej cenie sprzedaży dla podanego materiału ze wszystkich kategorii.
     */
    public Optional<ShopItem> findBestSellableItem(Material material) {
        if (material == null) {
            return Optional.empty();
        }
        ShopItem cached = bestSellByMaterial.get(material);
        if (cached != null) {
            return Optional.of(cached);
        }
        ShopItem best = null;
        for (ShopCategory category : categories.values()) {
            for (Map<Integer, ShopItem> page : category.pages()) {
                for (ShopItem item : page.values()) {
                    if (!item.isNavigationItem() && item.material() == material && item.canSell()) {
                        if (best == null || item.sellPrice() > best.sellPrice()) {
                            best = item;
                        }
                    }
                }
            }
        }
        if (best != null) {
            bestSellByMaterial.put(material, best);
        }
        return Optional.ofNullable(best);
    }

    public Optional<ShopCategory> getCategory(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(categories.get(id.trim().toLowerCase(Locale.ROOT)));
    }

    public Set<String> getCategoryIds() {
        return Collections.unmodifiableSet(categories.keySet());
    }

    public Collection<ShopCategory> getCategories() {
        return Collections.unmodifiableCollection(categories.values());
    }

    private static boolean isValidShopMaterial(Material material) {
        if (material == null
                || material == Material.AIR
                || material == Material.CAVE_AIR
                || material == Material.VOID_AIR
                || material.isLegacy()) {
            return false;
        }
        return Bukkit.getServer() == null || material.isItem();
    }

    private static String stripExtension(String filename) {
        int idx = filename.lastIndexOf('.');
        return idx > 0 ? filename.substring(0, idx) : filename;
    }

    private record ParsedItemResult(ShopItem item, boolean explicitSlot) {
    }
}
