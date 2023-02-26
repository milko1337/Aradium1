package nauka1.nauka.effect.comand;

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

import static nauka1.nauka.imports.general.console;
import static nauka1.nauka.imports.getmesage.itemheisMesage;
import static nauka1.nauka.imports.lore.itemheisLore;
import static nauka1.nauka.imports.material.itemheistMaterial;
import static nauka1.nauka.imports.name.itemheistName;

public class heist {

    public static class heist1 implements CommandExecutor, Listener {

        public heist1() {
            Objects.requireNonNull(Bukkit.getPluginCommand("heist1")).setExecutor(this);}

        @Override
        public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(console);
                return false;
            }

            Player player = (Player) sender;
            ItemStack item = new ItemStack(itemheistMaterial);
            ItemMeta meta = item.getItemMeta();
            assert meta != null;
            meta.setDisplayName(itemheistName + "1");
            List<String> lore = new ArrayList<>();
            lore.add(itemheisLore);
            meta.setLore(lore);
            meta.addEnchant(Enchantment.DURABILITY, 1, true);
            item.setItemMeta(meta);
            player.getInventory().addItem(item);
            player.sendMessage(itemheisMesage + "1");
            return true;

        }

    }

    public static class heist2 implements CommandExecutor, Listener {

        public heist2() {Objects.requireNonNull(Bukkit.getPluginCommand("heist2")).setExecutor(this);}

        @Override
        public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(console);
                return false;
            }

            Player player = (Player) sender;
            ItemStack item = new ItemStack(itemheistMaterial);
            ItemMeta meta = item.getItemMeta();
            assert meta != null;
            meta.setDisplayName(itemheistName + "2");
            List<String> lore = new ArrayList<>();
            lore.add(itemheisLore);
            meta.setLore(lore);
            meta.addEnchant(Enchantment.DURABILITY, 2, true);
            item.setItemMeta(meta);
            player.getInventory().addItem(item);
            player.sendMessage(itemheisMesage + "2");
            return true;

        }

    }

    public static class heist3 implements CommandExecutor, Listener {

        public heist3() {Objects.requireNonNull(Bukkit.getPluginCommand("heist3")).setExecutor(this);}

        @Override
        public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(console);
                return false;
            }

            Player player = (Player) sender;
            ItemStack item = new ItemStack(itemheistMaterial);
            ItemMeta meta = item.getItemMeta();
            assert meta != null;
            meta.setDisplayName(itemheistName + "3");
            List<String> lore = new ArrayList<>();
            lore.add(itemheisLore);
            meta.setLore(lore);
            meta.addEnchant(Enchantment.DURABILITY, 3, true);
            item.setItemMeta(meta);
            player.getInventory().addItem(item);
            player.sendMessage(itemheisMesage + "3");
            return true;

        }

    }

    public static class heist4 implements CommandExecutor, Listener {

        public heist4() {Objects.requireNonNull(Bukkit.getPluginCommand("heist4")).setExecutor(this);}

        @Override
        public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(console);
                return false;
            }

            Player player = (Player) sender;
            ItemStack item = new ItemStack(itemheistMaterial);
            ItemMeta meta = item.getItemMeta();
            assert meta != null;
            meta.setDisplayName(itemheistName + "4");
            List<String> lore = new ArrayList<>();
            lore.add(itemheisLore);
            meta.setLore(lore);
            meta.addEnchant(Enchantment.DURABILITY, 4, true);
            item.setItemMeta(meta);
            player.getInventory().addItem(item);
            player.sendMessage(itemheisMesage + "4");
            return true;

        }

    }
}
