package pl.nauka.aradium.modules.spawners;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import pl.nauka.aradium.AradiumPlugin;
import pl.nauka.aradium.core.PluginModule;
import pl.nauka.aradium.modules.economy.EconomyManager;
import pl.nauka.aradium.modules.economy.EconomyModule;
import pl.nauka.aradium.modules.shop.ShopCategory;
import pl.nauka.aradium.modules.shop.ShopGUI;
import pl.nauka.aradium.modules.shop.ShopManager;
import pl.nauka.aradium.utils.ChatUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * GUI panelu ulepszeń spawnera (4 niezależne ścieżki + sklep mobów) otwierane przez Shift + PPM.
 */
public class SpawnerGUI {

    public static final int SLOT_INFO = 11;
    public static final int SLOT_UPGRADE = 15;

    public static final int MAIN_SLOT_INFO = 4;
    public static final int MAIN_SLOT_DELAY = 19;
    public static final int MAIN_SLOT_AMOUNT = 20;
    public static final int MAIN_SLOT_RANGE = 21;
    public static final int MAIN_SLOT_LIGHT = 22;
    public static final int MAIN_SLOT_MOB_SHOP = 25;
    public static final int MAIN_SLOT_CLOSE = 31;

    public static final int MOB_SHOP_SIZE = 54;
    public static final int MOB_SHOP_SLOT_HEAD = 45;
    public static final int MOB_SHOP_SLOT_PREV = 48;
    public static final int MOB_SHOP_SLOT_PAGE = 49;
    public static final int MOB_SHOP_SLOT_NEXT = 50;
    public static final int MOB_SHOP_SLOT_BACK = 53;

    private final AradiumPlugin plugin;
    private final SpawnerModule module;

