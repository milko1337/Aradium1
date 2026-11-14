package pl.nauka.aradium.modules.spawners;

import net.kyori.adventure.text.Component;
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
import pl.nauka.aradium.AradiumPlugin;
import pl.nauka.aradium.core.PluginModule;
import pl.nauka.aradium.modules.economy.EconomyManager;
import pl.nauka.aradium.modules.economy.EconomyModule;
import pl.nauka.aradium.modules.shop.ShopCategory;
import pl.nauka.aradium.modules.shop.ShopGUI;
import pl.nauka.aradium.utils.ChatUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * GUI ulepszeń spawnerów otwierane przez Shift + PPM bezpośrednio na blok spawnera.
 */
public class SpawnerGUI {

    public static final int SLOT_INFO = 11;
    public static final int SLOT_UPGRADE = 15;

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

    public Inventory createInventory(CreatureSpawner spawner) {
        SpawnerDataManager dataManager = module.getDataManager();
        int currentTier = dataManager.getSpawnerTier(spawner);
        EntityType entityType = dataManager.getSpawnerEntityType(spawner);

        SpawnerUpgradeHolder holder = SpawnerUpgradeHolder.forBlock(spawner.getBlock());
        int size = ShopCategory.normalizeInventorySize(module.getGuiSize());
        String titleText = module.getGuiTitle()
                .replace("%tier%", String.valueOf(currentTier))
                .replace("%entity%", SpawnerDataManager.formatEntityName(entityType));

        Inventory inventory = Bukkit.createInventory(holder, size, ChatUtils.component(titleText));
        holder.setInventory(inventory);

        ItemStack filler = createFillerItem();
        for (int i = 0; i < size; i++) {
            inventory.setItem(i, filler);
        }

        SpawnerTierDefinition currentTierDef = module.getTierDefinition(currentTier);
        SpawnerTierDefinition nextTierDef = module.getTierDefinition(currentTier + 1);

        inventory.setItem(SLOT_INFO, createInfoItem(spawner, entityType, currentTier, currentTierDef));
        inventory.setItem(SLOT_UPGRADE, createUpgradeItem(currentTier, currentTierDef, nextTierDef));
        return inventory;
    }

    public void handleUpgradeClick(Player player, SpawnerUpgradeHolder holder) {
        CreatureSpawner spawner = holder.resolveSpawner();
        if (spawner == null) {
            player.closeInventory();
            player.sendMessage(ChatUtils.getMessage(
                    plugin.getConfig(),
                    "messages.spawner_not_found",
                    "&cTen spawner już nie istnieje!"
            ));
            return;
        }

        SpawnerDataManager dataManager = module.getDataManager();
        int currentTier = dataManager.getSpawnerTier(spawner);
        SpawnerTierDefinition nextTier = module.getTierDefinition(currentTier + 1);
        if (nextTier == null) {
            player.sendMessage(ChatUtils.getMessage(
                    plugin.getConfig(),
                    "messages.spawner_max_tier",
                    "&eTen spawner osiągnął już maksymalny poziom ulepszenia!"
            ));
            refreshViewersForBlock(holder.getWorldName(), holder.getBlockX(), holder.getBlockY(), holder.getBlockZ(), spawner);
            return;
        }

        double cost = Math.max(0.0, EconomyManager.normalizeAmount(nextTier.upgradeCost()));
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
        Map<Material, Integer> missingItems = computeMissingItems(inventory, nextTier.requiredItems());
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

        if (!nextTier.requiredItems().isEmpty()) {
            removeRequiredItems(inventory, nextTier.requiredItems());
        }

        EntityType entityType = dataManager.getSpawnerEntityType(spawner);
        dataManager.applyTierToBlock(spawner, nextTier, entityType);

        player.sendMessage(ChatUtils.getMessage(
                        plugin.getConfig(),
                        "messages.spawner_upgraded",
                        "&aUlepszono spawner &e%entity% &ado poziomu &6Tier %tier%&a!"
                )
                .replace("%entity%", SpawnerDataManager.formatEntityName(entityType))
                .replace("%tier%", String.valueOf(nextTier.tier())));

        refreshViewersForBlock(holder.getWorldName(), holder.getBlockX(), holder.getBlockY(), holder.getBlockZ(), spawner);
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
                    openSpawnerGUI(online, updatedSpawner);
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

    private static ItemStack createInfoItem(
            CreatureSpawner spawner,
            EntityType entityType,
            int currentTier,
            SpawnerTierDefinition currentTierDef
    ) {
        int minDelay = currentTierDef != null ? currentTierDef.minSpawnDelay() : spawner.getMinSpawnDelay();
        int maxDelay = currentTierDef != null ? currentTierDef.maxSpawnDelay() : spawner.getMaxSpawnDelay();
        int spawnCount = currentTierDef != null ? currentTierDef.spawnCount() : spawner.getSpawnCount();
        int maxNearby = currentTierDef != null ? currentTierDef.maxNearbyEntities() : spawner.getMaxNearbyEntities();
        int playerRange = currentTierDef != null ? currentTierDef.requiredPlayerRange() : spawner.getRequiredPlayerRange();

        ItemStack item = new ItemStack(Material.SPAWNER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(ChatUtils.itemComponent("&e&lInformacje o Spawnerze"));
            List<Component> lore = List.of(
                    ChatUtils.itemComponent("&8» &7Mob: &e" + SpawnerDataManager.formatEntityName(entityType)),
                    ChatUtils.itemComponent("&8» &7Aktualny poziom: &6Tier " + currentTier),
                    ChatUtils.itemComponent("&8» &7Opóźnienie spawnu: &f" + formatDelayRangeSeconds(minDelay, maxDelay)),
                    ChatUtils.itemComponent("&8» &7Ilość mobów: &f" + spawnCount),
                    ChatUtils.itemComponent("&8» &7Limit w pobliżu: &f" + maxNearby),
                    ChatUtils.itemComponent("&8» &7Zasięg gracza: &f" + playerRange + " kratek")
            );
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack createUpgradeItem(
            int currentTier,
            SpawnerTierDefinition currentTierDef,
            SpawnerTierDefinition nextTierDef
    ) {
        Material icon = nextTierDef != null ? Material.EXPERIENCE_BOTTLE : Material.GOLD_BLOCK;
        String displayName = nextTierDef != null
                ? "&a&lUlepsz do Tieru " + nextTierDef.tier()
                : "&6&lMaksymalny Poziom &8(&eTier " + currentTier + "&8)";

        ItemStack item = new ItemStack(icon);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(ChatUtils.itemComponent(displayName));
            List<Component> lore = buildUpgradeLore(currentTierDef, nextTierDef).stream()
                    .map(line -> line.isEmpty() ? Component.empty() : ChatUtils.itemComponent(line))
                    .toList();
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
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
}
