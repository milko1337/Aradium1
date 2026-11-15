package pl.nauka.aradium.modules.shop;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
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
 * Generator GUI sklepu obsługujący wielostronicowe kategorie, okna wyboru ilości
 * (BuyScreen, SellScreen, BuyStacksScreen), okno /sellgui oraz bezpieczne transakcje Anti-Dupe.
 */
public class ShopGUI {

    public static final int CATEGORY_NAV_ROW_START = 45;
    public static final int PREV_PAGE_SLOT = 48;
    public static final int PAGE_INFO_SLOT = 49;
    public static final int NEXT_PAGE_SLOT = 50;
    public static final int CLOSE_OR_BACK_SLOT = 53;

    public static final int TRANSACTION_GUI_SIZE = 45;
    public static final int TX_CONFIRM_SLOT = 13;
    public static final int TX_PREVIEW_SLOT = 22;
    public static final int TX_MODE_SWITCH_SLOT = 31;
    public static final int TX_PLAYER_HEAD_SLOT = 36;
    public static final int TX_BACK_SLOT = 44;

    public static final int SELL_GUI_SIZE = 54;
    public static final int SELL_GUI_CONTENT_LIMIT = 45;
    public static final int SELL_GUI_HEAD_SLOT = 45;
    public static final int SELL_GUI_INFO_SLOT = 49;
    public static final int SELL_GUI_CONFIRM_SLOT = 53;

    private static final int STACK_BUY_AMOUNT = 64;
    private static final int MAX_SINGLE_SCREEN_AMOUNT = 64;
    private static final int MAX_STACK_SCREEN_AMOUNT = 36;

    private static final Map<Integer, Integer> QUANTITY_DELTAS = Map.of(
            19, -32,
            20, -16,
            21, -1,
            23, 1,
            24, 16,
            25, 32
    );

    private final AradiumPlugin plugin;
    private final ShopManager shopManager;
    private final NamespacedKey uiButtonKey;

