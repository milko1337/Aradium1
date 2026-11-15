package pl.nauka.aradium.modules.shop;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import pl.nauka.aradium.AradiumPlugin;
import pl.nauka.aradium.utils.ChatUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Komenda /sellall [inventory|hand|<material>] sprzedająca pasujące przedmioty po najwyższej cenie w sklepie.
 */
public class SellAllCommand implements TabExecutor {

    private final AradiumPlugin plugin;
    private final ShopModule module;

    public SellAllCommand(AradiumPlugin plugin, ShopModule module) {
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

        if (args.length == 0
                || args[0].equalsIgnoreCase("inventory")
                || args[0].equalsIgnoreCase("all")
                || args[0].equalsIgnoreCase("wszystko")) {
            module.getShopGUI().sellAllMatchingFromInventory(player, null, false);
            return true;
        }

        if (args[0].equalsIgnoreCase("hand") || args[0].equalsIgnoreCase("reka") || args[0].equalsIgnoreCase("ręka")) {
            module.getShopGUI().sellAllMatchingFromInventory(player, null, true);
            return true;
        }

        Material filter = Material.matchMaterial(args[0].trim().toUpperCase(Locale.ROOT));
        if (filter == null || !filter.isItem()) {
            player.sendMessage(ChatUtils.getMessage(
                    plugin.getConfig(),
                    "messages.shop_sellall_usage",
                    "&cUżycie: /sellall [inventory|hand|<material>]"
            ));
            return true;
        }

        module.getShopGUI().sellAllMatchingFromInventory(player, filter, false);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!module.isEnabled() || !sender.hasPermission("aradium.player") || args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> suggestions = new ArrayList<>();
        if ("inventory".startsWith(prefix)) {
            suggestions.add("inventory");
        }
        if ("hand".startsWith(prefix)) {
            suggestions.add("hand");
        }
        if (suggestions.size() < 20 && module.getShopManager() != null) {
            module.getShopManager().getCategories().stream()
                    .flatMap(cat -> cat.pages().stream())
                    .flatMap(page -> page.values().stream())
                    .filter(item -> !item.isNavigationItem() && item.canSell())
                    .map(item -> item.material().name().toLowerCase(Locale.ROOT))
                    .distinct()
                    .filter(matName -> matName.startsWith(prefix))
                    .sorted()
                    .limit(20)
                    .forEach(suggestions::add);
        }
        return suggestions;
    }
}
