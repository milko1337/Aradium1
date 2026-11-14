package pl.nauka.aradium.modules.skyblock.gui;

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
import pl.nauka.aradium.modules.skyblock.island.IslandWarp;
import pl.nauka.aradium.utils.ChatUtils;

import java.util.List;
import java.util.Objects;

/**
 * Menu GUI prezentujące wszystkie dostępne publiczne warpy wysp SkyBlock.
 */
public class IslandWarpsGUI {

    private static final int SLOT_BACK = 49;

    private final AradiumPlugin plugin;
    private final SkyBlockModule module;

    public IslandWarpsGUI(AradiumPlugin plugin, SkyBlockModule module) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.module = Objects.requireNonNull(module, "module");
    }

    public void open(Player player) {
        if (player == null) {
            return;
        }
        IslandManager islandManager = module.getIslandManager();
        SkyBlockGuiHolder holder = new SkyBlockGuiHolder(SkyBlockGuiHolder.GuiType.WARPS_MENU, null, null);
        Inventory inventory = Bukkit.createInventory(holder, 54, ChatUtils.component("&8» &dPubliczne Warpy Wysp"));
        holder.setInventory(inventory);

        ItemStack filler = createItem(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 45; i < 54; i++) {
            inventory.setItem(i, filler);
        }

        int slot = 0;
        for (Island island : islandManager.getAllIslands()) {
            if (!island.isPublicAccess() && !island.isPermanentMember(player.getUniqueId())) {
                continue;
            }
            OfflinePlayer owner = Bukkit.getOfflinePlayer(island.getOwnerUuid());
            String ownerName = owner.getName() != null ? owner.getName() : island.getOwnerUuid().toString();

            for (IslandWarp warp : island.getWarpsSnapshot().values()) {
                if (slot >= 45) {
                    break;
                }
                holder.registerWarpSlot(slot, warp);
                inventory.setItem(slot, createItem(
                        warp.icon(),
                        "&d&l" + warp.name(),
                        List.of(
                                "&8» &7Właściciel wyspy: &f" + ownerName,
                                "&8» &7Status wyspy: " + (island.isPublicAccess() ? "&aPubliczna" : "&cPrywatna"),
                                "",
                                "&aKliknij, aby przeteleportować!"
                        )
                ));
                slot++;
            }
            if (slot >= 45) {
                break;
            }
        }

        inventory.setItem(SLOT_BACK, createItem(
                Material.ARROW,
                "&c&lPowrót",
                List.of("&7Wróć do panelu wyspy lub zamknij menu."),
                false
        ));

        player.openInventory(inventory);
    }

    public void handleClick(Player player, SkyBlockGuiHolder holder, int slot) {
        if (slot == SLOT_BACK) {
            module.getIslandManager().getPlayerIsland(player.getUniqueId()).ifPresentOrElse(
                    island -> plugin.getServer().getScheduler().runTask(plugin, () -> module.getPanelGUI().open(player, island)),
                    player::closeInventory
            );
            return;
        }

        IslandWarp warp = holder.getWarpAtSlot(slot);
        if (warp == null) {
            return;
        }

        player.closeInventory();
        Location loc = warp.toLocation();
        if (loc == null) {
            return;
        }

        player.teleport(loc);
        module.getIslandManager().refreshWorldBorder(player, true);
        player.sendMessage(ChatUtils.getMessage(
                        plugin.getConfig(),
                        "messages.island_warp_teleported",
                        "&aPrzeteleportowano na warp &e%warp%&a!"
                )
                .replace("%warp%", warp.name()));
    }

    private static ItemStack createItem(Material material, String name, List<String> loreLines) {
        return createItem(material, name, loreLines, true);
    }

    private static ItemStack createItem(Material material, String name, List<String> loreLines, boolean hideAttributes) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(ChatUtils.itemComponent(name));
            if (loreLines != null && !loreLines.isEmpty()) {
                meta.lore(loreLines.stream()
                        .map(line -> line.isEmpty()
                                ? net.kyori.adventure.text.Component.empty()
                                : ChatUtils.itemComponent(line))
                        .toList());
            }
            if (hideAttributes) {
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            }
            item.setItemMeta(meta);
        }
        return item;
    }
}
