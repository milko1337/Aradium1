package pl.nauka.aradium.modules.skyblock.gui;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import pl.nauka.aradium.AradiumPlugin;
import pl.nauka.aradium.modules.skyblock.SkyBlockModule;
import pl.nauka.aradium.modules.skyblock.island.BorderColor;
import pl.nauka.aradium.modules.skyblock.island.Island;
import pl.nauka.aradium.modules.skyblock.island.IslandManager;
import pl.nauka.aradium.modules.skyblock.island.IslandRole;
import pl.nauka.aradium.utils.ChatUtils;

import java.util.List;
import java.util.Objects;

/**
 * Główne menu zarządzania wyspą SkyBlock (dom, prywatność, kolor WorldBorder, role, warpy).
 */
public class IslandPanelGUI {

    private static final int SLOT_HOME = 10;
    private static final int SLOT_PRIVACY = 11;
    private static final int SLOT_BORDER = 12;
    private static final int SLOT_UPGRADES = 13;
    private static final int SLOT_ROLES = 14;
    private static final int SLOT_WARPS = 15;
    private static final int SLOT_INFO = 16;
    private static final int SLOT_QUESTS = 19;
    private static final int SLOT_LEVEL = 21;
    private static final int SLOT_TOP = 23;

    private final AradiumPlugin plugin;
    private final SkyBlockModule module;

