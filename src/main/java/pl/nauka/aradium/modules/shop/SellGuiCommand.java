package pl.nauka.aradium.modules.shop;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import pl.nauka.aradium.AradiumPlugin;
import pl.nauka.aradium.utils.ChatUtils;

import java.util.List;

/**
 * Komenda /sellgui otwierająca okno szybkiej sprzedaży przedmiotów po zamknięciu.
 */
public class SellGuiCommand implements TabExecutor {

    private final AradiumPlugin plugin;
    private final ShopModule module;

    public SellGuiCommand(AradiumPlugin plugin, ShopModule module) {
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

        module.getShopGUI().openSellGui(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return List.of();
    }
}
