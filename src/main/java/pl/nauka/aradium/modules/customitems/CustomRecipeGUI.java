package pl.nauka.aradium.modules.customitems;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import pl.nauka.aradium.AradiumPlugin;
import pl.nauka.aradium.modules.shop.ShopManager;
import pl.nauka.aradium.utils.ChatUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Zarządza oknami GUI receptur craftingu:
 * - przeglądarka kategorii i przepisów dla graczy (/receptury)
 * - lista, kreator i edytor receptur dla administratorów (/receptury admin)
 */
public class CustomRecipeGUI {

    public static final int PLAYER_CATEGORIES_SIZE = 45;
    public static final int PLAYER_LIST_SIZE = 54;
    public static final int PLAYER_VIEW_SIZE = 45;
    public static final int ADMIN_LIST_SIZE = 54;
    public static final int ADMIN_EDITOR_SIZE = 54;

    public static final int[] GRID_SLOTS = {10, 11, 12, 19, 20, 21, 28, 29, 30};
    public static final int SLOT_BENCH = 23;
    public static final int SLOT_RESULT = 25;
    public static final int SLOT_PLAYER_VIEW_BACK = 40;

    public static final int SLOT_ADMIN_LIST_NEW = 45;
    public static final int SLOT_ADMIN_LIST_PREV = 47;
    public static final int SLOT_ADMIN_LIST_RELOAD = 49;
    public static final int SLOT_ADMIN_LIST_NEXT = 51;
    public static final int SLOT_ADMIN_LIST_BACK = 53;

    public static final int SLOT_EDITOR_CATEGORY = 15;
    public static final int SLOT_EDITOR_ENABLED = 34;
    public static final int SLOT_EDITOR_SAVE = 48;
    public static final int SLOT_EDITOR_DELETE = 50;
    public static final int SLOT_EDITOR_BACK = 53;

    private static final int[] CATEGORY_CARD_SLOTS = {11, 13, 15, 29, 33};
    private static final List<Integer> LIST_CONTENT_SLOTS = ShopManager.computeAutoSlots(54, 53);

    private final AradiumPlugin plugin;
    private final CustomItemModule module;
    private final CustomRecipeManager recipeManager;
    private final NamespacedKey previewKey;
    private final NamespacedKey recipeIdButtonKey;

    public CustomRecipeGUI(
            AradiumPlugin plugin,
            CustomItemModule module,
            CustomRecipeManager recipeManager
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.module = Objects.requireNonNull(module, "module");
        this.recipeManager = Objects.requireNonNull(recipeManager, "recipeManager");
        this.previewKey = new NamespacedKey(plugin, "recipe_preview_item");
        this.recipeIdButtonKey = new NamespacedKey(plugin, "recipe_gui_id");
    }

    public void openPlayerCategories(Player player) {
        CustomRecipeGuiHolder holder = new CustomRecipeGuiHolder(
                CustomRecipeGuiHolder.ScreenType.PLAYER_CATEGORIES, null, 0, null
        );
        Inventory inv = Bukkit.createInventory(holder, PLAYER_CATEGORIES_SIZE,
                ChatUtils.component("&8» &6&lWłasne Receptury Craftingu"));
        holder.setInventory(inv);
        fillWithFiller(inv, PLAYER_CATEGORIES_SIZE);

        inv.setItem(4, createButton(Material.CRAFTING_TABLE, "&e&lKsięga Receptur Aradium",
                List.of(
                        "&7Wybierz kategorię poniżej, aby sprawdzić",
                        "&7wszystkie własne craftingi na serwerze."
                ), 1));

        CustomRecipeDefinition.RecipeCategory[] categories = CustomRecipeDefinition.RecipeCategory.values();
        for (int i = 0; i < categories.length && i < CATEGORY_CARD_SLOTS.length; i++) {
            CustomRecipeDefinition.RecipeCategory cat = categories[i];
            int count = recipeManager.getRecipesByCategory(cat, true).size();
            inv.setItem(CATEGORY_CARD_SLOTS[i], createButton(
                    cat.getIcon(),
                    cat.getDisplayName(),
                    List.of(
                            cat.getDescription(),
                            "&7Liczba receptur: &e" + count,
                            "",
                            "&aKliknij, aby przeglądać kategorię!"
                    ),
                    Math.max(1, Math.min(64, count))
            ));
        }

        inv.setItem(SLOT_PLAYER_VIEW_BACK, createButton(Material.BARRIER, "&c&lZamknij",
                List.of("&7Kliknij, aby zamknąć menu receptur."), 1));
        player.openInventory(inv);
    }

