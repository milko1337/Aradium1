package pl.nauka.aradium.commands;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import pl.nauka.aradium.AradiumPlugin;
import pl.nauka.aradium.utils.ChatUtils;

public class OreRegionCommand implements CommandExecutor {

    private final AradiumPlugin plugin;

    public OreRegionCommand(AradiumPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatUtils.color(plugin.getConfig().getString("messages.player_only")));
            return true;
        }

        Player player = (Player) sender;
        if (!player.hasPermission("aradium.admin")) {
            player.sendMessage(ChatUtils.color(plugin.getConfig().getString("messages.no_permission")));
            return true;
        }

        if (args.length < 2) {
            player.sendMessage(ChatUtils.color(plugin.getConfig().getString("messages.region_usage")));
            return true;
        }

        Material oreType = Material.matchMaterial(args[0]);
        if (oreType == null || !oreType.isBlock()) {
            player.sendMessage(ChatUtils.color(plugin.getConfig().getString("messages.invalid_ore").replace("%ore%", args[0])));
            return true;
        }

        String type = args[1].toLowerCase();
        if (type.equals("min")) {
            plugin.getOreRegionManager().setMin(oreType, player.getLocation());
            player.sendMessage(ChatUtils.color(plugin.getConfig().getString("messages.region_min_set").replace("%ore%", oreType.name())));
        } else if (type.equals("max")) {
            plugin.getOreRegionManager().setMax(oreType, player.getLocation());
            player.sendMessage(ChatUtils.color(plugin.getConfig().getString("messages.region_max_set").replace("%ore%", oreType.name())));
        } else {
            player.sendMessage(ChatUtils.color(plugin.getConfig().getString("messages.region_usage")));
        }

        return true;
    }
}