    public SpawnerGUI(AradiumPlugin plugin, SpawnerModule module) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.module = Objects.requireNonNull(module, "module");
    }

    public void openSpawnerGUI(Player player, CreatureSpawner spawner) {
        if (player == null || spawner == null) {
            return;
        }
        Inventory gui = createInventory(spawner);
        player.openInventory(gui);
    }

    public void openMobShopGUI(Player player, CreatureSpawner spawner, int page) {
        if (player == null || spawner == null) {
            return;
        }
        Inventory gui = createMobShopInventory(spawner, page, player);
        player.openInventory(gui);
    }

    public Inventory createInventory(CreatureSpawner spawner) {
        SpawnerDataManager dataManager = module.getDataManager();
        SpawnerState state = dataManager.getSpawnerState(spawner);
        String entityDisplay = module.formatMobDisplayName(state.activeEntity());

        SpawnerUpgradeHolder holder = SpawnerUpgradeHolder.forBlock(spawner.getBlock());
        int size = Math.max(36, ShopCategory.normalizeInventorySize(module.getGuiSize()));
        String titleText = module.getGuiTitle()
                .replace("%tier%", String.valueOf(state.legacyOverallTier()))
                .replace("%entity%", entityDisplay);

        Inventory inventory = Bukkit.createInventory(holder, size, ChatUtils.component(titleText));
        holder.setInventory(inventory);

        ItemStack filler = createFillerItem();
        for (int i = 0; i < size; i++) {
            inventory.setItem(i, filler);
        }

        inventory.setItem(MAIN_SLOT_INFO, createOverviewItem(spawner, state));
        for (SpawnerUpgradeTrack track : SpawnerUpgradeTrack.values()) {
            inventory.setItem(track.guiSlot(), createTrackUpgradeItem(track, state));
        }
        inventory.setItem(MAIN_SLOT_MOB_SHOP, createMobShopButtonItem(state));
        inventory.setItem(MAIN_SLOT_CLOSE, createSimpleButton(
                Material.BARRIER,
                "&c&lZamknij Panel",
                List.of("&7Kliknij, aby zamknąć menu spawnera.")
        ));
        return inventory;
    }

    public Inventory createMobShopInventory(CreatureSpawner spawner, int requestedPage, Player viewer) {
        SpawnerDataManager dataManager = module.getDataManager();
        SpawnerState state = dataManager.getSpawnerState(spawner);

        List<SpawnerMobDefinition> allMobs = new ArrayList<>(module.getMobDefinitions());
        List<Integer> gridSlots = ShopManager.computeAutoSlots(MOB_SHOP_SIZE, MOB_SHOP_SLOT_BACK);
        int perPage = Math.max(1, gridSlots.size());
        int totalPages = Math.max(1, (int) Math.ceil(allMobs.size() / (double) perPage));
        int safePage = Math.max(0, Math.min(requestedPage, totalPages - 1));

        int startIdx = safePage * perPage;
        int endIdx = Math.min(startIdx + perPage, allMobs.size());

        Map<Integer, EntityType> slotMobs = new LinkedHashMap<>();
        for (int i = startIdx; i < endIdx; i++) {
            int slot = gridSlots.get(i - startIdx);
            slotMobs.put(slot, allMobs.get(i).entityType());
        }

        SpawnerUpgradeHolder holder = SpawnerUpgradeHolder.forBlock(
                spawner.getBlock(),
                SpawnerUpgradeHolder.ScreenType.MOB_SHOP,
                safePage,
                slotMobs
        );

        String titleText = module.getMobGuiTitle()
                .replace("%page%", String.valueOf(safePage + 1))
                .replace("%pages%", String.valueOf(totalPages))
                .replace("%entity%", module.formatMobDisplayName(state.activeEntity()));

        Inventory inventory = Bukkit.createInventory(holder, MOB_SHOP_SIZE, ChatUtils.component(titleText));
        holder.setInventory(inventory);

        ItemStack filler = createFillerItem();
        for (int i = 0; i < MOB_SHOP_SIZE; i++) {
            inventory.setItem(i, filler);
        }

        for (int i = startIdx; i < endIdx; i++) {
            int slot = gridSlots.get(i - startIdx);
            SpawnerMobDefinition mobDef = allMobs.get(i);
            inventory.setItem(slot, createMobSelectionItem(mobDef, state));
        }

        if (viewer != null) {
            inventory.setItem(MOB_SHOP_SLOT_HEAD, createPlayerProfileItem(viewer));
        }
        if (safePage > 0) {
            inventory.setItem(MOB_SHOP_SLOT_PREV, createSimpleButton(
                    Material.RED_STAINED_GLASS_PANE,
                    "&d&l« Poprzednia strona",
                    List.of("&7Kliknij, aby przejść na stronę &e" + safePage + "&7.")
            ));
        }
        inventory.setItem(MOB_SHOP_SLOT_PAGE, createSimpleButton(
                Material.NETHER_STAR,
                "&e&lStrona &6" + (safePage + 1) + "&8/&6" + totalPages,
                List.of(
                        "&8» &7Odblokowane moby: &e" + state.unlockedEntities().size() + "&8/&7" + allMobs.size(),
                        "&8» &7Aktywny mob: &a" + module.formatMobDisplayName(state.activeEntity()),
                        "",
                        "&7Kliknij jajo moba, aby go odblokować lub ustawić."
                )
        ));
        if (safePage + 1 < totalPages) {
            inventory.setItem(MOB_SHOP_SLOT_NEXT, createSimpleButton(
                    Material.BLUE_STAINED_GLASS_PANE,
                    "&b&lNastępna strona »",
                    List.of("&7Kliknij, aby przejść na stronę &e" + (safePage + 2) + "&7.")
            ));
        }
        inventory.setItem(MOB_SHOP_SLOT_BACK, createSimpleButton(
                Material.ARROW,
                "&c&lPowrót do Panelu",
                List.of("&7Kliknij, aby wrócić do ulepszeń spawnera.")
        ));

        return inventory;
    }

    public void handleInventoryClick(Player player, SpawnerUpgradeHolder holder, int rawSlot) {
        if (player == null || holder == null) {
            return;
        }
        if (holder.getScreenType() == SpawnerUpgradeHolder.ScreenType.MOB_SHOP) {
            handleMobShopClick(player, holder, rawSlot);
            return;
        }
        handleMainMenuClick(player, holder, rawSlot);
    }

    private void handleMainMenuClick(Player player, SpawnerUpgradeHolder holder, int rawSlot) {
        if (rawSlot == MAIN_SLOT_CLOSE) {
            plugin.getServer().getScheduler().runTask(plugin, () -> player.closeInventory());
            return;
        }

        if (rawSlot == MAIN_SLOT_MOB_SHOP) {
            CreatureSpawner spawner = holder.resolveSpawner();
            if (spawner == null) {
                notifySpawnerMissing(player);
                return;
            }
            plugin.getServer().getScheduler().runTask(plugin, () -> openMobShopGUI(player, spawner, 0));
            return;
        }

        for (SpawnerUpgradeTrack track : SpawnerUpgradeTrack.values()) {
            if (track.guiSlot() == rawSlot) {
                handleTrackUpgradeClick(player, holder, track);
                return;
            }
        }
    }

    public void handleTrackUpgradeClick(Player player, SpawnerUpgradeHolder holder, SpawnerUpgradeTrack track) {
        CreatureSpawner spawner = holder.resolveSpawner();
        if (spawner == null) {
            notifySpawnerMissing(player);
            return;
        }

        SpawnerDataManager dataManager = module.getDataManager();
        SpawnerState currentState = dataManager.getSpawnerState(spawner);
        int currentLevel = currentState.getLevel(track);
        SpawnerTrackLevelDefinition nextLevel = module.getTrackLevelDefinition(track, currentLevel + 1);

        if (nextLevel == null) {
            player.sendMessage(ChatUtils.getMessage(
                    plugin.getConfig(),
                    "messages.spawner_max_tier",
                    "&eTa ścieżka ulepszenia osiągnęła już maksymalny poziom!"
            ));
            refreshViewersForBlock(holder.getWorldName(), holder.getBlockX(), holder.getBlockY(), holder.getBlockZ(), spawner);
            return;
        }

        double cost = Math.max(0.0, EconomyManager.normalizeAmount(nextLevel.upgradeCost()));
        EconomyManager economy = resolveEconomyManager();
        if (cost > 0.0) {
            if (economy == null) {
                player.sendMessage(ChatUtils.getMessage(
                        plugin.getConfig(),
                        "messages.economy_disabled",
                        "&cModuł ekonomii jest aktualnie wyłączony!"
                ));
                return;
            }
            if (!economy.hasMoney(player.getUniqueId(), cost)) {
                player.sendMessage(ChatUtils.getMessage(
                                plugin.getConfig(),
                                "messages.spawner_insufficient_funds",
                                "&cNie stać Cię na ulepszenie! Wymagany koszt: &e%price%&c."
                        )
                        .replace("%price%", EconomyManager.formatMoney(cost)));
                return;
            }
        }

        PlayerInventory inventory = player.getInventory();
        Map<Material, Integer> missingItems = computeMissingItems(inventory, nextLevel.requiredItems());
        if (!missingItems.isEmpty()) {
            player.sendMessage(ChatUtils.getMessage(
                            plugin.getConfig(),
                            "messages.spawner_missing_items",
                            "&cBrakuje Ci wymaganych przedmiotów do ulepszenia: &f%items%&c!"
                    )
                    .replace("%items%", formatRequiredItemsSummary(missingItems)));
            return;
        }

        if (cost > 0.0 && !economy.takeMoney(player.getUniqueId(), cost)) {
            player.sendMessage(ChatUtils.getMessage(
                            plugin.getConfig(),
                            "messages.spawner_insufficient_funds",
                            "&cNie stać Cię na ulepszenie! Wymagany koszt: &e%price%&c."
                    )
                    .replace("%price%", EconomyManager.formatMoney(cost)));
            return;
        }

        if (!nextLevel.requiredItems().isEmpty()) {
            removeRequiredItems(inventory, nextLevel.requiredItems());
        }

        SpawnerState updatedState = currentState.withTrackLevel(track, nextLevel.level());
        dataManager.applyStateToBlock(spawner, updatedState, module);

        player.sendMessage(ChatUtils.getMessage(
                        plugin.getConfig(),
                        "messages.spawner_track_upgraded",
                        "&aUlepszono ścieżkę &e%track% &aspawnera do poziomu &6%level%&a!"
                )
                .replace("%track%", plainText(track.displayName()))
                .replace("%level%", String.valueOf(nextLevel.level()))
                .replace("%entity%", module.formatMobDisplayName(updatedState.activeEntity())));

        refreshViewersForBlock(holder.getWorldName(), holder.getBlockX(), holder.getBlockY(), holder.getBlockZ(), spawner);
    }

    public void handleMobShopClick(Player player, SpawnerUpgradeHolder holder, int rawSlot) {
        CreatureSpawner spawner = holder.resolveSpawner();
        if (spawner == null) {
            notifySpawnerMissing(player);
            return;
        }

        int currentPage = holder.getPage();
        if (rawSlot == MOB_SHOP_SLOT_BACK) {
            plugin.getServer().getScheduler().runTask(plugin, () -> openSpawnerGUI(player, spawner));
            return;
        }
        if (rawSlot == MOB_SHOP_SLOT_PREV && currentPage > 0) {
            plugin.getServer().getScheduler().runTask(plugin, () -> openMobShopGUI(player, spawner, currentPage - 1));
            return;
        }
        if (rawSlot == MOB_SHOP_SLOT_NEXT) {
            int perPage = Math.max(1, ShopManager.computeAutoSlots(MOB_SHOP_SIZE, MOB_SHOP_SLOT_BACK).size());
            int totalPages = Math.max(1, (int) Math.ceil(module.getMobDefinitions().size() / (double) perPage));
            if (currentPage + 1 < totalPages) {
                plugin.getServer().getScheduler().runTask(plugin, () -> openMobShopGUI(player, spawner, currentPage + 1));
            }
            return;
        }

        EntityType clickedMob = holder.getMobAtSlot(rawSlot);
        if (clickedMob == null || !module.isAllowedEntity(clickedMob)) {
            return;
        }

        SpawnerMobDefinition mobDef = module.getMobDefinition(clickedMob);
        if (mobDef == null) {
            return;
        }

        SpawnerDataManager dataManager = module.getDataManager();
        SpawnerState currentState = dataManager.getSpawnerState(spawner);

        if (currentState.activeEntity() == clickedMob) {
            player.sendMessage(ChatUtils.getMessage(
                    plugin.getConfig(),
                    "messages.spawner_mob_already_active",
                    "&eTen mob jest już aktualnie ustawiony na tym spawnerze!"
            ));
            return;
        }

        if (currentState.isUnlocked(clickedMob)) {
            SpawnerState updated = currentState.withActiveEntity(clickedMob);
            dataManager.applyStateToBlock(spawner, updated, module);
            player.sendMessage(ChatUtils.getMessage(
                            plugin.getConfig(),
                            "messages.spawner_mob_switched",
                            "&aZmieniono aktywnego moba spawnera na: &e%entity%&a!"
                    )
                    .replace("%entity%", plainText(mobDef.displayName())));
            refreshViewersForBlock(holder.getWorldName(), holder.getBlockX(), holder.getBlockY(), holder.getBlockZ(), spawner);
            return;
        }

        double cost = Math.max(0.0, EconomyManager.normalizeAmount(mobDef.unlockCost()));
        EconomyManager economy = resolveEconomyManager();
        if (cost > 0.0) {
            if (economy == null) {
                player.sendMessage(ChatUtils.getMessage(
                        plugin.getConfig(),
                        "messages.economy_disabled",
                        "&cModuł ekonomii jest aktualnie wyłączony!"
                ));
                return;
            }
            if (!economy.hasMoney(player.getUniqueId(), cost)) {
                player.sendMessage(ChatUtils.getMessage(
                                plugin.getConfig(),
                                "messages.spawner_insufficient_funds",
                                "&cNie stać Cię na odblokowanie tego moba! Koszt: &e%price%&c."
                        )
                        .replace("%price%", EconomyManager.formatMoney(cost)));
                return;
            }
            if (!economy.takeMoney(player.getUniqueId(), cost)) {
                player.sendMessage(ChatUtils.getMessage(
                                plugin.getConfig(),
                                "messages.spawner_insufficient_funds",
                                "&cNie stać Cię na odblokowanie tego moba! Koszt: &e%price%&c."
                        )
                        .replace("%price%", EconomyManager.formatMoney(cost)));
                return;
            }
        }

        SpawnerState updated = currentState.withActiveEntity(clickedMob);
        dataManager.applyStateToBlock(spawner, updated, module);

        player.sendMessage(ChatUtils.getMessage(
                        plugin.getConfig(),
                        "messages.spawner_mob_unlocked",
                        "&aOdblokowano i ustawiono moba &e%entity% &ana spawnerze za &e%price%&a!"
                )
                .replace("%entity%", plainText(mobDef.displayName()))
                .replace("%price%", EconomyManager.formatMoney(cost)));

        refreshViewersForBlock(holder.getWorldName(), holder.getBlockX(), holder.getBlockY(), holder.getBlockZ(), spawner);
    }

    private void notifySpawnerMissing(Player player) {
        plugin.getServer().getScheduler().runTask(plugin, () -> player.closeInventory());
        player.sendMessage(ChatUtils.getMessage(
                plugin.getConfig(),
                "messages.spawner_not_found",
                "&cTen spawner już nie istnieje!"
        ));
    }

    public void closeViewersForBlock(Block block) {
        if (block == null) {
            return;
        }
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getOpenInventory().getTopInventory().getHolder() instanceof SpawnerUpgradeHolder holder
                    && holder.matchesBlock(block)) {
                online.closeInventory();
            }
        }
    }

    private void refreshViewersForBlock(String worldName, int x, int y, int z, CreatureSpawner updatedSpawner) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (updatedSpawner.getBlock().getType() != Material.SPAWNER) {
                closeViewersForBlock(updatedSpawner.getBlock());
                return;
            }
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getOpenInventory().getTopInventory().getHolder() instanceof SpawnerUpgradeHolder holder
                        && holder.matchesLocation(worldName, x, y, z)) {
                    if (holder.getScreenType() == SpawnerUpgradeHolder.ScreenType.MOB_SHOP) {
                        openMobShopGUI(online, updatedSpawner, holder.getPage());
                    } else {
                        openSpawnerGUI(online, updatedSpawner);
                    }
                }
            }
        });
    }

    private EconomyManager resolveEconomyManager() {
        return plugin.getEconomyModule()
                .filter(PluginModule::isEnabled)
                .map(EconomyModule::getEconomyManager)
                .orElse(null);
    }

    private static Map<Material, Integer> computeMissingItems(PlayerInventory inventory, Map<Material, Integer> requiredItems) {
        if (requiredItems == null || requiredItems.isEmpty()) {
            return Map.of();
        }
        Map<Material, Integer> counts = new LinkedHashMap<>();
        for (ItemStack stack : inventory.getStorageContents()) {
            if (stack == null || stack.getType() == Material.AIR) {
                continue;
            }
            Material mat = stack.getType();
            if (!requiredItems.containsKey(mat) || !ShopGUI.isSellableVanillaItem(stack, mat)) {
                continue;
            }
            counts.merge(mat, stack.getAmount(), Integer::sum);
        }

        Map<Material, Integer> missing = new LinkedHashMap<>();
        for (Map.Entry<Material, Integer> req : requiredItems.entrySet()) {
            int needed = Math.max(0, req.getValue());
            int owned = counts.getOrDefault(req.getKey(), 0);
            if (owned < needed) {
                missing.put(req.getKey(), needed - owned);
            }
        }
        return missing;
    }

    private static void removeRequiredItems(PlayerInventory inventory, Map<Material, Integer> requiredItems) {
        ItemStack[] contents = inventory.getStorageContents();
        Map<Material, Integer> remaining = new LinkedHashMap<>(requiredItems);

        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (stack == null || stack.getType() == Material.AIR) {
                continue;
            }
            Material mat = stack.getType();
            Integer needed = remaining.get(mat);
            if (needed == null || needed <= 0 || !ShopGUI.isSellableVanillaItem(stack, mat)) {
                continue;
            }

            if (stack.getAmount() <= needed) {
                remaining.put(mat, needed - stack.getAmount());
                contents[i] = null;
            } else {
                stack.setAmount(stack.getAmount() - needed);
                remaining.put(mat, 0);
            }
        }
        inventory.setStorageContents(contents);
    }

    public static String formatRequiredItemsSummary(Map<Material, Integer> items) {
        if (items == null || items.isEmpty()) {
            return "Brak";
        }
        List<String> parts = new ArrayList<>(items.size());
        for (Map.Entry<Material, Integer> entry : items.entrySet()) {
            parts.add(entry.getValue() + "x " + formatMaterialName(entry.getKey()));
        }
        return String.join(", ", parts);
    }

    public static List<String> buildUpgradeLore(SpawnerTierDefinition currentTier, SpawnerTierDefinition nextTier) {
        List<String> lore = new ArrayList<>();
        if (nextTier == null) {
            int level = currentTier != null ? currentTier.tier() : 1;
            lore.add("&8» &7Aktualny poziom: &6Tier " + level);
            lore.add("");
            lore.add("&aOsiągnięto maksymalny poziom spawnera!");
            return lore;
        }

        lore.add("&8» &7Nowy poziom: &aTier " + nextTier.tier());
        lore.add("&8» &7Opóźnienie spawnu: &f" + formatDelayRangeSeconds(nextTier.minSpawnDelay(), nextTier.maxSpawnDelay()));
        lore.add("&8» &7Ilość mobów: &f" + nextTier.spawnCount());
        lore.add("&8» &7Limit w pobliżu: &f" + nextTier.maxNearbyEntities());
        lore.add("&8» &7Zasięg gracza: &f" + nextTier.requiredPlayerRange() + " kratek");
        lore.add("");
        lore.add("&8» &7Koszt waluty: &e" + EconomyManager.formatMoney(nextTier.upgradeCost()));

        if (!nextTier.requiredItems().isEmpty()) {
            lore.add("&8» &7Wymagane przedmioty:");
            for (Map.Entry<Material, Integer> req : nextTier.requiredItems().entrySet()) {
                lore.add("  &8- &e" + req.getValue() + "x &f" + formatMaterialName(req.getKey()));
            }
        }

        lore.add("");
        lore.add("&aKliknij, aby ulepszyć spawner!");
        return lore;
    }

    public static List<String> buildTrackUpgradeLore(
            SpawnerUpgradeTrack track,
            SpawnerTrackLevelDefinition currentDef,
            SpawnerTrackLevelDefinition nextDef,
            int maxLevel
    ) {
        List<String> lore = new ArrayList<>();
        int currentLevel = currentDef != null ? currentDef.level() : track.defaultLevel();
        String statusText = track == SpawnerUpgradeTrack.LIGHT_IMMUNITY
                ? (currentLevel >= 1 ? "&aOdblokowane (0-15)" : "&cWyłączone")
                : "&ePoziom " + currentLevel + "&8/&7" + maxLevel;

        lore.add("&8» &7Obecnie: " + statusText);
        if (currentDef != null) {
            lore.add("&8» &7Aktualny efekt: &f" + describeTrackValue(track, currentDef));
        }

        if (nextDef == null) {
            lore.add("");
            lore.add("&aOsiągnięto maksymalny poziom tej ścieżki!");
            return lore;
        }

        lore.add("");
        lore.add("&8» &7Następny poziom: &a" + (track == SpawnerUpgradeTrack.LIGHT_IMMUNITY
                ? "Odblokowanie"
                : "Poziom " + nextDef.level()));
        lore.add("&8» &7Nowy efekt: &a" + describeTrackValue(track, nextDef));
        lore.add("");
        lore.add("&8» &7Koszt waluty: &e" + EconomyManager.formatMoney(nextDef.upgradeCost()));

        if (!nextDef.requiredItems().isEmpty()) {
            lore.add("&8» &7Wymagane przedmioty:");
            for (Map.Entry<Material, Integer> req : nextDef.requiredItems().entrySet()) {
                lore.add("  &8- &e" + req.getValue() + "x &f" + formatMaterialName(req.getKey()));
            }
        }

        lore.add("");
        lore.add("&aKliknij, aby ulepszyć tę ścieżkę!");
        return lore;
    }

    private static String describeTrackValue(SpawnerUpgradeTrack track, SpawnerTrackLevelDefinition def) {
        return switch (track) {
            case DELAY -> formatDelayRangeSeconds(def.minSpawnDelay(), def.maxSpawnDelay());
            case AMOUNT -> def.spawnCount() + " mobów (limit w pobliżu: " + def.maxNearbyEntities() + ")";
            case RANGE -> def.requiredPlayerRange() + " kratek";
            case LIGHT_IMMUNITY -> def.ignoreLight() ? "Spawn przy każdym świetle (0-15)" : "Wymaga naturalnego światła";
        };
    }

    private ItemStack createOverviewItem(CreatureSpawner spawner, SpawnerState state) {
        SpawnerTrackLevelDefinition delayDef = module.getTrackLevelDefinition(SpawnerUpgradeTrack.DELAY, state.delayLevel());
        SpawnerTrackLevelDefinition amountDef = module.getTrackLevelDefinition(SpawnerUpgradeTrack.AMOUNT, state.amountLevel());
        SpawnerTrackLevelDefinition rangeDef = module.getTrackLevelDefinition(SpawnerUpgradeTrack.RANGE, state.rangeLevel());

        int minDelay = delayDef != null ? delayDef.minSpawnDelay() : spawner.getMinSpawnDelay();
        int maxDelay = delayDef != null ? delayDef.maxSpawnDelay() : spawner.getMaxSpawnDelay();
        int spawnCount = amountDef != null ? amountDef.spawnCount() : spawner.getSpawnCount();
        int maxNearby = amountDef != null ? amountDef.maxNearbyEntities() : spawner.getMaxNearbyEntities();
        int playerRange = rangeDef != null ? rangeDef.requiredPlayerRange() : spawner.getRequiredPlayerRange();

        int maxDelayLvl = module.getMaxTrackLevel(SpawnerUpgradeTrack.DELAY);
        int maxAmountLvl = module.getMaxTrackLevel(SpawnerUpgradeTrack.AMOUNT);
        int maxRangeLvl = module.getMaxTrackLevel(SpawnerUpgradeTrack.RANGE);
        int totalMobs = Math.max(1, module.getMobDefinitions().size());

        ItemStack item = new ItemStack(Material.SPAWNER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(ChatUtils.itemComponent("&e&lInformacje o Spawnerze"));
            meta.lore(List.of(
                    ChatUtils.itemComponent("&8» &7Aktywny mob: &e" + module.formatMobDisplayName(state.activeEntity())),
                    ChatUtils.itemComponent("&8» &7Odblokowane moby: &e" + state.unlockedEntities().size() + "&8/&7" + totalMobs),
                    ChatUtils.itemComponent("&8» &7Czas respu (&ePoz. " + state.delayLevel() + "/" + maxDelayLvl + "&7): &f"
                            + formatDelayRangeSeconds(minDelay, maxDelay)),
                    ChatUtils.itemComponent("&8» &7Ilość mobów (&aPoz. " + state.amountLevel() + "/" + maxAmountLvl + "&7): &f"
                            + spawnCount + " &8(Limit: " + maxNearby + ")"),
                    ChatUtils.itemComponent("&8» &7Zasięg gracza (&bPoz. " + state.rangeLevel() + "/" + maxRangeLvl + "&7): &f"
                            + playerRange + " kratek"),
                    ChatUtils.itemComponent("&8» &7Ignorowanie światła: "
                            + (state.isLightImmune() ? "&aOdblokowane (0-15)" : "&cWyłączone"))
            ));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createTrackUpgradeItem(SpawnerUpgradeTrack track, SpawnerState state) {
        int currentLevel = state.getLevel(track);
        int maxLevel = module.getMaxTrackLevel(track);
        SpawnerTrackLevelDefinition currentDef = module.getTrackLevelDefinition(track, currentLevel);
        SpawnerTrackLevelDefinition nextDef = module.getTrackLevelDefinition(track, currentLevel + 1);

        ItemStack item = new ItemStack(track.icon());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String title = track.displayName() + " &8("
                    + (nextDef == null ? "&aMAX" : "&ePoz. " + currentLevel + "/" + maxLevel) + "&8)";
            meta.displayName(ChatUtils.itemComponent(title));
            List<Component> lore = buildTrackUpgradeLore(track, currentDef, nextDef, maxLevel).stream()
                    .map(line -> line.isEmpty() ? Component.empty() : ChatUtils.itemComponent(line))
                    .toList();
            meta.lore(lore);
            if (nextDef == null) {
                meta.setEnchantmentGlintOverride(Boolean.TRUE);
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createMobShopButtonItem(SpawnerState state) {
        SpawnerMobDefinition activeDef = module.getMobDefinition(state.activeEntity());
        Material icon = activeDef != null ? activeDef.icon() : Material.PIG_SPAWN_EGG;
        int totalMobs = Math.max(1, module.getMobDefinitions().size());

        ItemStack item = new ItemStack(icon);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(ChatUtils.itemComponent("&6&lWybór / Zakup Moba"));
            meta.lore(List.of(
                    ChatUtils.itemComponent("&8» &7Aktualny mob: &e" + module.formatMobDisplayName(state.activeEntity())),
                    ChatUtils.itemComponent("&8» &7Odblokowane: &a" + state.unlockedEntities().size() + "&8/&7" + totalMobs),
                    Component.empty(),
                    ChatUtils.itemComponent("&7Wykup dowolnego z &f" + totalMobs + " &7mobów"),
                    ChatUtils.itemComponent("&7i przełączaj je bez utraty ulepszeń!"),
                    Component.empty(),
                    ChatUtils.itemComponent("&aKliknij, aby otworzyć sklep mobów!")
            ));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createMobSelectionItem(SpawnerMobDefinition mobDef, SpawnerState state) {
        boolean active = state.activeEntity() == mobDef.entityType();
        boolean unlocked = state.isUnlocked(mobDef.entityType());

        String suffix = active ? " &8[&aAktywny&8]" : (unlocked ? " &8[&aOdblokowany&8]" : " &8[&cZablokowany&8]");
        String prefix = active ? "&a&l✔ " : "";

        ItemStack item = new ItemStack(mobDef.icon());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(ChatUtils.itemComponent(prefix + mobDef.displayName() + suffix));
            List<Component> lore = new ArrayList<>();
            if (active) {
                lore.add(ChatUtils.itemComponent("&8» &7Status: &aAktualnie aktywny"));
                lore.add(Component.empty());
                lore.add(ChatUtils.itemComponent("&aTen mob respi się z tego spawnera."));
            } else if (unlocked) {
                lore.add(ChatUtils.itemComponent("&8» &7Status: &aOdblokowany na tym spawnerze"));
                lore.add(Component.empty());
                lore.add(ChatUtils.itemComponent("&eKliknij, aby ustawić jako aktywnego moba!"));
            } else {
                lore.add(ChatUtils.itemComponent("&8» &7Status: &cWymaga odblokowania"));
                lore.add(ChatUtils.itemComponent("&8» &7Koszt zakupu: &e" + EconomyManager.formatMoney(mobDef.unlockCost())));
                lore.add(Component.empty());
                lore.add(ChatUtils.itemComponent("&aKliknij, aby wykupić i aktywować moba!"));
            }
            if (active) {
                meta.setEnchantmentGlintOverride(Boolean.TRUE);
            }
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
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
            head.setItemMeta(skullMeta);
        }
        return head;
    }

    private static ItemStack createSimpleButton(Material material, String name, List<String> loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(ChatUtils.itemComponent(name));
            if (loreLines != null && !loreLines.isEmpty()) {
                meta.lore(loreLines.stream()
                        .map(line -> line.isEmpty() ? Component.empty() : ChatUtils.itemComponent(line))
                        .toList());
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack createFillerItem() {
        return createSimpleButton(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
    }

    private static String formatDelayRangeSeconds(int minTicks, int maxTicks) {
        return formatSeconds(minTicks) + " - " + formatSeconds(maxTicks);
    }

    private static String formatSeconds(int ticks) {
        double sec = ticks / 20.0;
        if (Math.abs(sec - Math.rint(sec)) < 0.001) {
            return ((int) Math.rint(sec)) + "s";
        }
        return String.format(Locale.US, "%.1fs", sec);
    }

    private static String formatMaterialName(Material material) {
        if (material == null) {
            return "Unknown";
        }
        String[] words = material.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            String word = words[i];
            if (word.isEmpty()) {
                continue;
            }
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return sb.toString();
    }

    private static String plainText(String coloredText) {
        if (coloredText == null || coloredText.isEmpty()) {
            return "";
        }
        return PlainTextComponentSerializer.plainText().serialize(ChatUtils.component(coloredText));
    }
}
