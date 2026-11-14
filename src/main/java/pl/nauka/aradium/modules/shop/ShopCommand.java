package pl.nauka.aradium.modules.shop;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import pl.nauka.aradium.AradiumPlugin;
import pl.nauka.aradium.utils.ChatUtils;

import java.util.List;
import java.util.Locale;

/**
 * Komenda /shop [kategoria] otwierająca GUI sklepu serwerowego.
 */
public class ShopCommand implements TabExecutor {

    private final AradiumPlugin plugin;
    private final ShopModule module;

    public ShopCommand(AradiumPlugin plugin, ShopModule module) {
        this.plugin = plugin;
        this.module = module;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!module.isEnabled() || module.getShopGUI() == null) {
            sender.sendMessage(ChatUtils.getMessage(plugin.getConfig(), "messages.shop_disabled", "&cModuł sklepu jest aktualnie wyłączony!"));
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

        String categoryId = args.length >= 1 ? args[0].toLowerCase(Locale.ROOT) : "main";
        module.getShopGUI().openCategory(player, categoryId);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!module.isEnabled() || module.getShopManager() == null || !sender.hasPermission("aradium.player") || args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return module.getShopManager().getCategoryIds().stream()
                .filter(id -> id.startsWith(prefix))
                .sorted()
                .toList();
    }
}