    public void openPlayerCategoryList(Player player, CustomRecipeDefinition.RecipeCategory category, int page) {
        List<CustomRecipeDefinition> list = recipeManager.getRecipesByCategory(category, true);
        int perPage = LIST_CONTENT_SLOTS.size();
        int totalPages = Math.max(1, (list.size() + perPage - 1) / perPage);
        int safePage = Math.max(0, Math.min(totalPages - 1, page));

        CustomRecipeGuiHolder holder = new CustomRecipeGuiHolder(
                CustomRecipeGuiHolder.ScreenType.PLAYER_CATEGORY_LIST, category, safePage, null
        );
        String title = "&8» " + category.getDisplayName() + " &8(&e" + (safePage + 1) + "&8/&e" + totalPages + "&8)";
        Inventory inv = Bukkit.createInventory(holder, PLAYER_LIST_SIZE, ChatUtils.component(title));
        holder.setInventory(inv);
        fillWithFiller(inv, PLAYER_LIST_SIZE);

        int start = safePage * perPage;
        for (int i = 0; i < perPage && (start + i) < list.size(); i++) {
            CustomRecipeDefinition def = list.get(start + i);
            inv.setItem(LIST_CONTENT_SLOTS.get(i), buildPlayerRecipeCard(def));
        }

        if (safePage > 0) {
            inv.setItem(48, createButton(Material.RED_STAINED_GLASS_PANE, "&d&l« Poprzednia strona",
                    List.of("&7Przejdź do strony &e" + safePage), 1));
        }
        inv.setItem(49, createButton(Material.NETHER_STAR,
                "&e&lStrona &6" + (safePage + 1) + "&8/&6" + totalPages,
                List.of("&7Kliknij dowolny przedmiot, aby zobaczyć crafting."), 1));
        if (safePage + 1 < totalPages) {
            inv.setItem(50, createButton(Material.BLUE_STAINED_GLASS_PANE, "&b&lNastępna strona »",
                    List.of("&7Przejdź do strony &e" + (safePage + 2)), 1));
        }
        inv.setItem(53, createButton(Material.ARROW, "&c&lPowrót do kategorii",
                List.of("&7Wróć do wyboru kategorii receptur."), 1));

        player.openInventory(inv);
    }

    public void openPlayerRecipeView(Player player, String recipeId, CustomRecipeDefinition.RecipeCategory backCategory, int backPage) {
        CustomRecipeDefinition def = recipeManager.getRecipe(recipeId).orElse(null);
        if (def == null) {
            player.sendMessage(ChatUtils.color("&cNie znaleziono receptury o ID: &f" + recipeId + "&c!"));
            return;
        }
        CustomRecipeDefinition.RecipeCategory effectiveCategory = backCategory != null ? backCategory : def.category();
        CustomRecipeGuiHolder holder = new CustomRecipeGuiHolder(
                CustomRecipeGuiHolder.ScreenType.PLAYER_RECIPE_VIEW, effectiveCategory, backPage, def.id()
        );
        Inventory inv = Bukkit.createInventory(holder, PLAYER_VIEW_SIZE,
                ChatUtils.component("&8» &eReceptura: " + def.name()));
        holder.setInventory(inv);
        fillWithFiller(inv, PLAYER_VIEW_SIZE);

        for (int i = 0; i < GRID_SLOTS.length; i++) {
            CustomRecipeDefinition.RecipeSlotIngredient ing = def.matrixSlots().get(i);
            if (ing != null) {
                inv.setItem(GRID_SLOTS[i], recipeManager.resolveIngredientPreviewItem(ing));
            } else {
                inv.setItem(GRID_SLOTS[i], createSlotPlaceholder());
            }
        }

        inv.setItem(SLOT_BENCH, createButton(Material.CRAFTING_TABLE, "&a&lStół Rzemieślniczy 3x3",
                List.of(
                        "&7Ułóż pokazane składniki dokładnie",
                        "&7w takim samym układzie w craftingu.",
                        "&8» &7Kategoria: " + def.category().getDisplayName()
                ), 1));

        ItemStack resultPreview = recipeManager.resolveResultItem(def);
        if (resultPreview != null) {
            inv.setItem(SLOT_RESULT, resultPreview);
        }

        inv.setItem(SLOT_PLAYER_VIEW_BACK, createButton(Material.ARROW, "&c&lPowrót",
                List.of("&7Kliknij, aby wrócić do listy receptur."), 1));
        player.openInventory(inv);
    }

