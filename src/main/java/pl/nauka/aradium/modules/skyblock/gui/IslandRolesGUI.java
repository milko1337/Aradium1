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
import pl.nauka.aradium.modules.skyblock.SkyBlockModule;
import pl.nauka.aradium.modules.skyblock.island.Island;
import pl.nauka.aradium.modules.skyblock.island.IslandPermission;
import pl.nauka.aradium.modules.skyblock.island.IslandRole;
import pl.nauka.aradium.utils.ChatUtils;

import java.util.List;
import java.util.Objects;

/**
 * Zaawansowane menu GUI uprawnień ról wyspy (TRUSTED, MEMBER, CO_OWNER),
 * w którym zielona/czerwona szyba oznacza aktualny status flagi.
 */
public class IslandRolesGUI {

    private static final int SLOT_TAB_TRUSTED = 2;
    private static final int SLOT_TAB_MEMBER = 4;
    private static final int SLOT_TAB_CO_OWNER = 6;
    private static final int SLOT_BACK = 49;

    private static final IslandPermission[] PERMISSIONS = IslandPermission.values();
    private static final int[] PERM_ICON_SLOTS = {19, 20, 21, 22, 23, 24, 25};
    private static final int[] PERM_STATUS_SLOTS = {28, 29, 30, 31, 32, 33, 34};

    private final AradiumPlugin plugin;
    private final SkyBlockModule module;

    public IslandRolesGUI(AradiumPlugin plugin, SkyBlockModule module) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.module = Objects.requireNonNull(module, "module");
    }

    public void open(Player player, Island island, IslandRole selectedRole) {
        if (player == null || island == null) {
            return;
        }
        IslandRole activeRole = (selectedRole == IslandRole.TRUSTED
                || selectedRole == IslandRole.MEMBER
                || selectedRole == IslandRole.CO_OWNER)
                ? selectedRole
                : IslandRole.MEMBER;

        SkyBlockGuiHolder holder = new SkyBlockGuiHolder(
                SkyBlockGuiHolder.GuiType.ROLES_MENU,
                island.getIslandId(),
                activeRole
        );
        Inventory inventory = Bukkit.createInventory(holder, 54, ChatUtils.component("&8» &6Uprawnienia Ról Wyspy"));
        holder.setInventory(inventory);

        ItemStack filler = createItem(Material.GRAY_STAINED_GLASS_PANE, " ", List.of(), false);
        for (int i = 0; i < 54; i++) {
            inventory.setItem(i, filler);
        }

        inventory.setItem(SLOT_TAB_TRUSTED, createRoleTab(IslandRole.TRUSTED, Material.FEATHER, activeRole == IslandRole.TRUSTED));
        inventory.setItem(SLOT_TAB_MEMBER, createRoleTab(IslandRole.MEMBER, Material.IRON_CHESTPLATE, activeRole == IslandRole.MEMBER));
        inventory.setItem(SLOT_TAB_CO_OWNER, createRoleTab(IslandRole.CO_OWNER, Material.GOLDEN_HELMET, activeRole == IslandRole.CO_OWNER));

        for (int i = 0; i < PERMISSIONS.length && i < PERM_ICON_SLOTS.length; i++) {
            IslandPermission perm = PERMISSIONS[i];
            boolean allowed = island.getRolePermission(activeRole, perm);

            inventory.setItem(PERM_ICON_SLOTS[i], createItem(
                    perm.getIcon(),
                    "&e&l" + perm.getDisplayName(),
                    List.of(
                            "&8» &7" + perm.getDescription(),
                            "&8» &7Rola: " + activeRole.getDisplayName(),
                            "&8» &7Status: " + (allowed ? "&aDOZWOLONE" : "&cZABLOKOWANE")
                    ),
                    false
            ));

            Material paneMat = allowed ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE;
            String paneName = allowed ? "&a&lDOZWOLONE" : "&c&lZABLOKOWANE";
            inventory.setItem(PERM_STATUS_SLOTS[i], createItem(
                    paneMat,
                    paneName,
                    List.of(
                            "&8» &7Uprawnienie: &f" + perm.getDisplayName(),
                            "&8» &7Rola: " + activeRole.getDisplayName(),
                            "",
                            "&eKliknij, aby przełączyć!"
                    ),
                    false
            ));
        }

        inventory.setItem(SLOT_BACK, createItem(
                Material.ARROW,
                "&c&lPowrót",
                List.of("&7Kliknij, aby wrócić do Panelu Wyspy."),
                false
        ));

        player.openInventory(inventory);
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

        IslandRole clickedTab = switch (slot) {
            case SLOT_TAB_TRUSTED -> IslandRole.TRUSTED;
            case SLOT_TAB_MEMBER -> IslandRole.MEMBER;
            case SLOT_TAB_CO_OWNER -> IslandRole.CO_OWNER;
            default -> null;
        };
        if (clickedTab != null) {
            plugin.getServer().getScheduler().runTask(plugin, () -> open(player, island, clickedTab));
            return;
        }

        int permIndex = findPermissionIndexBySlot(slot);
        if (permIndex < 0) {
            return;
        }

        if (!island.getOwnerUuid().equals(player.getUniqueId())) {
            player.sendMessage(ChatUtils.getMessage(
                    plugin.getConfig(),
                    "messages.island_no_permission",
                    "&cTylko właściciel wyspy może zmieniać uprawnienia ról!"
            ));
            return;
        }

        IslandRole activeRole = holder.getSelectedRole() != null ? holder.getSelectedRole() : IslandRole.MEMBER;
        IslandPermission perm = PERMISSIONS[permIndex];
        island.toggleRolePermission(activeRole, perm);
        module.getIslandManager().saveIsland(island);

        plugin.getServer().getScheduler().runTask(plugin, () -> open(player, island, activeRole));
    }

    private static int findPermissionIndexBySlot(int slot) {
        for (int i = 0; i < PERMISSIONS.length; i++) {
            if (PERM_ICON_SLOTS[i] == slot || PERM_STATUS_SLOTS[i] == slot) {
                return i;
            }
        }
        return -1;
    }

    private static ItemStack createRoleTab(IslandRole role, Material material, boolean active) {
        return createItem(
                material,
                role.getDisplayName(),
                List.of(
                        active ? "&aAktualnie wybrane w edytorze." : "&eKliknij, aby edytować uprawnienia tej roli."
                ),
                active
        );
    }

    private static ItemStack createItem(Material material, String name, List<String> loreLines, boolean glint) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(ChatUtils.itemComponent(name));
            if (loreLines != null && !loreLines.isEmpty()) {
                List<Component> lore = loreLines.stream()
                        .map(line -> line.isEmpty() ? Component.empty() : ChatUtils.itemComponent(line))
                        .toList();
                meta.lore(lore);
            }
            if (glint) {
                meta.setEnchantmentGlintOverride(true);
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            stack.setItemMeta(meta);
        }
        return stack;
    }
}
