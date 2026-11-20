package pl.nauka.aradium.core;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import pl.nauka.aradium.AradiumPlugin;
import pl.nauka.aradium.utils.ChatUtils;

import java.util.List;
import java.util.Locale;

public class AradiumCommand implements TabExecutor {

    private static final List<String> SUBCOMMANDS = List.of("reload", "modules", "enable", "disable", "toggle");
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
            sender.sendMessage(ChatUtils.color("&cUżycie: /aradium <reload|modules|enable|disable|toggle> [moduł]"));
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
            case "enable" -> handleModuleSwitch(sender, args, true, false);
            case "disable" -> handleModuleSwitch(sender, args, false, false);
            case "toggle" -> handleModuleSwitch(sender, args, false, true);
            default -> sender.sendMessage(ChatUtils.color("&cUżycie: /aradium <reload|modules|enable|disable|toggle> [moduł]"));
        }
        return true;
    }

    private void handleModuleSwitch(CommandSender sender, String[] args, boolean targetState, boolean toggle) {
        if (args.length < 2) {
            sender.sendMessage(ChatUtils.color("&cUżycie: /aradium " + args[0].toLowerCase(Locale.ROOT) + " <moduł>"));
            return;
        }
        ModuleManager manager = plugin.getModuleManager();
        PluginModule module = manager.getModule(args[1]).orElse(null);
        if (module == null) {
            sender.sendMessage(ChatUtils.getMessage(
                            plugin.getConfig(),
                            "messages.module_not_found",
                            "&cNie znaleziono modułu: &f%module%&c!"
                    )
                    .replace("%module%", args[1]));
            return;
        }
        boolean nextState = toggle ? !module.isEnabled() : targetState;
        manager.setModuleEnabled(module.getId(), nextState, true);
        String statusText = nextState ? "&aWŁĄCZONY" : "&cWYŁĄCZONY";
        sender.sendMessage(ChatUtils.getMessage(
                        plugin.getConfig(),
                        "messages.module_toggled",
                        "&7Moduł &e%module% &7został ustawiony na: %status% &8(&7zapisano w config.yml&8)&7."
                )
                .replace("%module%", module.getId())
                .replace("%status%", ChatUtils.color(statusText)));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("aradium.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return SUBCOMMANDS.stream()
                    .filter(sub -> sub.startsWith(prefix))
                    .toList();
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if ("enable".equals(sub) || "disable".equals(sub) || "toggle".equals(sub)) {
                String prefix = args[1].toLowerCase(Locale.ROOT);
                return plugin.getModuleManager().getModules().stream()
                        .map(PluginModule::getId)
                        .filter(id -> id.startsWith(prefix))
                        .toList();
            }
        }
        return List.of();
    }
}