    public void openAdminList(Player player, int page) {
        if (!player.hasPermission("aradium.admin")) {
            player.sendMessage(ChatUtils.getMessage(plugin.getConfig(), "messages.no_permission", "&cNie masz uprawnień do tego!"));
            return;
        }
        List<CustomRecipeDefinition> list = new ArrayList<>(recipeManager.getAllRecipes());
        int perPage = LIST_CONTENT_SLOTS.size();
        int totalPages = Math.max(1, (list.size() + perPage - 1) / perPage);
        int safePage = Math.max(0, Math.min(totalPages - 1, page));

        CustomRecipeGuiHolder holder = new CustomRecipeGuiHolder(
                CustomRecipeGuiHolder.ScreenType.ADMIN_LIST, null, safePage, null
        );
        Inventory inv = Bukkit.createInventory(holder, ADMIN_LIST_SIZE,
                ChatUtils.component("&8» &4&lZarządzanie Recepturami &8(&e" + (safePage + 1) + "&8/&e" + totalPages + "&8)"));
        holder.setInventory(inv);
        fillWithFiller(inv, ADMIN_LIST_SIZE);

        int start = safePage * perPage;
        for (int i = 0; i < perPage && (start + i) < list.size(); i++) {
            inv.setItem(LIST_CONTENT_SLOTS.get(i), buildAdminRecipeCard(list.get(start + i)));
        }

        inv.setItem(SLOT_ADMIN_LIST_NEW, createButton(Material.LIME_CONCRETE, "&a&l+ Nowa receptura",
                List.of("&7Otwiera kreator craftingu 3x3,", "&7aby utworzyć i zapisać nową recepturę."), 1));

        if (safePage > 0) {
            inv.setItem(SLOT_ADMIN_LIST_PREV, createButton(Material.RED_STAINED_GLASS_PANE, "&d&l« Poprzednia strona",
                    List.of("&7Strona &e" + safePage), 1));
        }
        inv.setItem(SLOT_ADMIN_LIST_RELOAD, createButton(Material.CLOCK, "&e&lPrzeładuj recipes.yml",
                List.of(
                        "&7Strona: &e" + (safePage + 1) + "&8/&e" + totalPages,
                        "&7Łącznie receptur: &f" + list.size(),
                        "&aKliknij, aby ponownie wczytać recipes.yml!"
                ), 1));
        if (safePage + 1 < totalPages) {
            inv.setItem(SLOT_ADMIN_LIST_NEXT, createButton(Material.BLUE_STAINED_GLASS_PANE, "&b&lNastępna strona »",
                    List.of("&7Strona &e" + (safePage + 2)), 1));
        }
        inv.setItem(SLOT_ADMIN_LIST_BACK, createButton(Material.CHEST, "&b&lWidok Gracza (/receptury)",
                List.of("&7Przełącz na menu podglądu dla graczy."), 1));

        player.openInventory(inv);
    }

