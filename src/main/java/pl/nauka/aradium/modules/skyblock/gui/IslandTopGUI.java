package pl.nauka.aradium.modules.skyblock.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import pl.nauka.aradium.AradiumPlugin;
import pl.nauka.aradium.modules.skyblock.SkyBlockModule;
import pl.nauka.aradium.modules.skyblock.island.Island;
import pl.nauka.aradium.modules.skyblock.island.IslandManager;
import pl.nauka.aradium.modules.skyblock.level.IslandTopCache;
import pl.nauka.aradium.utils.ChatUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Menu GUI prezentujące ranking TOP 10 wysp na żywo (/is top).
 */
public class IslandTopGUI {

    private static final int[] TOP_SLOTS = {2, 3, 4, 5, 6, 11, 12, 13, 14, 15};
    private static final int SLOT_OWN_RANK = 26;
    private static final int SLOT_BACK = 22;

    private final AradiumPlugin plugin;
    private final SkyBlockModule module;

    public IslandTopGUI(AradiumPlugin plugin, SkyBlockModule module) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.module = Objects.requireNonNull(module, "module");
    }

    public void open(Player player) {
        if (player == null) {
            return;
        }
        IslandManager islandManager = module.getIslandManager();
        IslandTopCache topCache = module.getTopCache();
        topCache.refresh(islandManager.getAllIslands());

        Optional<Island> ownIslandOpt = islandManager.getPlayerIsland(player.getUniqueId());
        UUID ownId = ownIslandOpt.map(Island::getIslandId).orElse(null);

        SkyBlockGuiHolder holder = new SkyBlockGuiHolder(SkyBlockGuiHolder.GuiType.TOP_MENU, ownId, null);
        Inventory inventory = Bukkit.createInventory(holder, 27, ChatUtils.component("&8» &6Ranking Wysp (TOP 10)"));
        holder.setInventory(inventory);

        ItemStack filler = createItem(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < 27; i++) {
            inventory.setItem(i, filler);
        }

        List<Island> topList = topCache.getTopIslands(TOP_SLOTS.length);
        for (int i = 0; i < topList.size(); i++) {
            int slot = TOP_SLOTS[i];
            int rank = i + 1;
            Island island = topList.get(i);
            holder.registerTargetIslandSlot(slot, island.getIslandId());

            OfflinePlayer owner = Bukkit.getOfflinePlayer(island.getOwnerUuid());
            String ownerName = owner.getName() != null ? owner.getName() : island.getOwnerUuid().toString();
            Material icon = switch (rank) {
                case 1 -> Material.NETHER_STAR;
                case 2 -> Material.DIAMOND;
                case 3 -> Material.GOLD_INGOT;
                default -> Material.PAPER;
            };

            List<String> lore = new ArrayList<>();
            lore.add("&8» &7Właściciel: &f" + ownerName);
            lore.add("&8» &7Poziom wyspy: &6" + formatLevel(island.getIslandLevel()));
            lore.add("&8» &7Członkowie: &a" + island.getPermanentMemberCount() + "&7/&f" + island.getMaxMembers());
            lore.add("&8» &7Status: " + (island.isPublicAccess() ? "&aPUBLICZNA" : "&cPRYWATNA"));
            lore.add("");
            boolean canVisit = island.canVisit(player.getUniqueId()) || player.hasPermission("aradium.admin");
            lore.add(canVisit ? "&aKliknij, aby odwiedzić tę wyspę!" : "&cWyspa prywatna — brak wstępu");

            inventory.setItem(slot, createItem(icon, "&6&l#" + rank + " &fWyspa &e" + ownerName, lore));
        }

        if (ownIslandOpt.isPresent()) {
            Island own = ownIslandOpt.get();
            int ownRank = topCache.getIslandRank(own.getIslandId());
            inventory.setItem(SLOT_OWN_RANK, createItem(
                    Material.NETHERITE_INGOT,
                    "&e&lTwoja Wyspa",
                    List.of(
                            "&8» &7Miejsce w rankingu: &6#" + (ownRank > 0 ? ownRank : "?"),
                            "&8» &7Poziom wyspy: &6" + formatLevel(own.getIslandLevel()),
                            "&8» &7Wpisz &f/is level&7, aby odświeżyć poziom!"
                    )
            ));
        }

        inventory.setItem(SLOT_BACK, createItem(
                Material.ARROW,
                ownId != null ? "&c&lPowrót do Panelu" : "&c&lZamknij",
                List.of("&7Kliknij, aby wrócić.")
        ));

        player.openInventory(inventory);
    }

    public void handleClick(Player player, SkyBlockGuiHolder holder, int slot) {
        IslandManager islandManager = module.getIslandManager();
        if (slot == SLOT_BACK) {
            if (holder.getIslandId() != null) {
                Island own = islandManager.getIslandById(holder.getIslandId()).orElse(null);
                if (own != null) {
                    plugin.getServer().getScheduler().runTask(plugin, () -> module.getPanelGUI().open(player, own));
                    return;
                }
            }
            player.closeInventory();
            return;
        }

        UUID targetId = holder.getTargetIslandAtSlot(slot);
        if (targetId == null) {
            return;
        }
        Island target = islandManager.getIslandById(targetId).orElse(null);
        if (target == null) {
            player.closeInventory();
            return;
        }
        if (!target.canVisit(player.getUniqueId()) && !player.hasPermission("aradium.admin")) {
            player.sendMessage(ChatUtils.getMessage(
                    plugin.getConfig(),
                    "messages.island_private_blocked",
                    "&cTa wyspa jest prywatna! Właściciel zablokował dostęp odwiedzającym."
            ));
            return;
        }

        Location home = target.getHomeLocation();
        if (home != null) {
            player.closeInventory();
            player.teleport(home);
            islandManager.refreshWorldBorder(player, true);
            OfflinePlayer owner = Bukkit.getOfflinePlayer(target.getOwnerUuid());
            String ownerName = owner.getName() != null ? owner.getName() : "Gracz";
            player.sendMessage(ChatUtils.color("&aOdwiedzasz wyspę gracza &e" + ownerName + "&a!"));
        }
    }

    public static String formatLevel(double level) {
        return String.format(Locale.US, "%,.2f", Math.max(0.0, level));
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
