package pl.nauka.aradium.modules.economy;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import pl.nauka.aradium.AradiumPlugin;
import pl.nauka.aradium.utils.ChatUtils;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Komenda administracyjna /eco <give|take|set> <gracz> <kwota>.
 */
public class EcoAdminCommand implements TabExecutor {

    private static final List<String> SUBCOMMANDS = List.of("give", "take", "set");

    private final AradiumPlugin plugin;
    private final EconomyModule module;

    public EcoAdminCommand(AradiumPlugin plugin, EconomyModule module) {
        this.plugin = plugin;
        this.module = module;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("aradium.admin")) {
            sender.sendMessage(ChatUtils.getMessage(plugin.getConfig(), "messages.no_permission", "&cNie masz uprawnień do tego!"));
            return true;
        }

        if (!module.isEnabled() || module.getEconomyManager() == null) {
            sender.sendMessage(ChatUtils.getMessage(plugin.getConfig(), "messages.economy_disabled", "&cModuł ekonomii jest aktualnie wyłączony!"));
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(ChatUtils.getMessage(plugin.getConfig(), "messages.eco_usage", "&cUżycie: /eco <give|take|set> <gracz> <kwota>"));
            return true;
        }

        String action = args[0].toLowerCase(Locale.ROOT);
        if (!SUBCOMMANDS.contains(action)) {
            sender.sendMessage(ChatUtils.getMessage(plugin.getConfig(), "messages.eco_usage", "&cUżycie: /eco <give|take|set> <gracz> <kwota>"));
            return true;
        }

        Player onlineTarget = Bukkit.getPlayerExact(args[1]);
        if (onlineTarget == null) {
            onlineTarget = Bukkit.getPlayer(args[1]);
        }

        UUID targetId;
        String targetName;
        if (onlineTarget != null) {
            targetId = onlineTarget.getUniqueId();
            targetName = onlineTarget.getName();
        } else {
            OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(args[1]);
            if (cached == null || !cached.hasPlayedBefore()) {
                sender.sendMessage(ChatUtils.getMessage(plugin.getConfig(), "messages.player_not_found", "&cNie znaleziono gracza!"));
                return true;
            }
            targetId = cached.getUniqueId();
            targetName = cached.getName() != null ? cached.getName() : args[1];
        }

        double normalized = parseAmount(args[2]);
        boolean invalidAmount = "set".equals(action) ? normalized < 0.0 : normalized <= 0.0;
        if (invalidAmount) {
            sender.sendMessage(ChatUtils.getMessage(plugin.getConfig(), "messages.pay_invalid_amount", "&cKwota musi być liczbą dodatnią!"));
            return true;
        }

        EconomyManager eco = module.getEconomyManager();
        String formattedAmount = EconomyManager.formatMoney(normalized);

        switch (action) {
            case "give" -> {
                eco.addMoney(targetId, normalized);
                sender.sendMessage(ChatUtils.getMessage(
                                plugin.getConfig(),
                                "messages.eco_given",
                                "&aDodano &e%amount% &ado konta gracza &f%player% &8(&7Nowe saldo: &a%balance%&8)&a."
                        )
                        .replace("%amount%", formattedAmount)
                        .replace("%player%", targetName)
                        .replace("%balance%", EconomyManager.formatMoney(eco.getMoney(targetId))));
            }
            case "take" -> {
                if (!eco.takeMoney(targetId, normalized)) {
                    sender.sendMessage(ChatUtils.getMessage(
                                    plugin.getConfig(),
                                    "messages.eco_insufficient_funds",
                                    "&cGracz &f%player% &cnie posiada wystarczających środków (%amount%)!"
                            )
                            .replace("%player%", targetName)
                            .replace("%amount%", formattedAmount));
                    return true;
                }
                sender.sendMessage(ChatUtils.getMessage(
                                plugin.getConfig(),
                                "messages.eco_taken",
                                "&aPobrano &e%amount% &az konta gracza &f%player% &8(&7Nowe saldo: &a%balance%&8)&a."
                        )
                        .replace("%amount%", formattedAmount)
                        .replace("%player%", targetName)
                        .replace("%balance%", EconomyManager.formatMoney(eco.getMoney(targetId))));
            }
            case "set" -> {
                eco.setMoney(targetId, normalized);
                sender.sendMessage(ChatUtils.getMessage(
                                plugin.getConfig(),
                                "messages.eco_set",
                                "&aUstawiono saldo gracza &f%player% &ana &e%balance%&a."
                        )
                        .replace("%player%", targetName)
                        .replace("%balance%", EconomyManager.formatMoney(eco.getMoney(targetId))));
            }
            default -> sender.sendMessage(ChatUtils.getMessage(plugin.getConfig(), "messages.eco_usage", "&cUżycie: /eco <give|take|set> <gracz> <kwota>"));
        }
        return true;
    }

    private static double parseAmount(String raw) {
        try {
            double parsed = Double.parseDouble(raw.replace(',', '.'));
            return EconomyManager.normalizeAmount(parsed);
        } catch (NumberFormatException e) {
            return -1.0;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("aradium.admin") || !module.isEnabled()) {
            return List.of();
        }
        return switch (args.length) {
            case 1 -> {
                String prefix = args[0].toLowerCase(Locale.ROOT);
                yield SUBCOMMANDS.stream()
                        .filter(sub -> sub.startsWith(prefix))
                        .toList();
            }
            case 2 -> {
                String prefix = args[1].toLowerCase(Locale.ROOT);
                yield Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                        .toList();
            }
            case 3 -> {
                String prefix = args[2];
                yield List.of("100", "1000", "10000").stream()
                        .filter(val -> val.startsWith(prefix))
                        .toList();
            }
            default -> List.of();
        };
    }
}