    public void openAdminEditor(Player player, String recipeId) {
        if (!player.hasPermission("aradium.admin")) {
            player.sendMessage(ChatUtils.getMessage(plugin.getConfig(), "messages.no_permission", "&cNie masz uprawnień do tego!"));
            return;
        }
        CustomRecipeDefinition existing = recipeId != null ? recipeManager.getRecipe(recipeId).orElse(null) : null;
        CustomRecipeGuiHolder holder = new CustomRecipeGuiHolder(
                CustomRecipeGuiHolder.ScreenType.ADMIN_EDITOR,
                existing != null ? existing.category() : CustomRecipeDefinition.RecipeCategory.MISC,
                0,
                existing != null ? existing.id() : recipeId
        );
        holder.setCreatingNew(existing == null);
        holder.setEditorCategory(existing != null ? existing.category() : CustomRecipeDefinition.RecipeCategory.MISC);
        holder.setEditorEnabled(existing == null || existing.enabled());

        String title = existing != null
                ? "&8» &cEdytor: &f" + existing.id()
                : "&8» &aNowa Receptura" + (recipeId != null ? " &8(&f" + recipeId + "&8)" : "");
        Inventory inv = Bukkit.createInventory(holder, ADMIN_EDITOR_SIZE, ChatUtils.component(title));
        holder.setInventory(inv);
        fillWithFiller(inv, ADMIN_EDITOR_SIZE);

        for (int i = 0; i < GRID_SLOTS.length; i++) {
            int guiSlot = GRID_SLOTS[i];
            if (existing != null && existing.matrixSlots().containsKey(i)) {
                ItemStack preview = recipeManager.resolveIngredientPreviewItem(existing.matrixSlots().get(i));
                inv.setItem(guiSlot, markAsPreviewTemplate(preview));
            } else {
                inv.setItem(guiSlot, null);
            }
        }

        if (existing != null) {
            ItemStack resultItem = recipeManager.resolveResultItem(existing);
            inv.setItem(SLOT_RESULT, markAsPreviewTemplate(resultItem));
        } else {
            inv.setItem(SLOT_RESULT, null);
        }

        inv.setItem(SLOT_BENCH, createButton(Material.CRAFTING_TABLE, "&e&lKreator Craftingu 3x3",
                List.of(
                        "&7Włóż składniki w pustą siatkę &f3x3 &7po lewej",
                        "&7oraz gotowy wynik w slot &fpo prawej&7.",
                        "&7Obsługuje także własne itemy z PDC/lore!"
                ), 1));

        refreshEditorControlButtons(inv, holder);
        player.openInventory(inv);
    }

    private void refreshEditorControlButtons(Inventory inv, CustomRecipeGuiHolder holder) {
        CustomRecipeDefinition.RecipeCategory cat = holder.getEditorCategory();
        inv.setItem(SLOT_EDITOR_CATEGORY, createButton(cat.getIcon(),
                "&6&lKategoria: " + cat.getDisplayName(),
                List.of(
                        cat.getDescription(),
                        "",
                        "&eKliknij, aby przełączyć kategorię!"
                ), 1));

        boolean enabled = holder.isEditorEnabled();
        inv.setItem(SLOT_EDITOR_ENABLED, createButton(
                enabled ? Material.LIME_DYE : Material.GRAY_DYE,
                enabled ? "&a&lStatus: AKTYWNA" : "&c&lStatus: WYŁĄCZONA",
                List.of("&7Kliknij, aby włączyć lub wyłączyć crafting."), 1));

        inv.setItem(SLOT_EDITOR_SAVE, createButton(Material.EMERALD_BLOCK, "&a&lZapisz recepturę",
                List.of(
                        "&7Zapisze układ w &frecipes.yml &7i zarejestruje crafting.",
                        "&7Twoje włożone itemy wrócą bezpiecznie do ekwipunku!"
                ), 1));

        inv.setItem(SLOT_EDITOR_DELETE, createButton(Material.TNT, "&c&lUsuń recepturę",
                List.of("&7Trwale usuwa tę recepturę z &frecipes.yml&7."), 1));

        inv.setItem(SLOT_EDITOR_BACK, createButton(Material.ARROW, "&c&lPowrót",
                List.of("&7Wróć do listy bez zapisywania zmian."), 1));
    }

    public void handleInventoryClick(InventoryClickEvent event, Player player, CustomRecipeGuiHolder holder) {
        CustomRecipeGuiHolder.ScreenType screen = holder.getScreenType();
        if (screen == CustomRecipeGuiHolder.ScreenType.ADMIN_EDITOR) {
            handleEditorInventoryClick(event, player, holder);
            return;
        }

        event.setCancelled(true);
        if (!module.isEnabled()) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        Inventory clicked = event.getClickedInventory();
        if (clicked == null || !clicked.equals(top)) {
            return;
        }

        int slot = event.getSlot();
        switch (screen) {
            case PLAYER_CATEGORIES -> handlePlayerCategoriesClick(player, slot);
            case PLAYER_CATEGORY_LIST -> handlePlayerListClick(player, holder, slot, top.getItem(slot));
            case PLAYER_RECIPE_VIEW -> {
                if (slot == SLOT_PLAYER_VIEW_BACK) {
                    schedule(() -> openPlayerCategoryList(player, holder.getCategory(), holder.getPage()));
                }
            }
            case ADMIN_LIST -> handleAdminListClick(player, holder, slot, event.getClick(), top.getItem(slot));
            default -> {
            }
        }
    }

