package pl.nauka.aradium.core;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import pl.nauka.aradium.AradiumPlugin;
import pl.nauka.aradium.utils.ChatUtils;

import java.util.List;
import java.util.Locale;

public class AradiumCommand implements TabExecutor {

    private static final List<String> SUBCOMMANDS = List.of("reload", "modules");
    private final AradiumPlugin plugin;

    public AradiumCommand(AradiumPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("aradium.admin")) {
            sender.sendMessage(ChatUtils.getMessage(plugin.getConfig(), "messages.no_permission", "&cNie masz uprawnień do tego!"));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(ChatUtils.color("&cUżycie: /aradium <reload|modules>"));
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                plugin.reloadConfig();
                plugin.getModuleManager().reloadAll();
                sender.sendMessage(ChatUtils.getMessage(
                        plugin.getConfig(),
                        "messages.reload_success",
                        "&aKonfiguracja i moduły Aradium zostały pomyślnie przeładowane!"
                ));
            }
            case "modules" -> {
                sender.sendMessage(ChatUtils.getMessage(
                        plugin.getConfig(),
                        "messages.module_list_header",
                        "&6Moduły Aradium:"
                ));
                for (PluginModule module : plugin.getModuleManager().getModules()) {
                    String status = module.isEnabled() ? "&aWŁĄCZONY" : "&cWYŁĄCZONY";
                    sender.sendMessage(ChatUtils.color("&7 - &e" + module.getId() + "&7: " + status));
                }
            }
            default -> sender.sendMessage(ChatUtils.color("&cUżycie: /aradium <reload|modules>"));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("aradium.admin") || args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return SUBCOMMANDS.stream()
                .filter(sub -> sub.startsWith(prefix))
                .toList();
    }
}