    public IslandPanelGUI(AradiumPlugin plugin, SkyBlockModule module) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.module = Objects.requireNonNull(module, "module");
    }

    public void open(Player player, Island island) {
        if (player == null || island == null) {
            return;
        }
        SkyBlockGuiHolder holder = new SkyBlockGuiHolder(SkyBlockGuiHolder.GuiType.ISLAND_PANEL, island.getIslandId(), null);
        Inventory inventory = Bukkit.createInventory(holder, 27, ChatUtils.component("&8» &6Panel Wyspy"));
        holder.setInventory(inventory);

        ItemStack filler = createSimpleItem(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < 27; i++) {
            inventory.setItem(i, filler);
        }

        inventory.setItem(SLOT_HOME, createSimpleItem(
                Material.OAK_DOOR,
                "&a&lDom Wyspy",
                List.of(
                        "&8» &7Teleportuje Cię do punktu domu",
                        "&8» &7Twojej wyspy (&f/is home&7).",
                        "",
                        "&aKliknij, aby przeteleportować!"
                )
        ));

        boolean isPublic = island.isPublicAccess();
        inventory.setItem(SLOT_PRIVACY, createSimpleItem(
                isPublic ? Material.LIME_DYE : Material.RED_DYE,
                "&e&lPrywatność Wyspy",
                List.of(
                        "&8» &7Aktualny status: " + (isPublic ? "&aPUBLICZNA" : "&cPRYWATNA"),
                        "&8» &7Publiczne wyspy mogą odwiedzać wszyscy gracze.",
                        "",
                        "&eKliknij, aby przełączyć status!"
                )
        ));

        BorderColor color = island.getBorderColor();
        inventory.setItem(SLOT_BORDER, createSimpleItem(
                color.getIcon(),
                "&b&lKolor Granicy WorldBorder",
                List.of(
                        "&8» &7Aktualny kolor: " + color.getDisplayName(),
                        "&8» &7Dostępne: &bNiebieski &8/ &aZielony &8/ &cCzerwony",
                        "",
                        "&eKliknij, aby zmienić kolor!"
                )
        ));

        inventory.setItem(SLOT_UPGRADES, createSimpleItem(
                Material.ANVIL,
                "&a&lUlepszenia Wyspy",
                List.of(
                        "&8» &7Powiększ rozmiar WorldBorder,",
                        "&8» &7limit spawnerów, wzrost roślin i limit członków.",
                        "",
                        "&eKliknij, aby otworzyć menu ulepszeń!"
                )
        ));

        inventory.setItem(SLOT_ROLES, createSimpleItem(
                Material.COMPARATOR,
                "&6&lUprawnienia Ról",
                List.of(
                        "&8» &7Konfiguruj uprawnienia dla ról:",
                        "&8  - &eZaufany (TRUSTED)",
                        "&8  - &aCzłonek (MEMBER)",
                        "&8  - &6Współwłaściciel (CO_OWNER)",
                        "",
                        "&eKliknij, aby otworzyć menu ról!"
                )
        ));

        inventory.setItem(SLOT_WARPS, createSimpleItem(
                Material.ENDER_PEARL,
                "&d&lPubliczne Warpy",
                List.of(
                        "&8» &7Przeglądaj publiczne warpy graczy",
                        "&8» &7i teleportuj się na ich wyspy.",
                        "",
                        "&eKliknij, aby otworzyć listę warpów!"
                )
        ));

        OfflinePlayer owner = Bukkit.getOfflinePlayer(island.getOwnerUuid());
        String ownerName = owner.getName() != null ? owner.getName() : island.getOwnerUuid().toString();
        inventory.setItem(SLOT_INFO, createSimpleItem(
                Material.NETHER_STAR,
                "&e&lInformacje o Wyspie",
                List.of(
                        "&8» &7Właściciel: &f" + ownerName,
                        "&8» &7Poziom wyspy: &6" + IslandTopGUI.formatLevel(island.getIslandLevel()),
                        "&8» &7Członkowie: &a" + island.getPermanentMemberCount() + "&7/&f" + island.getMaxMembers(),
                        "&8» &7Tymczasowo zaufani: &e" + island.getTrustedPlayersSnapshot().size(),
                        "&8» &7Zasięg granicy: &b" + island.getBorderRadius() + " kratek",
                        "&8» &7Spawnery: &d" + island.getPlacedSpawnersCount() + "&7/&f" + island.getSpawnerLimit(),
                        "&8» &7Wzrost roślin: &a+" + island.getCropGrowthBonusPercent() + "%",
                        "&8» &7Liczba warpów: &d" + island.getWarpsSnapshot().size()
                )
        ));

        int activeStage = module.getQuestManager() != null
                ? module.getQuestManager().getHighestUnlockedStage(island)
                : 1;
        inventory.setItem(SLOT_QUESTS, createSimpleItem(
                Material.WRITABLE_BOOK,
                "&e&lMisje Wyspy",
                List.of(
                        "&8» &7Aktualny etap: &6Etap " + activeStage + "&7/5",
                        "&8» &7Współdzielone zadania całej wyspy.",
                        "",
                        "&eKliknij, aby otworzyć misje!"
                )
        ));

        inventory.setItem(SLOT_LEVEL, createSimpleItem(
                Material.DIAMOND_BLOCK,
                "&b&lOblicz Poziom Wyspy",
                List.of(
                        "&8» &7Aktualny poziom: &6" + IslandTopGUI.formatLevel(island.getIslandLevel()),
                        "&8» &7Przelicza bloki asynchronicznie w tle.",
                        "",
                        "&eKliknij, aby przeliczyć poziom!"
                )
        ));

        inventory.setItem(SLOT_TOP, createSimpleItem(
                Material.GOLD_BLOCK,
                "&6&lRanking Wysp TOP 10",
                List.of(
                        "&8» &7Sprawdź najlepsze wyspy na serwerze",
                        "&8» &7i odwiedzaj publiczne wyspy graczy.",
                        "",
                        "&eKliknij, aby otworzyć ranking!"
                )
        ));

        player.openInventory(inventory);
    }

    public void handleClick(Player player, SkyBlockGuiHolder holder, int slot) {
        IslandManager islandManager = module.getIslandManager();
        Island island = islandManager.getIslandById(holder.getIslandId()).orElse(null);
        if (island == null) {
            player.closeInventory();
            return;
        }

        switch (slot) {
            case SLOT_HOME -> {
                player.closeInventory();
                Location home = island.getHomeLocation();
                if (home != null) {
                    player.teleport(home);
                    islandManager.refreshWorldBorder(player, true);
                    player.sendMessage(ChatUtils.getMessage(
                            plugin.getConfig(),
                            "messages.island_teleported_home",
                            "&aPrzeteleportowano na dom Twojej wyspy!"
                    ));
                }
            }
            case SLOT_PRIVACY -> {
                IslandRole role = island.getPlayerRole(player.getUniqueId());
                if (!role.isAtLeast(IslandRole.CO_OWNER)) {
                    player.sendMessage(ChatUtils.getMessage(
                            plugin.getConfig(),
                            "messages.island_no_permission",
                            "&cNie masz uprawnień do tej akcji na tej wyspie!"
                    ));
                    return;
                }
                boolean nextPublic = !island.isPublicAccess();
                island.setPublicAccess(nextPublic);
                islandManager.saveIsland(island);
                if (!nextPublic) {
                    evictUnauthorizedVisitors(island);
                }
                player.sendMessage(ChatUtils.getMessage(
                                plugin.getConfig(),
                                "messages.island_privacy_changed",
                                "&aStatus wyspy został zmieniony na: %status%&a."
                        )
                        .replace("%status%", ChatUtils.color(nextPublic ? "&aPUBLICZNA" : "&cPRYWATNA")));
                plugin.getServer().getScheduler().runTask(plugin, () -> open(player, island));
            }
            case SLOT_BORDER -> {
                IslandRole role = island.getPlayerRole(player.getUniqueId());
                if (!role.isAtLeast(IslandRole.CO_OWNER)) {
                    player.sendMessage(ChatUtils.getMessage(
                            plugin.getConfig(),
                            "messages.island_no_permission",
                            "&cNie masz uprawnień do tej akcji na tej wyspie!"
                    ));
                    return;
                }
                BorderColor nextColor = island.getBorderColor().next();
                island.setBorderColor(nextColor);
                islandManager.saveIsland(island);
                islandManager.refreshBordersOnIsland(island);
                player.sendMessage(ChatUtils.getMessage(
                                plugin.getConfig(),
                                "messages.island_border_changed",
                                "&aKolor granicy WorldBorder został zmieniony na: %color%&a."
                        )
                        .replace("%color%", ChatUtils.color(nextColor.getDisplayName())));
                plugin.getServer().getScheduler().runTask(plugin, () -> open(player, island));
            }
            case SLOT_UPGRADES -> plugin.getServer().getScheduler().runTask(
                    plugin, () -> module.getUpgradesGUI().open(player, island)
            );
            case SLOT_ROLES -> plugin.getServer().getScheduler().runTask(
                    plugin, () -> module.getRolesGUI().open(player, island, IslandRole.MEMBER)
            );
            case SLOT_WARPS -> plugin.getServer().getScheduler().runTask(
                    plugin, () -> module.getWarpsGUI().open(player)
            );
            case SLOT_QUESTS -> plugin.getServer().getScheduler().runTask(
                    plugin, () -> module.getQuestGUI().open(player, island)
            );
            case SLOT_LEVEL -> {
                player.closeInventory();
                if (module.getLevelCalculator() != null) {
                    module.getLevelCalculator().requestScan(player, island);
                }
            }
            case SLOT_TOP -> plugin.getServer().getScheduler().runTask(
                    plugin, () -> module.getTopGUI().open(player)
            );
            default -> {
                // Pozostałe sloty pełnią funkcję informacyjną/dekoracyjną.
            }
        }
    }

    private void evictUnauthorizedVisitors(Island island) {
        World fallbackWorld = Bukkit.getWorlds().stream()
                .filter(w -> !w.getName().equals(island.getWorldName()))
                .findFirst()
                .orElse(null);

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!island.containsLocation(online.getLocation())) {
                continue;
            }
            if (island.canVisit(online.getUniqueId()) || online.hasPermission("aradium.admin")) {
                continue;
            }
            online.setWorldBorder(null);
            if (fallbackWorld != null) {
                online.teleport(fallbackWorld.getSpawnLocation());
            } else if (online.getWorld().getSpawnLocation() != null) {
                online.teleport(online.getWorld().getSpawnLocation());
            }
            online.sendMessage(ChatUtils.getMessage(
                    plugin.getConfig(),
                    "messages.island_private_blocked",
                    "&cTa wyspa jest prywatna! Właściciel zablokował dostęp odwiedzającym."
            ));
        }
    }

    private static ItemStack createSimpleItem(Material material, String name, List<String> loreLines) {
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
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }
}
