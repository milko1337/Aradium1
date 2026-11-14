package pl.nauka.aradium.modules.skyblock.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import pl.nauka.aradium.AradiumPlugin;
import pl.nauka.aradium.modules.economy.EconomyManager;
import pl.nauka.aradium.modules.economy.EconomyModule;
import pl.nauka.aradium.modules.skyblock.SkyBlockModule;
import pl.nauka.aradium.modules.skyblock.island.Island;
import pl.nauka.aradium.modules.skyblock.island.IslandRole;
import pl.nauka.aradium.modules.skyblock.upgrades.IslandUpgradeManager;
import pl.nauka.aradium.modules.skyblock.upgrades.IslandUpgradeManager.UpgradeTier;
import pl.nauka.aradium.modules.skyblock.upgrades.IslandUpgradeType;
import pl.nauka.aradium.utils.ChatUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Menu GUI ulepszeń wyspy SkyBlock (WorldBorder, Limit Spawnerów, Wzrost Roślin, Limit Członków).
 */
public class IslandUpgradesGUI {

    private static final int SLOT_BORDER = 10;
    private static final int SLOT_SPAWNERS = 12;
    private static final int SLOT_CROPS = 14;
    private static final int SLOT_MEMBERS = 16;
    private static final int SLOT_BACK = 22;

    private final AradiumPlugin plugin;
    private final SkyBlockModule module;

    public IslandUpgradesGUI(AradiumPlugin plugin, SkyBlockModule module) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.module = Objects.requireNonNull(module, "module");
    }

    public void open(Player player, Island island) {
        if (player == null || island == null) {
            return;
        }
        SkyBlockGuiHolder holder = new SkyBlockGuiHolder(SkyBlockGuiHolder.GuiType.UPGRADES_MENU, island.getIslandId(), null);
        Inventory inventory = Bukkit.createInventory(holder, 27, ChatUtils.component("&8» &6Ulepszenia Wyspy"));
        holder.setInventory(inventory);

        ItemStack filler = createItem(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < 27; i++) {
            inventory.setItem(i, filler);
        }

        inventory.setItem(SLOT_BORDER, buildUpgradeIcon(player, island, IslandUpgradeType.WORLD_BORDER));
        inventory.setItem(SLOT_SPAWNERS, buildUpgradeIcon(player, island, IslandUpgradeType.SPAWNER_LIMIT));
        inventory.setItem(SLOT_CROPS, buildUpgradeIcon(player, island, IslandUpgradeType.CROP_GROWTH));
        inventory.setItem(SLOT_MEMBERS, buildUpgradeIcon(player, island, IslandUpgradeType.MEMBER_LIMIT));

        inventory.setItem(SLOT_BACK, createItem(
                Material.ARROW,
                "&c&lPowrót do Panelu",
                List.of("&7Kliknij, aby wrócić do panelu wyspy.")
        ));

        player.openInventory(inventory);
    }

    private ItemStack buildUpgradeIcon(Player player, Island island, IslandUpgradeType type) {
        IslandUpgradeManager upgradeManager = module.getUpgradeManager();
        int currentTierNum = island.getUpgradeTier(type);
        int maxTierNum = upgradeManager.getMaxTier(type);
        UpgradeTier currentTier = upgradeManager.getTier(type, currentTierNum);
        UpgradeTier nextTier = upgradeManager.getExactTier(type, currentTierNum + 1);

        List<String> lore = new ArrayList<>();
        lore.add("&8» &7Aktualny poziom: &eTier " + currentTierNum + "&7/&f" + maxTierNum);
        if (currentTier != null) {
            lore.add("&8» &7Aktualna wartość: &a" + type.formatValue(currentTier.value()));
        }
        if (type == IslandUpgradeType.SPAWNER_LIMIT) {
            lore.add("&8» &7Spawnery na wyspie: &f" + island.getPlacedSpawnersCount() + "&7/&f" + island.getSpawnerLimit());
        } else if (type == IslandUpgradeType.MEMBER_LIMIT) {
            lore.add("&8» &7Członkowie na wyspie: &f" + island.getPermanentMemberCount() + "&7/&f" + island.getMaxMembers());
        }

        lore.add("");
        if (nextTier == null) {
            lore.add("&6&lMAKSYMALNY POZIOM!");
        } else {
            lore.add("&8» &7Następny poziom: &bTier " + nextTier.tier());
            lore.add("&8» &7Nowa wartość: &b" + type.formatValue(nextTier.value()));
            lore.add("&8» &7Koszt ulepszenia: &6" + EconomyManager.formatMoney(nextTier.cost()));
            lore.add("");

            boolean hasRole = island.getPlayerRole(player.getUniqueId()).isAtLeast(IslandRole.CO_OWNER);
            if (!hasRole) {
                lore.add("&cWymagana rola CO_OWNER lub OWNER!");
            } else {
                boolean canAfford = plugin.getEconomyModule()
                        .map(EconomyModule::getEconomyManager)
                        .map(eco -> eco.hasMoney(player.getUniqueId(), nextTier.cost()))
                        .orElse(false);
                lore.add(canAfford ? "&aKliknij, aby ulepszyć!" : "&cNiewystarczające środki na koncie!");
            }
        }

        return createItem(type.getIcon(), type.getDisplayName(), lore);
    }

    public void handleClick(Player player, SkyBlockGuiHolder holder, int slot) {
        Island island = module.getIslandManager().getIslandById(holder.getIslandId()).orElse(null);
        if (island == null) {
            player.closeInventory();
            return;
        }

        if (slot == SLOT_BACK) {
            plugin.getServer().getScheduler().runTask(plugin, () -> module.getPanelGUI().open(player, island));
            return;
        }

        IslandUpgradeType selected = switch (slot) {
            case SLOT_BORDER -> IslandUpgradeType.WORLD_BORDER;
            case SLOT_SPAWNERS -> IslandUpgradeType.SPAWNER_LIMIT;
            case SLOT_CROPS -> IslandUpgradeType.CROP_GROWTH;
            case SLOT_MEMBERS -> IslandUpgradeType.MEMBER_LIMIT;
            default -> null;
        };

        if (selected != null) {
            module.getUpgradeManager().purchaseUpgrade(player, island, selected);
            plugin.getServer().getScheduler().runTask(plugin, () -> open(player, island));
        }
    }

    private static ItemStack createItem(Material material, String name, List<String> loreLines) {
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
}