    public ShopGUI(AradiumPlugin plugin, ShopManager shopManager) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.shopManager = Objects.requireNonNull(shopManager, "shopManager");
        this.uiButtonKey = new NamespacedKey(plugin, "shop_ui_button");
    }

    public boolean openCategory(Player player, String categoryId) {
        return openCategory(player, categoryId, 0);
    }

    public boolean openCategory(Player player, String categoryId, int page) {
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

        int safePage = category.normalizePage(page);
        Inventory inventory = createInventory(category, safePage, player);
        player.openInventory(inventory);
        return true;
    }

    public Inventory createInventory(ShopCategory category) {
        return createInventory(category, 0, null);
    }

    public Inventory createInventory(ShopCategory category, int page, Player player) {
        int safePage = category.normalizePage(page);
        ShopInventoryHolder holder = new ShopInventoryHolder(
                category.id(),
                safePage,
                ShopInventoryHolder.ScreenType.CATEGORY,
                null,
                1
        );

        String title = category.title();
        if (category.pageCount() > 1) {
            title += " &8(&e" + (safePage + 1) + "&8/&e" + category.pageCount() + "&8)";
        }

        Inventory inventory = Bukkit.createInventory(holder, category.size(), ChatUtils.component(title));
        holder.setInventory(inventory);

        ItemStack filler = createFillerItem();
        for (int slot = 0; slot < category.size(); slot++) {
            inventory.setItem(slot, filler);
        }

        for (ShopItem item : category.pageItems(safePage).values()) {
            inventory.setItem(item.slot(), createDisplayItem(category, item));
        }

        populateCategoryNavigationBar(inventory, category, safePage, player);
        return inventory;
    }

    private void populateCategoryNavigationBar(Inventory inventory, ShopCategory category, int page, Player player) {
        int size = category.size();
        if (size >= 27) {
            int navStart = size - 9;
            if (player != null) {
                inventory.setItem(navStart, createPlayerProfileItem(player));
            }
            if (category.pageCount() > 1) {
                if (page > 0) {
                    inventory.setItem(navStart + 3, createUiButton(
                            Material.RED_STAINED_GLASS_PANE,
                            "&d&l« Poprzednia strona",
                            List.of("&7Kliknij, aby wrócić na stronę &e" + page + "&7."),
                            1
                    ));
                }
                inventory.setItem(navStart + 4, createUiButton(
                        Material.NETHER_STAR,
                        "&e&lStrona &6" + (page + 1) + "&8/&6" + category.pageCount(),
                        List.of(
                                "&7Użyj przycisków obok, aby przełączać strony.",
                                "&aLPM &8- &7Wybór ilości kupna",
                                "&ePPM &8- &7Wybór ilości sprzedaży"
                        ),
                        1
                ));
                if (page + 1 < category.pageCount()) {
                    inventory.setItem(navStart + 5, createUiButton(
                            Material.BLUE_STAINED_GLASS_PANE,
                            "&b&lNastępna strona »",
                            List.of("&7Kliknij, aby przejść na stronę &e" + (page + 2) + "&7."),
                            1
                    ));
                }
            } else if ("main".equals(category.id())) {
                inventory.setItem(navStart + 4, createUiButton(
                        Material.NETHER_STAR,
                        "&6&lSklep Serwerowy Aradium",
                        List.of(
                                "&7Wybierz kategorię, aby przeglądać asortyment.",
                                "&aLPM &8- &7Kup przedmiot &8(&fShift+LPM &7kup 64&8)",
                                "&ePPM &8- &7Sprzedaj przedmiot &8(&fShift+PPM &7sprzedaj wszystko&8)",
                                "&7Komendy: &e/sellall &7oraz &e/sellgui"
                        ),
                        1
                ));
            }

            if ("main".equals(category.id()) && category.parentShopId() == null) {
                inventory.setItem(size - 1, createUiButton(
                        Material.BARRIER,
                        "&c&lZamknij sklep",
                        List.of("&7Kliknij, aby zamknąć menu sklepu."),
                        1
                ));
            }
        }

        if (category.backButtonSlot() >= 0
                && category.backButtonSlot() < size
                && category.parentShopId() != null) {
            inventory.setItem(category.backButtonSlot(), createBackButton(category.parentShopId()));
        }
    }

    public void openTransactionScreen(
            Player player,
            String categoryId,
            int page,
            ShopItem item,
            ShopInventoryHolder.ScreenType screenType,
            int initialAmount
    ) {
        if (player == null || item == null || screenType == ShopInventoryHolder.ScreenType.CATEGORY
                || screenType == ShopInventoryHolder.ScreenType.SELL_GUI) {
            return;
        }

        int maxAmount = getMaxSelectableAmount(screenType);
        int safeAmount = Math.max(1, Math.min(maxAmount, initialAmount));
        ShopInventoryHolder holder = new ShopInventoryHolder(categoryId, page, screenType, item, safeAmount);

        String title = switch (screenType) {
            case BUY_SCREEN -> "&8» &aKupno: &f" + stripColorCodesOrKeep(item.name());
            case BUY_STACKS_SCREEN -> "&8» &2Kupno Staków: &f" + stripColorCodesOrKeep(item.name());
            case SELL_SCREEN -> "&8» &eSprzedaż: &f" + stripColorCodesOrKeep(item.name());
            default -> "&8» &6Transakcja";
        };

        Inventory inventory = Bukkit.createInventory(holder, TRANSACTION_GUI_SIZE, ChatUtils.component(title));
        holder.setInventory(inventory);
        populateTransactionInventory(inventory, player, holder);
        player.openInventory(inventory);
    }

    private void populateTransactionInventory(Inventory inventory, Player player, ShopInventoryHolder holder) {
        ItemStack filler = createFillerItem();
        for (int slot = 0; slot < TRANSACTION_GUI_SIZE; slot++) {
            inventory.setItem(slot, filler);
        }

        ShopInventoryHolder.ScreenType screenType = holder.getScreenType();
        ShopItem item = holder.getSelectedItem();
        int selectedAmount = holder.getSelectedAmount();
        int maxAmount = getMaxSelectableAmount(screenType);
        boolean stacksMode = screenType == ShopInventoryHolder.ScreenType.BUY_STACKS_SCREEN;
        int unitMultiplier = stacksMode ? Math.max(1, item.material().getMaxStackSize()) : 1;
        int totalItems = selectedAmount * unitMultiplier;

        for (Map.Entry<Integer, Integer> entry : QUANTITY_DELTAS.entrySet()) {
            int delta = entry.getValue();
            int nextValue = calculateAdjustedAmount(selectedAmount, delta, 1, maxAmount);
            boolean removeButton = delta < 0;
            Material paneMaterial = removeButton ? Material.MAGENTA_STAINED_GLASS_PANE : Material.LIGHT_BLUE_STAINED_GLASS_PANE;
            String sign = delta > 0 ? "+" : "";
            String color = removeButton ? "&d&l" : "&b&l";
            String unitLabel = stacksMode ? " st." : " szt.";
            inventory.setItem(entry.getKey(), createUiButton(
                    paneMaterial,
                    color + sign + delta + unitLabel,
                    List.of("&7Po kliknięciu: &e" + nextValue + (stacksMode ? " st. (&f" + (nextValue * unitMultiplier) + " szt.&e)" : " szt.")),
                    Math.abs(delta)
            ));
        }

        double unitPrice = (screenType == ShopInventoryHolder.ScreenType.SELL_SCREEN) ? item.sellPrice() : item.buyPrice();
        double totalPrice = EconomyManager.normalizeAmount(unitPrice * totalItems);

        ItemStack preview = new ItemStack(
                item.material(),
                Math.max(1, Math.min(selectedAmount, item.material().getMaxStackSize()))
        );
        ItemMeta previewMeta = preview.getItemMeta();
        if (previewMeta != null) {
            previewMeta.displayName(ChatUtils.itemComponent(item.name()));
            List<Component> previewLore = new ArrayList<>();
            if (stacksMode) {
                previewLore.add(ChatUtils.itemComponent("&7Wybrano staków: &e" + selectedAmount + " &8(&f" + totalItems + " szt.&8)"));
            } else {
                previewLore.add(ChatUtils.itemComponent("&7Wybrana ilość: &e" + totalItems + " szt."));
            }
            previewLore.add(ChatUtils.itemComponent(
                    screenType == ShopInventoryHolder.ScreenType.SELL_SCREEN
                            ? "&7Wartość sprzedaży: &a" + EconomyManager.formatMoney(totalPrice)
                            : "&7Koszt zakupu: &e" + EconomyManager.formatMoney(totalPrice)
            ));
            previewMeta.lore(previewLore);
            previewMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            previewMeta.getPersistentDataContainer().set(uiButtonKey, PersistentDataType.BYTE, (byte) 1);
            preview.setItemMeta(previewMeta);
        }
        inventory.setItem(TX_PREVIEW_SLOT, preview);

        if (screenType == ShopInventoryHolder.ScreenType.SELL_SCREEN) {
            int ownedCount = countSellableVanillaItems(player.getInventory(), item.material());
            double sellAllTotal = EconomyManager.normalizeAmount(item.sellPrice() * ownedCount);
            inventory.setItem(TX_CONFIRM_SLOT, createUiButton(
                    Material.PAPER,
                    "&a&lSprzedać wybraną ilość?",
                    List.of(
                            "&7Przedmiot: " + item.name(),
                            "&7Ilość do sprzedaży: &e" + totalItems + " szt.",
                            "&7Otrzymasz: &a+" + EconomyManager.formatMoney(totalPrice),
                            "&7Posiadasz w ekwipunku: &f" + ownedCount + " szt.",
                            "",
                            ownedCount >= totalItems ? "&aKliknij, aby sprzedać!" : "&cMasz za mało sztuk w ekwipunku!"
                    ),
                    1
            ));
            inventory.setItem(TX_MODE_SWITCH_SLOT, createUiButton(
                    Material.EMERALD,
                    "&c&lSprzedaj wszystko",
                    List.of(
                            "&7Sprzedaje wszystkie pasujące przedmioty",
                            "&7z Twojego ekwipunku.",
                            "&7Łącznie: &f" + ownedCount + " szt. &8(&a+" + EconomyManager.formatMoney(sellAllTotal) + "&8)"
                    ),
                    1
            ));
        } else {
            EconomyManager economy = resolveEconomyManager();
            boolean enoughMoney = economy != null && economy.hasMoney(player.getUniqueId(), totalPrice);
            inventory.setItem(TX_CONFIRM_SLOT, createUiButton(
                    Material.PAPER,
                    "&a&lZakupić?",
                    List.of(
                            "&7Przedmiot: " + item.name(),
                            stacksMode
                                    ? "&7Ilość: &e" + selectedAmount + " st. &8(&f" + totalItems + " szt.&8)"
                                    : "&7Ilość: &e" + totalItems + " szt.",
                            "&7Cena całkowita: &e" + EconomyManager.formatMoney(totalPrice),
                            "",
                            enoughMoney ? "&aKliknij, aby potwierdzić zakup!" : "&cNiewystarczające środki na koncie!"
                    ),
                    1
            ));
            if (screenType == ShopInventoryHolder.ScreenType.BUY_SCREEN) {
                inventory.setItem(TX_MODE_SWITCH_SLOT, createUiButton(
                        Material.CHEST,
                        "&a&lKup w Stakach",
                        List.of("&7Kliknij, aby wybrać ilość całych staków (1–36)."),
                        1
                ));
            } else {
                inventory.setItem(TX_MODE_SWITCH_SLOT, createUiButton(
                        Material.BOOK,
                        "&e&lKup pojedyncze sztuki",
                        List.of("&7Kliknij, aby wrócić do zakupu na sztuki (1–64)."),
                        1
                ));
            }
        }

        inventory.setItem(TX_PLAYER_HEAD_SLOT, createPlayerProfileItem(player));
        inventory.setItem(TX_BACK_SLOT, createBackButton(holder.getCategoryId()));
    }

    public void openSellGui(Player player) {
        if (player == null) {
            return;
        }
        ShopInventoryHolder holder = new ShopInventoryHolder(
                "sellgui",
                0,
                ShopInventoryHolder.ScreenType.SELL_GUI,
                null,
                1
        );
        Inventory inventory = Bukkit.createInventory(holder, SELL_GUI_SIZE, ChatUtils.component("&8» &2&lSzybka Sprzedaż &8(&aSellGUI&8)"));
        holder.setInventory(inventory);

        ItemStack filler = createFillerItem();
        for (int slot = SELL_GUI_CONTENT_LIMIT; slot < SELL_GUI_SIZE; slot++) {
            inventory.setItem(slot, filler);
        }

        inventory.setItem(SELL_GUI_HEAD_SLOT, createPlayerProfileItem(player));
        inventory.setItem(SELL_GUI_INFO_SLOT, createUiButton(
                Material.NETHER_STAR,
                "&a&lJak działa SellGUI?",
                List.of(
                        "&7Wrzuć do górnych slotów dowolne",
                        "&7przedmioty ze swojego ekwipunku.",
                        "&7Po kliknięciu szmaragdu lub zamknięciu",
                        "&7sprzedamy je po najwyższej cenie w sklepie.",
                        "&ePrzedmioty niesprzedawalne wrócą do Ciebie!"
                ),
                1
        ));
        inventory.setItem(SELL_GUI_CONFIRM_SLOT, createUiButton(
                Material.EMERALD,
                "&a&lSprzedaj zawartość i zamknij",
                List.of("&7Kliknij, aby natychmiast sprzedać przedmioty."),
                1
        ));

        player.openInventory(inventory);
    }

    public void processSellGuiClose(Player player, Inventory inventory) {
        if (player == null || inventory == null
                || !(inventory.getHolder() instanceof ShopInventoryHolder holder)
                || holder.getScreenType() != ShopInventoryHolder.ScreenType.SELL_GUI) {
            return;
        }
        if (holder.isSellProcessed()) {
            return;
        }
        holder.markSellProcessed();

        EconomyManager economy = resolveEconomyManager();
        List<ItemStack> unsellable = new ArrayList<>();
        int totalSold = 0;
        double totalEarned = 0.0;

        for (int slot = 0; slot < SELL_GUI_CONTENT_LIMIT; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack == null || stack.getType() == Material.AIR) {
                continue;
            }
            inventory.setItem(slot, null);

            if (isUiButton(stack) || economy == null) {
                unsellable.add(stack);
                continue;
            }

            ShopItem bestMatch = shopManager.findBestSellableItem(stack.getType()).orElse(null);
            if (bestMatch == null || !isSellableVanillaItem(stack, stack.getType())) {
                unsellable.add(stack);
                continue;
            }

            totalSold += stack.getAmount();
            totalEarned += bestMatch.sellPrice() * stack.getAmount();
        }

        double normalizedEarned = EconomyManager.normalizeAmount(totalEarned);
        if (totalSold > 0 && economy != null && normalizedEarned > 0.0) {
            economy.addMoney(player.getUniqueId(), normalizedEarned);
            player.sendMessage(ChatUtils.getMessage(
                            plugin.getConfig(),
                            "messages.shop_sellgui_sold",
                            "&aSprzedano &e%amount% szt. &aprzedmiotów za &a%price%&a!"
                    )
                    .replace("%amount%", String.valueOf(totalSold))
                    .replace("%price%", EconomyManager.formatMoney(normalizedEarned)));
        } else if (!unsellable.isEmpty() && economy == null) {
            player.sendMessage(ChatUtils.getMessage(
                    plugin.getConfig(),
                    "messages.economy_disabled",
                    "&cModuł ekonomii jest aktualnie wyłączony!"
            ));
        }

        if (!unsellable.isEmpty()) {
            returnItemStacksToPlayer(player, unsellable);
            player.sendMessage(ChatUtils.getMessage(
                            plugin.getConfig(),
                            "messages.shop_sellgui_returned",
                            "&eZwrócono &f%amount% &enieobsługiwanych stosów przedmiotów do ekwipunku."
                    )
                    .replace("%amount%", String.valueOf(unsellable.size())));
        }
    }

    /**
     * Sprzedaje wszystkie pasujące waniliowe przedmioty z ekwipunku lub głównej ręki (/sellall).
     */
    public boolean sellAllMatchingFromInventory(Player player, Material filterMaterial, boolean mainHandOnly) {
        if (player == null) {
            return false;
        }
        EconomyManager economy = resolveEconomyManager();
        if (economy == null) {
            player.sendMessage(ChatUtils.getMessage(
                    plugin.getConfig(),
                    "messages.economy_disabled",
                    "&cModuł ekonomii jest aktualnie wyłączony!"
            ));
            return false;
        }

        PlayerInventory inventory = player.getInventory();
        int totalSold = 0;
        double totalEarned = 0.0;

        if (mainHandOnly) {
            ItemStack hand = inventory.getItemInMainHand();
            if (hand != null && hand.getType() != Material.AIR) {
                ShopItem bestMatch = shopManager.findBestSellableItem(hand.getType()).orElse(null);
                if (bestMatch != null && isSellableVanillaItem(hand, hand.getType())) {
                    totalSold = hand.getAmount();
                    totalEarned = bestMatch.sellPrice() * totalSold;
                    inventory.setItemInMainHand(null);
                }
            }
        } else {
            ItemStack[] contents = inventory.getStorageContents();
            for (int i = 0; i < contents.length; i++) {
                ItemStack stack = contents[i];
                if (stack == null || stack.getType() == Material.AIR) {
                    continue;
                }
                if (filterMaterial != null && stack.getType() != filterMaterial) {
                    continue;
                }
                ShopItem bestMatch = shopManager.findBestSellableItem(stack.getType()).orElse(null);
                if (bestMatch == null || !isSellableVanillaItem(stack, stack.getType())) {
                    continue;
                }
                totalSold += stack.getAmount();
                totalEarned += bestMatch.sellPrice() * stack.getAmount();
                contents[i] = null;
            }
            if (totalSold > 0) {
                inventory.setStorageContents(contents);
            }
        }

        if (totalSold <= 0) {
            player.sendMessage(ChatUtils.getMessage(
                    plugin.getConfig(),
                    "messages.shop_no_items_to_sell",
                    "&cNie posiadasz w ekwipunku tego przedmiotu do sprzedaży!"
            ));
            return false;
        }

        double normalizedEarned = EconomyManager.normalizeAmount(totalEarned);
        if (normalizedEarned > 0.0) {
            economy.addMoney(player.getUniqueId(), normalizedEarned);
        }

        player.sendMessage(ChatUtils.getMessage(
                        plugin.getConfig(),
                        "messages.shop_sellall_success",
                        "&aSprzedano łącznie &e%amount% szt. &aprzedmiotów za &a%price%&a!"
                )
                .replace("%amount%", String.valueOf(totalSold))
                .replace("%price%", EconomyManager.formatMoney(Math.max(0.0, normalizedEarned))));
        return true;
    }

    public void handleClick(Player player, String categoryId, int slot, ClickType clickType) {
        handleClick(player, new ShopInventoryHolder(categoryId), slot, clickType);
    }

    public void handleClick(Player player, ShopInventoryHolder holder, int rawSlot, ClickType clickType) {
        if (player == null || holder == null) {
            return;
        }

        switch (holder.getScreenType()) {
            case CATEGORY -> handleCategoryClick(player, holder, rawSlot, clickType);
            case BUY_SCREEN, SELL_SCREEN, BUY_STACKS_SCREEN -> handleTransactionClick(player, holder, rawSlot);
            case SELL_GUI -> {
                if (rawSlot == SELL_GUI_CONFIRM_SLOT) {
                    plugin.getServer().getScheduler().runTask(plugin, () -> player.closeInventory());
                }
            }
        }
    }

    private void handleCategoryClick(Player player, ShopInventoryHolder holder, int slot, ClickType clickType) {
        ShopCategory category = shopManager.getCategory(holder.getCategoryId()).orElse(null);
        if (category == null) {
            return;
        }

        int currentPage = category.normalizePage(holder.getPage());
        int size = category.size();
        int navStart = size >= 27 ? size - 9 : -1;

        if (category.pageCount() > 1 && navStart >= 0) {
            if (slot == navStart + 3 && currentPage > 0) {
                plugin.getServer().getScheduler().runTask(plugin, () -> openCategory(player, category.id(), currentPage - 1));
                return;
            }
            if (slot == navStart + 5 && currentPage + 1 < category.pageCount()) {
                plugin.getServer().getScheduler().runTask(plugin, () -> openCategory(player, category.id(), currentPage + 1));
                return;
            }
        }

        if (slot == category.backButtonSlot() && category.parentShopId() != null) {
            String parentId = category.parentShopId();
            plugin.getServer().getScheduler().runTask(plugin, () -> openCategory(player, parentId, 0));
            return;
        }

        if ("main".equals(category.id()) && category.parentShopId() == null && size >= 27 && slot == size - 1) {
            plugin.getServer().getScheduler().runTask(plugin, () -> player.closeInventory());
            return;
        }

        ShopItem item = category.pageItems(currentPage).get(slot);
        if (item == null) {
            return;
        }

        if (item.isNavigationItem()) {
            String targetId = item.targetShop();
            plugin.getServer().getScheduler().runTask(plugin, () -> openCategory(player, targetId, 0));
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
            if (!item.canBuy()) {
                player.sendMessage(ChatUtils.getMessage(
                        plugin.getConfig(),
                        "messages.shop_buy_disabled",
                        "&cKupno tego przedmiotu jest wyłączone!"
                ));
                return;
            }
            plugin.getServer().getScheduler().runTask(plugin, () -> openTransactionScreen(
                    player,
                    category.id(),
                    currentPage,
                    item,
                    ShopInventoryHolder.ScreenType.BUY_SCREEN,
                    item.defaultStackSize()
            ));
        } else if (clickType == ClickType.SHIFT_LEFT) {
            handleBuy(player, economy, item, STACK_BUY_AMOUNT);
        } else if (clickType == ClickType.RIGHT) {
            if (!item.canSell()) {
                player.sendMessage(ChatUtils.getMessage(
                        plugin.getConfig(),
                        "messages.shop_sell_disabled",
                        "&cSprzedaż tego przedmiotu jest wyłączona!"
                ));
                return;
            }
            plugin.getServer().getScheduler().runTask(plugin, () -> openTransactionScreen(
                    player,
                    category.id(),
                    currentPage,
                    item,
                    ShopInventoryHolder.ScreenType.SELL_SCREEN,
                    1
            ));
        } else if (clickType == ClickType.MIDDLE || clickType == ClickType.SHIFT_RIGHT) {
            handleSellAll(player, economy, item);
        }
    }

    private void handleTransactionClick(Player player, ShopInventoryHolder holder, int slot) {
        ShopItem item = holder.getSelectedItem();
        if (item == null) {
            return;
        }

        if (slot == TX_BACK_SLOT) {
            plugin.getServer().getScheduler().runTask(
                    plugin,
                    () -> openCategory(player, holder.getCategoryId(), holder.getPage())
            );
            return;
        }

        ShopInventoryHolder.ScreenType screenType = holder.getScreenType();
        Integer delta = QUANTITY_DELTAS.get(slot);
        if (delta != null) {
            int updated = calculateAdjustedAmount(holder.getSelectedAmount(), delta, 1, getMaxSelectableAmount(screenType));
            holder.setSelectedAmount(updated);
            if (holder.getInventory() != null) {
                populateTransactionInventory(holder.getInventory(), player, holder);
            }
            return;
        }

        if (slot == TX_MODE_SWITCH_SLOT) {
            if (screenType == ShopInventoryHolder.ScreenType.BUY_SCREEN) {
                plugin.getServer().getScheduler().runTask(plugin, () -> openTransactionScreen(
                        player,
                        holder.getCategoryId(),
                        holder.getPage(),
                        item,
                        ShopInventoryHolder.ScreenType.BUY_STACKS_SCREEN,
                        1
                ));
            } else if (screenType == ShopInventoryHolder.ScreenType.BUY_STACKS_SCREEN) {
                plugin.getServer().getScheduler().runTask(plugin, () -> openTransactionScreen(
                        player,
                        holder.getCategoryId(),
                        holder.getPage(),
                        item,
                        ShopInventoryHolder.ScreenType.BUY_SCREEN,
                        item.defaultStackSize()
                ));
            } else if (screenType == ShopInventoryHolder.ScreenType.SELL_SCREEN) {
                EconomyManager economy = resolveEconomyManager();
                if (economy == null) {
                    player.sendMessage(ChatUtils.getMessage(
                            plugin.getConfig(),
                            "messages.economy_disabled",
                            "&cModuł ekonomii jest aktualnie wyłączony!"
                    ));
                    return;
                }
                handleSellAll(player, economy, item);
                if (holder.getInventory() != null) {
                    populateTransactionInventory(holder.getInventory(), player, holder);
                }
            }
            return;
        }

        if (slot == TX_CONFIRM_SLOT) {
            EconomyManager economy = resolveEconomyManager();
            if (economy == null) {
                player.sendMessage(ChatUtils.getMessage(
                        plugin.getConfig(),
                        "messages.economy_disabled",
                        "&cModuł ekonomii jest aktualnie wyłączony!"
                ));
                return;
            }
            if (screenType == ShopInventoryHolder.ScreenType.SELL_SCREEN) {
                handleSellAmount(player, economy, item, holder.getSelectedAmount());
            } else if (screenType == ShopInventoryHolder.ScreenType.BUY_STACKS_SCREEN) {
                int itemsPerStack = Math.max(1, item.material().getMaxStackSize());
                handleBuy(player, economy, item, holder.getSelectedAmount() * itemsPerStack);
            } else {
                handleBuy(player, economy, item, holder.getSelectedAmount());
            }
            if (holder.getInventory() != null) {
                populateTransactionInventory(holder.getInventory(), player, holder);
            }
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

        giveItemsToPlayer(player, item.material(), amount);
        player.sendMessage(ChatUtils.getMessage(
                        plugin.getConfig(),
                        "messages.shop_bought",
                        "&aKupiono &e%amount%x %item% &aza &e%price%&a!"
                )
                .replace("%amount%", String.valueOf(amount))
                .replace("%item%", ChatUtils.color(item.name()))
                .replace("%price%", EconomyManager.formatMoney(totalPrice)));
    }

    private void handleSellAmount(Player player, EconomyManager economy, ShopItem item, int requestedAmount) {
        if (!item.canSell()) {
            player.sendMessage(ChatUtils.getMessage(
                    plugin.getConfig(),
                    "messages.shop_sell_disabled",
                    "&cSprzedaż tego przedmiotu jest wyłączona!"
            ));
            return;
        }

        PlayerInventory inventory = player.getInventory();
        int ownedCount = countSellableVanillaItems(inventory, item.material());
        if (ownedCount <= 0 || requestedAmount <= 0) {
            player.sendMessage(ChatUtils.getMessage(
                    plugin.getConfig(),
                    "messages.shop_no_items_to_sell",
                    "&cNie posiadasz w ekwipunku tego przedmiotu do sprzedaży!"
            ));
            return;
        }
        if (ownedCount < requestedAmount) {
            player.sendMessage(ChatUtils.getMessage(
                            plugin.getConfig(),
                            "messages.shop_not_enough_items",
                            "&cMasz tylko &e%owned% szt. &c(potrzebujesz &e%required% szt.&c)!"
                    )
                    .replace("%owned%", String.valueOf(ownedCount))
                    .replace("%required%", String.valueOf(requestedAmount)));
            return;
        }

        ItemStack[] contents = inventory.getStorageContents();
        int remainingToTake = requestedAmount;
        for (int i = 0; i < contents.length && remainingToTake > 0; i++) {
            ItemStack stack = contents[i];
            if (!isSellableVanillaItem(stack, item.material())) {
                continue;
            }
            int take = Math.min(remainingToTake, stack.getAmount());
            if (take >= stack.getAmount()) {
                contents[i] = null;
            } else {
                stack.setAmount(stack.getAmount() - take);
            }
            remainingToTake -= take;
        }

        inventory.setStorageContents(contents);
        double earned = EconomyManager.normalizeAmount(item.sellPrice() * requestedAmount);
        if (earned > 0.0) {
            economy.addMoney(player.getUniqueId(), earned);
        }

        player.sendMessage(ChatUtils.getMessage(
                        plugin.getConfig(),
                        "messages.shop_sold",
                        "&aSprzedano &e%amount%x %item% &aza &a%price%&a!"
                )
                .replace("%amount%", String.valueOf(requestedAmount))
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

    private boolean isUiButton(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(uiButtonKey, PersistentDataType.BYTE);
    }

    private static int getMaxSelectableAmount(ShopInventoryHolder.ScreenType screenType) {
        return screenType == ShopInventoryHolder.ScreenType.BUY_STACKS_SCREEN
                ? MAX_STACK_SCREEN_AMOUNT
                : MAX_SINGLE_SCREEN_AMOUNT;
    }

    /**
     * Oblicza nową ilość po kliknięciu przycisku zmiany (-32, -16, -1, +1, +16, +32) w granicach [min, max].
     */
    public static int calculateAdjustedAmount(int current, int delta, int min, int max) {
        long sum = (long) current + delta;
        if (sum < min) {
            return min;
        }
        if (sum > max) {
            return max;
        }
        return (int) sum;
    }

    private static void giveItemsToPlayer(Player player, Material material, int amount) {
        PlayerInventory inventory = player.getInventory();
        int maxStack = Math.max(1, material.getMaxStackSize());
        int remaining = amount;
        while (remaining > 0) {
            int stackSize = Math.min(remaining, maxStack);
            Map<Integer, ItemStack> overflow = inventory.addItem(new ItemStack(material, stackSize));
            for (ItemStack leftover : overflow.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
            remaining -= stackSize;
        }
    }

    private static void returnItemStacksToPlayer(Player player, List<ItemStack> stacks) {
        PlayerInventory inventory = player.getInventory();
        for (ItemStack stack : stacks) {
            if (stack == null || stack.getType() == Material.AIR || stack.getAmount() <= 0) {
                continue;
            }
            Map<Integer, ItemStack> overflow = inventory.addItem(stack);
            for (ItemStack leftover : overflow.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }
    }

    private static int countSellableVanillaItems(PlayerInventory inventory, Material material) {
        int total = 0;
        for (ItemStack stack : inventory.getStorageContents()) {
            if (isSellableVanillaItem(stack, material)) {
                total += stack.getAmount();
            }
        }
        return total;
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
     * Generuje kompletne linie lore przedmiotu w sklepie wraz z instrukcjami LPM / Shift+LPM / PPM / Shift+PPM.
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
            lines.add(format.getOrDefault("buy-enabled", "&aLPM &8- &7Kup (wybór ilości), od &e%buy_1%$")
                    .replace("%buy_1%", buy1));
            lines.add(format.getOrDefault("buy-stack-enabled", "&aShift+LPM &8- &7Szybko kup 64 szt. za &e%buy_64%$")
                    .replace("%buy_64%", buy64));
        } else {
            lines.add(format.getOrDefault("buy-disabled", "&cKupno tego przedmiotu jest wyłączone"));
        }

        if (item.canSell()) {
            String sell1 = EconomyManager.formatAmount(item.sellPrice());
            lines.add(format.getOrDefault("sell-enabled", "&ePPM &8- &7Sprzedaj (wybór ilości), po &a%sell_1%$")
                    .replace("%sell_1%", sell1));
            lines.add(format.getOrDefault("sell-all-enabled", "&6Shift+PPM / ŚPM &8- &7Sprzedaj wszystko z ekwipunku"));
        } else {
            lines.add(format.getOrDefault("sell-disabled", "&cSprzedaż tego przedmiotu jest wyłączona"));
        }

        return lines;
    }

    private ItemStack createDisplayItem(ShopCategory category, ShopItem item) {
        ItemStack stack = new ItemStack(item.material());
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(ChatUtils.itemComponent(item.name()));
            List<Component> loreComponents = buildRawLoreLines(category, item).stream()
                    .map(line -> line.isEmpty() ? Component.empty() : ChatUtils.itemComponent(line))
                    .toList();
            meta.lore(loreComponents);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.getPersistentDataContainer().set(uiButtonKey, PersistentDataType.BYTE, (byte) 1);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private ItemStack createPlayerProfileItem(Player player) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta rawMeta = head.getItemMeta();
        if (rawMeta instanceof SkullMeta skullMeta) {
            skullMeta.setOwningPlayer(player);
            EconomyManager economy = resolveEconomyManager();
            String balanceText = economy != null
                    ? EconomyManager.formatMoney(economy.getMoney(player.getUniqueId()))
                    : "&cModuł wyłączony";
            skullMeta.displayName(ChatUtils.itemComponent("&a&lProfil: &f" + player.getName()));
            skullMeta.lore(List.of(
                    ChatUtils.itemComponent("&7Stan konta: &a" + balanceText)
            ));
            skullMeta.getPersistentDataContainer().set(uiButtonKey, PersistentDataType.BYTE, (byte) 1);
            head.setItemMeta(skullMeta);
        }
        return head;
    }

    private ItemStack createBackButton(String parentShopId) {
        return createUiButton(
                Material.ARROW,
                "&c&lPowrót",
                List.of("&7Kliknij, aby wrócić do: &e" + parentShopId),
                1
        );
    }

    private ItemStack createFillerItem() {
        return createUiButton(Material.GRAY_STAINED_GLASS_PANE, " ", List.of(), 1);
    }

    private ItemStack createUiButton(Material material, String name, List<String> loreLines, int amount) {
        int clampedAmount = Math.max(1, Math.min(amount, material.getMaxStackSize()));
        ItemStack item = new ItemStack(material, clampedAmount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(ChatUtils.itemComponent(name));
            if (loreLines != null && !loreLines.isEmpty()) {
                meta.lore(loreLines.stream()
                        .map(line -> line.isEmpty() ? Component.empty() : ChatUtils.itemComponent(line))
                        .toList());
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.getPersistentDataContainer().set(uiButtonKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static String stripColorCodesOrKeep(String text) {
        return text != null ? text : "";
    }
}
