package pl.nauka.aradium.modules.economy;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import pl.nauka.aradium.AradiumPlugin;
import pl.nauka.aradium.utils.ChatUtils;

import java.util.List;
import java.util.Locale;

/**
 * Komenda /pay <gracz> <kwota> umożliwiająca bezpieczne przelewy waluty między graczami.
 */
public class PayCommand implements TabExecutor {

    private final AradiumPlugin plugin;
    private final EconomyModule module;

    public PayCommand(AradiumPlugin plugin, EconomyModule module) {
        this.plugin = plugin;
        this.module = module;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!module.isEnabled() || module.getEconomyManager() == null) {
            sender.sendMessage(ChatUtils.getMessage(plugin.getConfig(), "messages.economy_disabled", "&cModuł ekonomii jest aktualnie wyłączony!"));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatUtils.getMessage(plugin.getConfig(), "messages.player_only", "&cKomenda tylko dla graczy!"));
            return true;
        }

        if (!player.hasPermission("aradium.player")) {
            player.sendMessage(ChatUtils.getMessage(plugin.getConfig(), "messages.no_permission", "&cNie masz uprawnień do tego!"));
            return true;
        }

        if (args.length < 2) {
            player.sendMessage(ChatUtils.getMessage(plugin.getConfig(), "messages.pay_usage", "&cUżycie: /pay <gracz> <kwota>"));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            target = Bukkit.getPlayer(args[0]);
        }
        if (target == null) {
            player.sendMessage(ChatUtils.getMessage(plugin.getConfig(), "messages.player_not_found", "&cNie znaleziono gracza!"));
            return true;
        }

        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(ChatUtils.getMessage(plugin.getConfig(), "messages.pay_self", "&cNie możesz przelać pieniędzy samemu sobie!"));
            return true;
        }

        double parsed = parsePositiveAmount(args[1]);
        if (parsed <= 0.0) {
            player.sendMessage(ChatUtils.getMessage(plugin.getConfig(), "messages.pay_invalid_amount", "&cKwota musi być liczbą dodatnią!"));
            return true;
        }

        EconomyManager eco = module.getEconomyManager();
        if (!eco.transferMoney(player.getUniqueId(), target.getUniqueId(), parsed)) {
            player.sendMessage(ChatUtils.getMessage(plugin.getConfig(), "messages.pay_insufficient_funds", "&cNie masz wystarczających środków (%required%)!")
                    .replace("%required%", EconomyManager.formatMoney(parsed)));
            return true;
        }

        String formattedAmount = EconomyManager.formatMoney(parsed);
        player.sendMessage(ChatUtils.getMessage(plugin.getConfig(), "messages.pay_sent", "&aPrzelałeś &e%amount% &agraczowi &f%player%&a.")
                .replace("%amount%", formattedAmount)
                .replace("%player%", target.getName()));
        target.sendMessage(ChatUtils.getMessage(plugin.getConfig(), "messages.pay_received", "&aOtrzymałeś &e%amount% &aod gracza &f%player%&a.")
                .replace("%amount%", formattedAmount)
                .replace("%player%", player.getName()));
        return true;
    }

    private static double parsePositiveAmount(String raw) {
        try {
            double parsed = Double.parseDouble(raw.replace(',', '.'));
            double normalized = EconomyManager.normalizeAmount(parsed);
            return normalized > 0.0 ? normalized : -1.0;
        } catch (NumberFormatException e) {
            return -1.0;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!module.isEnabled() || !sender.hasPermission("aradium.player")) {
            return List.of();
        }
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> !name.equalsIgnoreCase(sender.getName()))
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .toList();
        }
        if (args.length == 2) {
            String prefix = args[1];
            return List.of("100", "500", "1000").stream()
                    .filter(val -> val.startsWith(prefix))
                    .toList();
        }
        return List.of();
    }
}
