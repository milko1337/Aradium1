package nauka1.Aradium1.zbroje.comand;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static nauka1.Aradium1.imports.general.console;
import static nauka1.Aradium1.imports.getmesage.itemarrmorMesage;
import static nauka1.Aradium1.imports.lore.*;
import static nauka1.Aradium1.imports.material.*;
import static nauka1.Aradium1.imports.name.*;

public class gold {

    public static class gold1 implements CommandExecutor, Listener {
        public gold1() {
            Objects.requireNonNull(Bukkit.getPluginCommand("gold1")).setExecutor(this);
        }

        @Override
        public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(console);
                return false;
            }

            Player player = (Player) sender;
            ItemStack item = new ItemStack(itemgoldMaterial1);
            ItemMeta meta = item.getItemMeta();
            assert meta != null;
            meta.setDisplayName(itemgoldName1 + "5");
            List<String> lore = new ArrayList<>();
            lore.add(itemLore1);
            meta.setLore(lore);
            meta.addEnchant(Enchantment.DURABILITY, 1, true);
            meta.addEnchant(Enchantment.PROTECTION_ENVIRONMENTAL, 1, true);
            meta.setUnbreakable(true);
            item.setItemMeta(meta);
            player.getInventory().addItem(item);

            ItemStack item2 = new ItemStack(itemgoldrMaterial2);
            ItemMeta meta2 = item2.getItemMeta();
            assert meta2 != null;
            meta2.setDisplayName(itemgoldName2 + "5");
            List<String> lore2 = new ArrayList<>();
            lore2.add(itemLore4);
            meta2.setLore(lore2);
            meta2.addEnchant(Enchantment.DURABILITY, 1, true);
            meta2.addEnchant(Enchantment.PROTECTION_ENVIRONMENTAL, 1, true);
            meta2.setUnbreakable(true);
            item2.setItemMeta(meta2);
            player.getInventory().addItem(item2);

            ItemStack item3 = new ItemStack(itemgoldMaterial3);
            ItemMeta meta3 = item3.getItemMeta();
            assert meta3 != null;
            meta3.setDisplayName(itemgoldName3 + "5");
            List<String> lore3 = new ArrayList<>();
            lore3.add(itemLore2);
            meta3.setLore(lore3);
            meta3.addEnchant(Enchantment.DURABILITY, 1, true);
            meta3.addEnchant(Enchantment.PROTECTION_ENVIRONMENTAL, 1, true);
            meta3.setUnbreakable(true);
            item3.setItemMeta(meta3);
            player.getInventory().addItem(item3);

