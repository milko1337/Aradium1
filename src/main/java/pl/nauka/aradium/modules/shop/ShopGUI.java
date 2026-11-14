package pl.nauka.aradium.modules.shop;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import pl.nauka.aradium.AradiumPlugin;
import pl.nauka.aradium.core.PluginModule;
import pl.nauka.aradium.modules.economy.EconomyManager;
import pl.nauka.aradium.modules.economy.EconomyModule;
import pl.nauka.aradium.utils.ChatUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Generator GUI sklepu z automatycznym lore instrukcji kliknięć (LPM, Shift+LPM, PPM, ŚPM)
 * oraz bezpieczną obsługą kupna i sprzedaży przedmiotów.
 */
public class ShopGUI {

    private static final int STACK_BUY_AMOUNT = 64;

    private final AradiumPlugin plugin;
    private final ShopManager shopManager;

    public ShopGUI(AradiumPlugin plugin, ShopManager shopManager) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.shopManager = Objects.requireNonNull(shopManager, "shopManager");
    }

    public boolean openCategory(Player player, String categoryId) {
        ShopCategory category = shopManager.getCategory(categoryId).orElse(null);
        if (category == null) {
            player.sendMessage(ChatUtils.getMessage(
                            plugin.getConfig(),
                            "messages.shop_category_not_found",
                            "&cNie znaleziono kategorii sklepu: &f%category%&c!"
                    )
                    .replace("%category%", categoryId != null ? categoryId : ""));
            return false;
        }

        Inventory inventory = createInventory(category);
        player.openInventory(inventory);
        return true;
    }

    public Inventory createInventory(ShopCategory category) {
        ShopInventoryHolder holder = new ShopInventoryHolder(category.id());
        Inventory inventory = Bukkit.createInventory(holder, category.size(), ChatUtils.component(category.title()));
        holder.setInventory(inventory);

        ItemStack filler = createFillerItem();
        for (int slot = 0; slot < category.size(); slot++) {
            inventory.setItem(slot, filler);
        }

        for (ShopItem item : category.itemsBySlot().values()) {
            inventory.setItem(item.slot(), createDisplayItem(category, item));
        }

        if (category.backButtonSlot() >= 0
                && category.backButtonSlot() < category.size()
                && category.parentShopId() != null) {
            inventory.setItem(category.backButtonSlot(), createBackButton(category.parentShopId()));
        }

        return inventory;
    }

    public void handleClick(Player player, String categoryId, int slot, ClickType clickType) {
        ShopCategory category = shopManager.getCategory(categoryId).orElse(null);
        if (category == null) {
            return;
        }

        if (slot == category.backButtonSlot() && category.parentShopId() != null) {
            String parentId = category.parentShopId();
            plugin.getServer().getScheduler().runTask(plugin, () -> openCategory(player, parentId));
            return;
        }

        ShopItem item = category.itemsBySlot().get(slot);
        if (item == null) {
            return;
        }

        if (item.isNavigationItem()) {
            String targetId = item.targetShop();
            plugin.getServer().getScheduler().runTask(plugin, () -> openCategory(player, targetId));
            return;
        }

        EconomyManager economy = resolveEconomyManager();
        if (economy == null) {
            player.sendMessage(ChatUtils.getMessage(
                    plugin.getConfig(),
                    "messages.economy_disabled",
                    "&cModuł ekonomii jest aktualnie wyłączony!"
            ));
            return;
        }

        if (clickType == ClickType.LEFT) {
            handleBuy(player, economy, item, 1);
        } else if (clickType == ClickType.SHIFT_LEFT) {
            handleBuy(player, economy, item, STACK_BUY_AMOUNT);
        } else if (clickType == ClickType.RIGHT) {
            handleSellOne(player, economy, item);
        } else if (clickType == ClickType.MIDDLE || clickType == ClickType.SHIFT_RIGHT) {
            handleSellAll(player, economy, item);
        }
    }

    private void handleBuy(Player player, EconomyManager economy, ShopItem item, int amount) {
        if (!item.canBuy()) {
            player.sendMessage(ChatUtils.getMessage(
                    plugin.getConfig(),
                    "messages.shop_buy_disabled",
                    "&cKupno tego przedmiotu jest wyłączone!"
            ));
            return;
        }

        double totalPrice = EconomyManager.normalizeAmount(item.buyPrice() * amount);
        if (totalPrice < 0.0) {
            return;
        }

        if (!economy.hasMoney(player.getUniqueId(), totalPrice)) {
            player.sendMessage(ChatUtils.getMessage(
                            plugin.getConfig(),
                            "messages.shop_insufficient_funds",
                            "&cNie stać Cię! Koszt zakupu to &e%price%&c."
                    )
                    .replace("%price%", EconomyManager.formatMoney(totalPrice)));
            return;
        }

        PlayerInventory inventory = player.getInventory();
        if (getAvailableSpace(inventory, item.material()) < amount) {
            player.sendMessage(ChatUtils.getMessage(
                    plugin.getConfig(),
                    "messages.shop_inventory_full",
                    "&cNie masz wystarczająco miejsca w ekwipunku na ten zakup!"
            ));
            return;
        }

        if (!economy.takeMoney(player.getUniqueId(), totalPrice)) {
            player.sendMessage(ChatUtils.getMessage(
                            plugin.getConfig(),
                            "messages.shop_insufficient_funds",
                            "&cNie stać Cię! Koszt zakupu to &e%price%&c."
                    )
                    .replace("%price%", EconomyManager.formatMoney(totalPrice)));
            return;
        }

        int maxStack = Math.max(1, item.material().getMaxStackSize());
        int remaining = amount;
        while (remaining > 0) {
            int stackSize = Math.min(remaining, maxStack);
            Map<Integer, ItemStack> overflow = inventory.addItem(new ItemStack(item.material(), stackSize));
            if (!overflow.isEmpty()) {
                for (ItemStack leftover : overflow.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), leftover);
                }
            }
            remaining -= stackSize;
        }

        player.sendMessage(ChatUtils.getMessage(
                        plugin.getConfig(),
                        "messages.shop_bought",
                        "&aKupiono &e%amount%x %item% &aza &e%price%&a!"
                )
                .replace("%amount%", String.valueOf(amount))
                .replace("%item%", ChatUtils.color(item.name()))
                .replace("%price%", EconomyManager.formatMoney(totalPrice)));
    }

    private void handleSellOne(Player player, EconomyManager economy, ShopItem item) {
        if (!item.canSell()) {
            player.sendMessage(ChatUtils.getMessage(
                    plugin.getConfig(),
                    "messages.shop_sell_disabled",
                    "&cSprzedaż tego przedmiotu jest wyłączona!"
            ));
            return;
        }

        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getStorageContents();
        boolean sold = false;
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (!isSellableVanillaItem(stack, item.material())) {
                continue;
            }
            if (stack.getAmount() <= 1) {
                contents[i] = null;
            } else {
                stack.setAmount(stack.getAmount() - 1);
            }
            sold = true;
            break;
        }

        if (!sold) {
            player.sendMessage(ChatUtils.getMessage(
                    plugin.getConfig(),
                    "messages.shop_no_items_to_sell",
                    "&cNie posiadasz w ekwipunku tego przedmiotu do sprzedaży!"
            ));
            return;
        }

        inventory.setStorageContents(contents);
        double earned = EconomyManager.normalizeAmount(item.sellPrice());
        if (earned > 0.0) {
            economy.addMoney(player.getUniqueId(), earned);
        }

        player.sendMessage(ChatUtils.getMessage(
                        plugin.getConfig(),
                        "messages.shop_sold",
                        "&aSprzedano &e%amount%x %item% &aza &a%price%&a!"
                )
                .replace("%amount%", "1")
                .replace("%item%", ChatUtils.color(item.name()))
                .replace("%price%", EconomyManager.formatMoney(Math.max(0.0, earned))));
    }

    private void handleSellAll(Player player, EconomyManager economy, ShopItem item) {
        if (!item.canSell()) {
            player.sendMessage(ChatUtils.getMessage(
                    plugin.getConfig(),
                    "messages.shop_sell_disabled",
                    "&cSprzedaż tego przedmiotu jest wyłączona!"
            ));
            return;
        }

        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getStorageContents();
        int totalSold = 0;

        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (!isSellableVanillaItem(stack, item.material())) {
                continue;
            }
            totalSold += stack.getAmount();
            contents[i] = null;
        }

        if (totalSold <= 0) {
            player.sendMessage(ChatUtils.getMessage(
                    plugin.getConfig(),
                    "messages.shop_no_items_to_sell",
                    "&cNie posiadasz w ekwipunku tego przedmiotu do sprzedaży!"
            ));
            return;
        }

        inventory.setStorageContents(contents);
        double totalEarned = EconomyManager.normalizeAmount(item.sellPrice() * totalSold);
        if (totalEarned > 0.0) {
            economy.addMoney(player.getUniqueId(), totalEarned);
        }

        player.sendMessage(ChatUtils.getMessage(
                        plugin.getConfig(),
                        "messages.shop_sold",
                        "&aSprzedano &e%amount%x %item% &aza &a%price%&a!"
                )
                .replace("%amount%", String.valueOf(totalSold))
                .replace("%item%", ChatUtils.color(item.name()))
                .replace("%price%", EconomyManager.formatMoney(Math.max(0.0, totalEarned))));
    }

    private EconomyManager resolveEconomyManager() {
        return plugin.getEconomyModule()
                .filter(PluginModule::isEnabled)
                .map(EconomyModule::getEconomyManager)
                .orElse(null);
    }

    private static int getAvailableSpace(PlayerInventory inventory, Material material) {
        int maxStackSize = Math.max(1, material.getMaxStackSize());
        int capacity = 0;
        for (ItemStack stack : inventory.getStorageContents()) {
            if (stack == null || stack.getType() == Material.AIR) {
                capacity += maxStackSize;
            } else if (isSellableVanillaItem(stack, material) && stack.getAmount() < maxStackSize) {
                capacity += (maxStackSize - stack.getAmount());
            }
        }
        return capacity;
    }

    /**
     * Sprawdza, czy przedmiot w ekwipunku jest czystym waniliowym itemem bez metadanych i PDC,
     * co chroni magiczne przedmioty oraz zbroje przed przypadkową sprzedażą w sklepie.
     */
    public static boolean isSellableVanillaItem(ItemStack stack, Material expectedMaterial) {
        if (stack == null || expectedMaterial == null || stack.getType() != expectedMaterial || stack.getAmount() <= 0) {
            return false;
        }
        if (!stack.hasItemMeta()) {
            return true;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return true;
        }
        if (meta.hasDisplayName() || meta.hasLore() || meta.hasEnchants()) {
            return false;
        }
        if (meta.hasCustomModelDataComponent() || meta.isUnbreakable()) {
            return false;
        }
        if (meta instanceof Damageable damageable && damageable.hasDamage()) {
            return false;
        }
        if (!meta.getPersistentDataContainer().getKeys().isEmpty()) {
            return false;
        }
        return !(meta instanceof EnchantmentStorageMeta storage) || !storage.hasStoredEnchants();
    }

    /**
     * Generuje kompletne linie lore przedmiotu w sklepie wraz z instrukcjami LPM / Shift+LPM / PPM / ŚPM.
     */
    public static List<String> buildRawLoreLines(ShopCategory category, ShopItem item) {
        List<String> lines = new ArrayList<>(item.lore());
        if (item.isNavigationItem()) {
            return lines;
        }

        if (!lines.isEmpty()) {
            lines.add("");
        }

        Map<String, String> format = category.actionLoreFormat();
        if (item.canBuy()) {
            String buy1 = EconomyManager.formatAmount(item.buyPrice());
            String buy64 = EconomyManager.formatAmount(EconomyManager.normalizeAmount(item.buyPrice() * STACK_BUY_AMOUNT));
            lines.add(format.getOrDefault("buy-enabled", "&aLPM &8- &7Kup 1 szt. za &e%buy_1%$")
                    .replace("%buy_1%", buy1));
            lines.add(format.getOrDefault("buy-stack-enabled", "&aShift+LPM &8- &7Kup 64 szt. za &e%buy_64%$")
                    .replace("%buy_64%", buy64));
        } else {
            lines.add(format.getOrDefault("buy-disabled", "&cKupno tego przedmiotu jest wyłączone"));
        }

        if (item.canSell()) {
            String sell1 = EconomyManager.formatAmount(item.sellPrice());
            lines.add(format.getOrDefault("sell-enabled", "&ePPM &8- &7Sprzedaj 1 szt. za &a%sell_1%$")
                    .replace("%sell_1%", sell1));
            lines.add(format.getOrDefault("sell-all-enabled", "&6ŚPM (Kółko) &8- &7Sprzedaj wszystko z ekwipunku"));
        } else {
            lines.add(format.getOrDefault("sell-disabled", "&cSprzedaż tego przedmiotu jest wyłączona"));
        }

        return lines;
    }

    private static ItemStack createDisplayItem(ShopCategory category, ShopItem item) {
        ItemStack stack = new ItemStack(item.material());
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(ChatUtils.itemComponent(item.name()));
            List<Component> loreComponents = buildRawLoreLines(category, item).stream()
                    .map(line -> line.isEmpty() ? Component.empty() : ChatUtils.itemComponent(line))
                    .toList();
            meta.lore(loreComponents);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static ItemStack createBackButton(String parentShopId) {
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta meta = back.getItemMeta();
        if (meta != null) {
            meta.displayName(ChatUtils.itemComponent("&c&lPowrót"));
            meta.lore(List.of(ChatUtils.itemComponent("&7Kliknij, aby wrócić do: &e" + parentShopId)));
            back.setItemMeta(meta);
        }
        return back;
    }

    private static ItemStack createFillerItem() {
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();
        if (meta != null) {
            meta.displayName(ChatUtils.itemComponent(" "));
            filler.setItemMeta(meta);
        }
        return filler;
    }
}
