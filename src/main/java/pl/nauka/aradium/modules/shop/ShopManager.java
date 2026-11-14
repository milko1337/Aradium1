package pl.nauka.aradium.modules.shop;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Menedżer ładujący i przechowujący wszystkie kategorie sklepu z katalogu shops/*.yml.
 */
public class ShopManager {

    private final Logger logger;
    private final Map<String, ShopCategory> categories = new LinkedHashMap<>();

    public ShopManager(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public void loadFromDirectory(File shopsDirectory) {
        categories.clear();
        if (shopsDirectory == null || !shopsDirectory.isDirectory()) {
            logger.warning("Katalog sklepów nie istnieje: " + shopsDirectory);
            return;
        }

        File[] files = shopsDirectory.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null || files.length == 0) {
            logger.warning("Nie znaleziono żadnych plików .yml w katalogu: " + shopsDirectory.getPath());
            return;
        }

        for (File file : files) {
            String fallbackId = stripExtension(file.getName());
            FileConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            ShopCategory category = parseCategory(yaml, fallbackId, logger);
            if (category != null) {
                categories.put(category.id().toLowerCase(Locale.ROOT), category);
            }
        }
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
        int backButtonSlot = yaml.getInt("shop.back-button-slot", "main".equals(id) ? -1 : 49);
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

        Map<Integer, ShopItem> itemsBySlot = new LinkedHashMap<>();
        ConfigurationSection itemsSection = yaml.getConfigurationSection("items");
        if (itemsSection != null) {
            for (String itemId : itemsSection.getKeys(false)) {
                ConfigurationSection itemSection = itemsSection.getConfigurationSection(itemId);
                if (itemSection == null) {
                    continue;
                }
                int slot = itemSection.getInt("slot", -1);
                if (slot < 0 || slot >= size) {
                    if (logger != null) {
                        logger.warning("Pominięto pozycję '" + itemId + "' w sklepie '" + id + "' — nieprawidłowy slot: " + slot);
                    }
                    continue;
                }
                String matName = itemSection.getString("material", "");
                Material material = Material.matchMaterial(matName.trim().toUpperCase(Locale.ROOT));
                if (!isValidShopMaterial(material)) {
                    if (logger != null) {
                        logger.warning("Pominięto pozycję '" + itemId + "' w sklepie '" + id + "' — nieznany materiał: " + matName);
                    }
                    continue;
                }

                String name = itemSection.getString("name", material.name());
                List<String> lore = itemSection.getStringList("lore");
                double buyPrice = itemSection.getDouble("buy-price", -1.0);
                double sellPrice = itemSection.getDouble("sell-price", -1.0);
                String targetShop = itemSection.getString("target-shop");
                if (targetShop != null) {
                    targetShop = targetShop.trim().toLowerCase(Locale.ROOT);
                }

                ShopItem item = new ShopItem(itemId, slot, material, name, lore, buyPrice, sellPrice, targetShop);
                itemsBySlot.put(slot, item);
            }
        }

        return new ShopCategory(id, title, size, backButtonSlot, parentShopId, actionLoreFormat, itemsBySlot);
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
}