            ItemStack item4 = new ItemStack(itemgoldMaterial4);
            ItemMeta meta4 = item4.getItemMeta();
            assert meta4 != null;
            meta4.setDisplayName(itemgoldName4 + "5");
            List<String> lore4 = new ArrayList<>();
            lore4.add(itemLore3);
            meta4.setLore(lore4);
            meta4.addEnchant(Enchantment.DURABILITY, 1, true);
            meta4.addEnchant(Enchantment.PROTECTION_ENVIRONMENTAL, 1, true);
            meta4.setUnbreakable(true);
            item4.setItemMeta(meta4);
            player.getInventory().addItem(item4);
            player.sendMessage(itemarrmorMesage + "5");
            return true;
        }
    }

    public static class gold2 implements CommandExecutor, Listener {

        public gold2() {
            Objects.requireNonNull(Bukkit.getPluginCommand("gold2")).setExecutor(this);
        }

        @Override
        public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(console);
                return false;
            }

            Player player = (Player) sender;
            ItemStack item = new ItemStack(itemgoldMaterial1);
            ItemMeta meta = item.getItemMeta();
            assert meta!= null;
            meta.setDisplayName(itemgoldName1 + "6");
            List<String> lore = new ArrayList<>();
            lore.add(itemLore1);
            meta.setLore(lore);
            meta.addEnchant(Enchantment.DURABILITY, 2, true);
            meta.addEnchant(Enchantment.PROTECTION_ENVIRONMENTAL, 2, true);
            meta.setUnbreakable(true);
            item.setItemMeta(meta);
            player.getInventory().addItem(item);

            ItemStack item2 = new ItemStack(itemgoldrMaterial2);
            ItemMeta meta2 = item2.getItemMeta();
            assert meta2!= null;
            meta2.setDisplayName(itemgoldName2 + "6");
            List<String> lore2 = new ArrayList<>();
            lore2.add(itemLore4);
            meta2.setLore(lore2);
            meta2.addEnchant(Enchantment.DURABILITY, 2, true);
            meta2.addEnchant(Enchantment.PROTECTION_ENVIRONMENTAL, 2, true);
            meta2.setUnbreakable(true);
            item2.setItemMeta(meta2);
            player.getInventory().addItem(item2);

            ItemStack item3 = new ItemStack(itemgoldMaterial3);
            ItemMeta meta3 = item3.getItemMeta();
            assert meta3!= null;
            meta3.setDisplayName(itemgoldName3 + "6");
            List<String> lore3 = new ArrayList<>();
            lore3.add(itemLore2);
            meta3.setLore(lore3);
            meta3.addEnchant(Enchantment.DURABILITY, 2, true);
            meta3.addEnchant(Enchantment.PROTECTION_ENVIRONMENTAL, 2, true);
            meta3.setUnbreakable(true);
            item3.setItemMeta(meta3);
            player.getInventory().addItem(item3);

            ItemStack item4 = new ItemStack(itemgoldMaterial4);
            ItemMeta meta4 = item4.getItemMeta();
            assert meta4!= null;
            meta4.setDisplayName(itemgoldName4 + "6");
            List<String> lore4 = new ArrayList<>();
            lore4.add(itemLore3);
            meta4.setLore(lore4);
            meta4.addEnchant(Enchantment.DURABILITY, 2, true);
            meta4.addEnchant(Enchantment.PROTECTION_ENVIRONMENTAL, 2, true);
            meta4.setUnbreakable(true);
            item4.setItemMeta(meta4);
            player.getInventory().addItem(item4);
            player.sendMessage(itemarrmorMesage + "6");
            return true;

        }
    }

    public static class gold3 implements CommandExecutor, Listener {
        public gold3() {
            Objects.requireNonNull(Bukkit.getPluginCommand("gold3")).setExecutor(this);
        }

        @Override
        public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(console);
                return false;
            }

            Player player = (Player) sender;
            ItemStack item = new ItemStack(itemgoldMaterial1);
            ItemMeta meta = item.getItemMeta();
            assert meta!= null;
            meta.setDisplayName(itemgoldName1 + "7");
            List<String> lore = new ArrayList<>();
            lore.add(itemLore1);
            meta.setLore(lore);
            meta.addEnchant(Enchantment.DURABILITY, 3, true);
            meta.addEnchant(Enchantment.PROTECTION_ENVIRONMENTAL, 3, true);
            meta.setUnbreakable(true);
            item.setItemMeta(meta);
            player.getInventory().addItem(item);

            ItemStack item2 = new ItemStack(itemgoldrMaterial2);
            ItemMeta meta2 = item2.getItemMeta();
            assert meta2!= null;
            meta2.setDisplayName(itemgoldName2 + "7");
            List<String> lore2 = new ArrayList<>();
            lore2.add(itemLore4);
            meta2.setLore(lore2);
            meta2.addEnchant(Enchantment.DURABILITY, 3, true);
            meta2.addEnchant(Enchantment.PROTECTION_ENVIRONMENTAL, 3, true);
            meta2.setUnbreakable(true);
            item2.setItemMeta(meta2);
            player.getInventory().addItem(item2);

            ItemStack item3 = new ItemStack(itemgoldMaterial3);
            ItemMeta meta3 = item3.getItemMeta();
            assert meta3!= null;
            meta3.setDisplayName(itemgoldName3 + "7");
            List<String> lore3 = new ArrayList<>();
            lore3.add(itemLore2);
            meta3.setLore(lore3);
            meta3.addEnchant(Enchantment.DURABILITY, 3, true);
            meta3.addEnchant(Enchantment.PROTECTION_ENVIRONMENTAL, 3, true);
            meta3.setUnbreakable(true);
            item3.setItemMeta(meta3);
            player.getInventory().addItem(item3);

            ItemStack item4 = new ItemStack(itemgoldMaterial);
            ItemMeta meta4 = item4.getItemMeta();
            assert meta4!= null;
            meta4.setDisplayName(itemgoldName4 + "7");
            List<String> lore4 = new ArrayList<>();
            lore4.add(itemLore4);
            meta4.setLore(lore4);
            meta4.addEnchant(Enchantment.DURABILITY, 3, true);
            meta4.addEnchant(Enchantment.PROTECTION_ENVIRONMENTAL, 3, true);
            meta4.setUnbreakable(true);
            item4.setItemMeta(meta4);
            player.getInventory().addItem(item4);
            player.sendMessage(itemarrmorMesage + "7");
            return true;
        }
    }

    public static class gold4 implements CommandExecutor, Listener {

        public gold4() {
            Objects.requireNonNull(Bukkit.getPluginCommand("gold4")).setExecutor(this);
        }

        @Override
        public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(console);
                return false;
            }

            Player player = (Player) sender;
            ItemStack item = new ItemStack(itemgoldMaterial1);
            ItemMeta meta = item.getItemMeta();
            assert meta!= null;
            meta.setDisplayName(itemgoldName1 + "8");
            List<String> lore = new ArrayList<>();
            lore.add(itemLore1);
            meta.setLore(lore);
            meta.addEnchant(Enchantment.DURABILITY, 4, true);
            meta.addEnchant(Enchantment.PROTECTION_ENVIRONMENTAL, 4, true);
            meta.setUnbreakable(true);
            item.setItemMeta(meta);
            player.getInventory().addItem(item);

            ItemStack item2 = new ItemStack(itemgoldrMaterial2);
            ItemMeta meta2 = item2.getItemMeta();
            assert meta2!= null;
            meta.setDisplayName(itemgoldName2 + "8");
            List<String> lore2 = new ArrayList<>();
            lore2.add(itemLore4);
            meta2.setLore(lore2);
            meta2.addEnchant(Enchantment.DURABILITY, 4, true);
            meta2.addEnchant(Enchantment.PROTECTION_ENVIRONMENTAL, 4, true);
            meta2.setUnbreakable(true);
            item2.setItemMeta(meta2);
            player.getInventory().addItem(item2);

            ItemStack item3 = new ItemStack(itemgoldMaterial3);
            ItemMeta meta3 = item3.getItemMeta();
            assert meta3!= null;
            meta3.setDisplayName(itemgoldName3 + "8");
            List<String> lore3 = new ArrayList<>();
            lore3.add(itemLore2);
            meta3.setLore(lore3);
            meta3.addEnchant(Enchantment.DURABILITY, 4, true);
            meta3.addEnchant(Enchantment.PROTECTION_ENVIRONMENTAL, 4, true);
            meta3.setUnbreakable(true);
            item3.setItemMeta(meta3);
            player.getInventory().addItem(item3);

            ItemStack item4 = new ItemStack(itemgoldMaterial);
            ItemMeta meta4 = item4.getItemMeta();
            assert meta4!= null;
            meta4.setDisplayName(itemgoldName4 + "8");
            List<String> lore4 = new ArrayList<>();
            lore4.add(itemLore4);
            meta4.setLore(lore4);
            meta4.addEnchant(Enchantment.DURABILITY, 4, true);
            meta4.addEnchant(Enchantment.PROTECTION_ENVIRONMENTAL, 4, true);
            meta4.setUnbreakable(true);
            item4.setItemMeta(meta4);
            player.getInventory().addItem(item4);
            player.sendMessage(itemarrmorMesage + "8");

            return true;
        }
    }
}