    private void handlePlayerCategoriesClick(Player player, int slot) {
        if (slot == SLOT_PLAYER_VIEW_BACK) {
            schedule(player::closeInventory);
            return;
        }
        CustomRecipeDefinition.RecipeCategory[] categories = CustomRecipeDefinition.RecipeCategory.values();
        for (int i = 0; i < categories.length && i < CATEGORY_CARD_SLOTS.length; i++) {
            if (slot == CATEGORY_CARD_SLOTS[i]) {
                CustomRecipeDefinition.RecipeCategory targetCat = categories[i];
                schedule(() -> openPlayerCategoryList(player, targetCat, 0));
                return;
            }
        }
    }

    private void handlePlayerListClick(
            Player player,
            CustomRecipeGuiHolder holder,
            int slot,
            ItemStack clickedItem
    ) {
        if (slot == 48 && holder.getPage() > 0) {
            schedule(() -> openPlayerCategoryList(player, holder.getCategory(), holder.getPage() - 1));
            return;
        }
        if (slot == 50 && clickedItem != null && clickedItem.getType() == Material.BLUE_STAINED_GLASS_PANE) {
            schedule(() -> openPlayerCategoryList(player, holder.getCategory(), holder.getPage() + 1));
            return;
        }
        if (slot == 53) {
            schedule(() -> openPlayerCategories(player));
            return;
        }
        String recipeId = readButtonRecipeId(clickedItem);
        if (recipeId != null) {
            schedule(() -> openPlayerRecipeView(player, recipeId, holder.getCategory(), holder.getPage()));
        }
    }

