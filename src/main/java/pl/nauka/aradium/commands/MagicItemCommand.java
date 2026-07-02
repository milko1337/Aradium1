package pl.nauka.aradium.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import pl.nauka.aradium.AradiumPlugin;
import pl.nauka.aradium.utils.ChatUtils;

public class MagicItemCommand implements CommandExecutor {

    private final AradiumPlugin plugin;

    public MagicItemCommand(AradiumPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("aradium.admin")) {
            sender.sendMessage(ChatUtils.color(plugin.getConfig().getString("messages.no_permission")));
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(ChatUtils.color(plugin.getConfig().getString("messages.magicitem_usage")));
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(ChatUtils.color("&cNie znaleziono gracza!"));
            return true;
        }

        String type = args[1].toLowerCase();
        int level;
        try {
            level = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatUtils.color("&cPoziom musi być liczbą!"));
            return true;
        }

        ItemStack item = plugin.getMagicItemManager().createMagicItem(type, level);
        if (item == null) {
            sender.sendMessage(ChatUtils.color("&cTaki typ przedmiotu nie istnieje w config.yml!"));
            return true;
        }

        target.getInventory().addItem(item);
        
        String msg = plugin.getConfig().getString("messages.magicitem_given");
        msg = msg.replace("%item%", type).replace("%level%", String.valueOf(level));
        target.sendMessage(ChatUtils.color(msg));

        sender.sendMessage(ChatUtils.color("&aPomyślnie nadano przedmiot."));
        return true;
    }
}
