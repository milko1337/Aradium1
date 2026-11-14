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
 * Komenda /money [gracz] wyświetlająca stan konta własnego lub wskazanego gracza.
 */
public class MoneyCommand implements TabExecutor {

    private final AradiumPlugin plugin;
    private final EconomyModule module;

    public MoneyCommand(AradiumPlugin plugin, EconomyModule module) {
        this.plugin = plugin;
        this.module = module;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!module.isEnabled() || module.getEconomyManager() == null) {
            sender.sendMessage(ChatUtils.getMessage(plugin.getConfig(), "messages.economy_disabled", "&cModuł ekonomii jest aktualnie wyłączony!"));
            return true;
        }

        if (!sender.hasPermission("aradium.player")) {
            sender.sendMessage(ChatUtils.getMessage(plugin.getConfig(), "messages.no_permission", "&cNie masz uprawnień do tego!"));
            return true;
        }

        EconomyManager eco = module.getEconomyManager();
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatUtils.getMessage(plugin.getConfig(), "messages.player_only", "&cKomenda tylko dla graczy!"));
                return true;
            }
            double balance = eco.getMoney(player.getUniqueId());
            sender.sendMessage(ChatUtils.getMessage(plugin.getConfig(), "messages.money_self", "&7Twój stan konta: &a%balance%")
                    .replace("%balance%", EconomyManager.formatMoney(balance)));
            return true;
        }

        Player onlineTarget = Bukkit.getPlayerExact(args[0]);
        if (onlineTarget == null) {
            onlineTarget = Bukkit.getPlayer(args[0]);
        }

        UUID targetId;
        String targetName;
        if (onlineTarget != null) {
            targetId = onlineTarget.getUniqueId();
            targetName = onlineTarget.getName();
        } else {
            OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(args[0]);
            if (cached == null || !cached.hasPlayedBefore()) {
                sender.sendMessage(ChatUtils.getMessage(plugin.getConfig(), "messages.player_not_found", "&cNie znaleziono gracza!"));
                return true;
            }
            targetId = cached.getUniqueId();
            targetName = cached.getName() != null ? cached.getName() : args[0];
        }

        double balance = eco.getMoney(targetId);
        sender.sendMessage(ChatUtils.getMessage(plugin.getConfig(), "messages.money_other", "&7Stan konta gracza &e%player%&7: &a%balance%")
                .replace("%player%", targetName)
                .replace("%balance%", EconomyManager.formatMoney(balance)));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!module.isEnabled() || !sender.hasPermission("aradium.player") || args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                .toList();
    }
}