    private void handleAdminListClick(
            Player player,
            CustomRecipeGuiHolder holder,
            int slot,
            ClickType clickType,
            ItemStack clickedItem
    ) {
        if (!player.hasPermission("aradium.admin")) {
            schedule(player::closeInventory);
            return;
        }
        if (slot == SLOT_ADMIN_LIST_NEW) {
            schedule(() -> openAdminEditor(player, null));
            return;
        }
        if (slot == SLOT_ADMIN_LIST_PREV && holder.getPage() > 0) {
            schedule(() -> openAdminList(player, holder.getPage() - 1));
            return;
        }
        if (slot == SLOT_ADMIN_LIST_RELOAD) {
            recipeManager.reload();
            player.sendMessage(ChatUtils.color("&aPrzeładowano receptury z &frecipes.yml &8(&e"
                    + recipeManager.getAllRecipes().size() + " receptur&8)&a."));
            schedule(() -> openAdminList(player, holder.getPage()));
            return;
        }
        if (slot == SLOT_ADMIN_LIST_NEXT && clickedItem != null && clickedItem.getType() == Material.BLUE_STAINED_GLASS_PANE) {
            schedule(() -> openAdminList(player, holder.getPage() + 1));
            return;
        }
        if (slot == SLOT_ADMIN_LIST_BACK) {
            schedule(() -> openPlayerCategories(player));
            return;
        }

        String recipeId = readButtonRecipeId(clickedItem);
        if (recipeId == null) {
            return;
        }
        if (clickType == ClickType.SHIFT_RIGHT) {
            if (recipeManager.deleteRecipe(recipeId)) {
                player.sendMessage(ChatUtils.color("&eUsunięto recepturę: &f" + recipeId + "&e."));
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.7f, 1.0f);
            }
            schedule(() -> openAdminList(player, holder.getPage()));
        } else if (clickType == ClickType.RIGHT) {
            recipeManager.toggleEnabled(recipeId);
            schedule(() -> openAdminList(player, holder.getPage()));
        } else {
            schedule(() -> openAdminEditor(player, recipeId));
        }
    }

    private void handleEditorInventoryClick(
            InventoryClickEvent event,
            Player player,
            CustomRecipeGuiHolder holder
    ) {
        if (!player.hasPermission("aradium.admin")) {
            event.setCancelled(true);
            schedule(player::closeInventory);
            return;
        }
        if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR
                || event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            event.setCancelled(true);
            return;
        }

        Inventory top = event.getView().getTopInventory();
        Inventory clicked = event.getClickedInventory();
        if (clicked == null || !clicked.equals(top)) {
            return;
        }

        int slot = event.getSlot();
        if (isUnlockedEditorSlot(slot)) {
            ItemStack current = top.getItem(slot);
            if (isPreviewTemplate(current)) {
                ItemStack cursor = event.getView().getCursor();
                if (cursor == null || RecipeDefinition.isAirMaterial(cursor.getType())) {
                    // Usuwamy wirtualny podgląd bez wydawania go graczowi (Anti-Dupe).
                    event.setCancelled(true);
                    top.setItem(slot, null);
                } else {
                    // Podmieniamy wirtualny podgląd na prawdziwy przedmiot gracza.
                    top.setItem(slot, null);
                }
            }
            return;
        }

        event.setCancelled(true);
        if (slot == SLOT_EDITOR_CATEGORY) {
            holder.setEditorCategory(holder.getEditorCategory().next());
            refreshEditorControlButtons(top, holder);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
        } else if (slot == SLOT_EDITOR_ENABLED) {
            holder.setEditorEnabled(!holder.isEditorEnabled());
            refreshEditorControlButtons(top, holder);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.0f);
        } else if (slot == SLOT_EDITOR_SAVE) {
            saveRecipeFromEditor(player, holder, top);
        } else if (slot == SLOT_EDITOR_DELETE) {
            if (!holder.isCreatingNew() && holder.getRecipeId() != null) {
                returnNonPreviewItems(player, holder, top);
                recipeManager.deleteRecipe(holder.getRecipeId());
                player.sendMessage(ChatUtils.color("&eUsunięto recepturę &f" + holder.getRecipeId() + "&e."));
            } else {
                returnNonPreviewItems(player, holder, top);
            }
            schedule(() -> openAdminList(player, 0));
        } else if (slot == SLOT_EDITOR_BACK) {
            returnNonPreviewItems(player, holder, top);
            schedule(() -> openAdminList(player, 0));
        }
    }

    public void handleInventoryDrag(InventoryDragEvent event, CustomRecipeGuiHolder holder) {
        if (holder.getScreenType() != CustomRecipeGuiHolder.ScreenType.ADMIN_EDITOR) {
            event.setCancelled(true);
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < topSize && !isUnlockedEditorSlot(rawSlot)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    public void handleEditorClose(Player player, CustomRecipeGuiHolder holder, Inventory top) {
        if (holder.getScreenType() == CustomRecipeGuiHolder.ScreenType.ADMIN_EDITOR) {
            returnNonPreviewItems(player, holder, top);
        }
    }

    private void saveRecipeFromEditor(Player player, CustomRecipeGuiHolder holder, Inventory top) {
        Map<Integer, CustomRecipeDefinition.RecipeSlotIngredient> matrixSlots = new LinkedHashMap<>();
        for (int i = 0; i < GRID_SLOTS.length; i++) {
            ItemStack raw = top.getItem(GRID_SLOTS[i]);
            if (raw == null || RecipeDefinition.isAirMaterial(raw.getType())) {
                continue;
            }
            ItemStack clean = stripPreviewMarker(raw.clone());
            clean.setAmount(1);
            String customId = module.getCustomItemId(clean);
            ItemStack exactCopy = (clean.hasItemMeta() && customId == null) ? clean : null;
            matrixSlots.put(i, new CustomRecipeDefinition.RecipeSlotIngredient(clean.getType(), exactCopy, customId));
        }

        ItemStack rawResult = top.getItem(SLOT_RESULT);
        if (matrixSlots.isEmpty() || rawResult == null || RecipeDefinition.isAirMaterial(rawResult.getType())) {
            player.sendMessage(ChatUtils.color("&cMusisz umieścić przynajmniej &f1 składnik &cw siatce 3x3 oraz &fwynik &cpo prawej!"));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
            return;
        }

        ItemStack cleanResult = stripPreviewMarker(rawResult.clone());
        String customResultId = module.getCustomItemId(cleanResult);
        String recipeId = holder.getRecipeId() != null && !holder.getRecipeId().isBlank()
                ? holder.getRecipeId().toLowerCase(java.util.Locale.ROOT)
                : recipeManager.nextAutoRecipeId(cleanResult.getType());

        CustomRecipeDefinition existing = recipeManager.getRecipe(recipeId).orElse(null);
        String resolvedName = existing != null
                ? existing.name()
                : resolveDisplayName(cleanResult);

        CustomRecipeDefinition.RecipeResultSpec resultSpec;
        if (customResultId != null) {
            resultSpec = new CustomRecipeDefinition.RecipeResultSpec(
                    customResultId, null, null, 1, cleanResult.getType(), cleanResult.getAmount(), resolvedName, null
            );
        } else if (existing != null && existing.resultSpec().specialArmorId() != null
                && cleanResult.getType() == recipeManager.resolveResultIconMaterial(existing)) {
            resultSpec = new CustomRecipeDefinition.RecipeResultSpec(
                    null, existing.resultSpec().specialArmorId(), null, 1,
                    cleanResult.getType(), cleanResult.getAmount(), resolvedName, null
            );
        } else if (existing != null && existing.resultSpec().magicItemId() != null
                && cleanResult.getType() == recipeManager.resolveResultIconMaterial(existing)) {
            resultSpec = new CustomRecipeDefinition.RecipeResultSpec(
                    null, null, existing.resultSpec().magicItemId(), existing.resultSpec().magicItemLevel(),
                    cleanResult.getType(), cleanResult.getAmount(), resolvedName, null
            );
        } else {
            resultSpec = new CustomRecipeDefinition.RecipeResultSpec(
                    null, null, null, 1,
                    cleanResult.getType(), cleanResult.getAmount(), resolvedName,
                    cleanResult.hasItemMeta() ? cleanResult : null
            );
        }

        CustomRecipeDefinition updated = new CustomRecipeDefinition(
                recipeId,
                resolvedName,
                holder.getEditorCategory(),
                holder.isEditorEnabled(),
                matrixSlots,
                resultSpec
        );

        returnNonPreviewItems(player, holder, top);
        recipeManager.saveRecipe(updated, true);

        player.sendMessage(ChatUtils.color("&aZapisano recepturę &e" + updated.id() + " &aw pliku &frecipes.yml&a!"));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.3f);
        schedule(() -> openAdminList(player, 0));
    }

    private void returnNonPreviewItems(Player player, CustomRecipeGuiHolder holder, Inventory top) {
        if (holder.isItemsReturned() || top == null) {
            return;
        }
        holder.markItemsReturned();

        List<ItemStack> toReturn = new ArrayList<>();
        for (int slot : GRID_SLOTS) {
            collectAndClearIfReal(top, slot, toReturn);
        }
        collectAndClearIfReal(top, SLOT_RESULT, toReturn);

        for (ItemStack item : toReturn) {
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
            for (ItemStack leftover : overflow.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }
    }

    private void collectAndClearIfReal(Inventory top, int slot, List<ItemStack> targetList) {
        ItemStack stack = top.getItem(slot);
        top.setItem(slot, null);
        if (stack != null && !RecipeDefinition.isAirMaterial(stack.getType()) && !isPreviewTemplate(stack)) {
            targetList.add(stack);
        }
    }

    private ItemStack buildPlayerRecipeCard(CustomRecipeDefinition def) {
        ItemStack base = recipeManager.resolveResultItem(def);
        if (base == null) {
            base = new ItemStack(recipeManager.resolveResultIconMaterial(def), 1);
        }
        ItemMeta meta = base.getItemMeta();
        if (meta != null) {
            if (!meta.hasDisplayName()) {
                meta.displayName(ChatUtils.itemComponent(def.name()));
            }
            List<Component> lore = meta.hasLore() && meta.lore() != null
                    ? new ArrayList<>(meta.lore())
                    : new ArrayList<>();
            lore.add(Component.empty());
            lore.add(ChatUtils.itemComponent("&7Kategoria: " + def.category().getDisplayName()));
            lore.add(ChatUtils.itemComponent("&aKliknij, aby zobaczyć recepturę craftingu!"));
            meta.lore(lore);
            meta.getPersistentDataContainer().set(recipeIdButtonKey, PersistentDataType.STRING, def.id());
            base.setItemMeta(meta);
        }
        return base;
    }

    private ItemStack buildAdminRecipeCard(CustomRecipeDefinition def) {
        ItemStack base = recipeManager.resolveResultItem(def);
        if (base == null) {
            base = new ItemStack(recipeManager.resolveResultIconMaterial(def), 1);
        }
        ItemMeta meta = base.getItemMeta();
        if (meta != null) {
            if (!meta.hasDisplayName()) {
                meta.displayName(ChatUtils.itemComponent(def.name()));
            }
            List<Component> lore = new ArrayList<>();
            lore.add(ChatUtils.itemComponent("&8ID: &7" + def.id()));
            lore.add(ChatUtils.itemComponent("&7Kategoria: " + def.category().getDisplayName()));
            lore.add(ChatUtils.itemComponent("&7Status: " + (def.enabled() ? "&a✔ AKTYWNA" : "&c✖ WYŁĄCZONA")));
            lore.add(Component.empty());
            lore.add(ChatUtils.itemComponent("&eLPM &8- &7Edytuj recepturę w kreatorze"));
            lore.add(ChatUtils.itemComponent("&bPPM &8- &7Włącz / Wyłącz"));
            lore.add(ChatUtils.itemComponent("&cShift+PPM &8- &7Usuń na stałe"));
            meta.lore(lore);
            meta.getPersistentDataContainer().set(recipeIdButtonKey, PersistentDataType.STRING, def.id());
            base.setItemMeta(meta);
        }
        return base;
    }

    private String readButtonRecipeId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(recipeIdButtonKey, PersistentDataType.STRING);
    }

    private ItemStack markAsPreviewTemplate(ItemStack item) {
        if (item == null || RecipeDefinition.isAirMaterial(item.getType())) {
            return null;
        }
        ItemStack clone = item.clone();
        ItemMeta meta = clone.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(previewKey, PersistentDataType.BYTE, (byte) 1);
            clone.setItemMeta(meta);
        }
        return clone;
    }

    private boolean isPreviewTemplate(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(previewKey, PersistentDataType.BYTE);
    }

    private ItemStack stripPreviewMarker(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return item;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.getPersistentDataContainer().has(previewKey, PersistentDataType.BYTE)) {
            meta.getPersistentDataContainer().remove(previewKey);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static boolean isUnlockedEditorSlot(int slot) {
        if (slot == SLOT_RESULT) {
            return true;
        }
        for (int gridSlot : GRID_SLOTS) {
            if (gridSlot == slot) {
                return true;
            }
        }
        return false;
    }

    private static String resolveDisplayName(ItemStack item) {
        if (item != null && item.hasItemMeta() && item.getItemMeta().hasDisplayName() && item.getItemMeta().displayName() != null) {
            String plain = PlainTextComponentSerializer.plainText().serialize(item.getItemMeta().displayName());
            if (!plain.isBlank()) {
                return "&f" + plain;
            }
        }
        return item != null ? GeneratorDefinition.formatFallbackMaterialName(item.getType()) : "&fReceptura";
    }

    private void fillWithFiller(Inventory inv, int size) {
        ItemStack filler = createButton(Material.GRAY_STAINED_GLASS_PANE, " ", List.of(), 1);
        for (int i = 0; i < size; i++) {
            inv.setItem(i, filler);
        }
    }

    private ItemStack createSlotPlaceholder() {
        return createButton(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "&8Puste pole", List.of(), 1);
    }

    private ItemStack createButton(Material material, String name, List<String> loreLines, int amount) {
        ItemStack stack = new ItemStack(material, Math.max(1, Math.min(amount, material.getMaxStackSize())));
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(ChatUtils.itemComponent(name));
            if (loreLines != null && !loreLines.isEmpty()) {
                meta.lore(loreLines.stream()
                        .map(line -> line == null || line.isEmpty() ? Component.empty() : ChatUtils.itemComponent(line))
                        .toList());
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private void schedule(Runnable task) {
        plugin.getServer().getScheduler().runTask(plugin, task);
    }
}
