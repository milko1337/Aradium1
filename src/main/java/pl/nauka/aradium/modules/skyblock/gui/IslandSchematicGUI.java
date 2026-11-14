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
import pl.nauka.aradium.modules.skyblock.island.Island;
import pl.nauka.aradium.modules.skyblock.island.IslandManager;
import pl.nauka.aradium.modules.skyblock.island.IslandSchematic;
import pl.nauka.aradium.utils.ChatUtils;

import java.util.List;
import java.util.Objects;

/**
 * Menu GUI wyboru schematu startowego przy tworzeniu wyspy SkyBlock.
 */
public class IslandSchematicGUI {

    private static final int SLOT_CLASSIC = 11;
    private static final int SLOT_DESERT = 13;
    private static final int SLOT_NETHER = 15;

    private final AradiumPlugin plugin;
    private final IslandManager islandManager;

    public IslandSchematicGUI(AradiumPlugin plugin, IslandManager islandManager) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.islandManager = Objects.requireNonNull(islandManager, "islandManager");
    }

    public void open(Player player) {
        SkyBlockGuiHolder holder = new SkyBlockGuiHolder(SkyBlockGuiHolder.GuiType.SCHEMATIC_SELECT, null, null);
        Inventory inventory = Bukkit.createInventory(holder, 27, ChatUtils.component("&8» &aWybierz Schemat Wyspy"));
        holder.setInventory(inventory);

        ItemStack filler = createFiller();
        for (int i = 0; i < 27; i++) {
            inventory.setItem(i, filler);
        }

        inventory.setItem(SLOT_CLASSIC, createSchematicItem(IslandSchematic.CLASSIC));
        inventory.setItem(SLOT_DESERT, createSchematicItem(IslandSchematic.DESERT));
        inventory.setItem(SLOT_NETHER, createSchematicItem(IslandSchematic.NETHER));

        player.openInventory(inventory);
    }

    public void handleClick(Player player, int slot) {
        IslandSchematic schematic = switch (slot) {
            case SLOT_CLASSIC -> IslandSchematic.CLASSIC;
            case SLOT_DESERT -> IslandSchematic.DESERT;
            case SLOT_NETHER -> IslandSchematic.NETHER;
            default -> null;
        };
        if (schematic == null) {
            return;
        }

        player.closeInventory();
        if (islandManager.getPlayerIsland(player.getUniqueId()).isPresent()) {
            player.sendMessage(ChatUtils.getMessage(
                    plugin.getConfig(),
                    "messages.island_already_exists",
                    "&cPosiadasz już swoją wyspę! Użyj &e/is home&c."
            ));
            return;
        }

        Island created = islandManager.createIsland(player, schematic);
        if (created != null) {
            player.sendMessage(ChatUtils.getMessage(
                            plugin.getConfig(),
                            "messages.island_created",
                            "&aPomyślnie utworzono Twoją wyspę (&e%schematic%&a)! Miłej gry!"
                    )
                    .replace("%schematic%", ChatUtils.color(schematic.getDisplayName())));
        }
    }

    private static ItemStack createSchematicItem(IslandSchematic schematic) {
        ItemStack stack = new ItemStack(schematic.getIcon());
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(ChatUtils.itemComponent(schematic.getDisplayName()));
            List<Component> lore = schematic.getLore().stream()
                    .map(line -> line.isEmpty() ? Component.empty() : ChatUtils.itemComponent(line))
                    .toList();
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static ItemStack createFiller() {
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();
        if (meta != null) {
            meta.displayName(ChatUtils.itemComponent(" "));
            filler.setItemMeta(meta);
        }
        return filler;
    }
}
