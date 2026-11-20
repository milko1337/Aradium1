package pl.nauka.aradium.modules.customitems;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import pl.nauka.aradium.AradiumPlugin;
import pl.nauka.aradium.modules.armor.ArmorCommand;
import pl.nauka.aradium.utils.ChatUtils;

import java.util.List;
import java.util.Locale;

/**
 * Komenda administracyjna /customitem give <gracz> <id> [ilość] nadająca specjalne przedmioty Etapu 6.
 */
public class CustomItemCommand implements TabExecutor {

    private static final List<String> SUGGESTED_AMOUNTS = List.of("1", "4", "16", "64");

    private final AradiumPlugin plugin;
    private final CustomItemModule module;

    public CustomItemCommand(AradiumPlugin plugin, CustomItemModule module) {
        this.plugin = plugin;
        this.module = module;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("aradium.admin")) {
            sender.sendMessage(ChatUtils.getMessage(plugin.getConfig(), "messages.no_permission", "&cNie masz uprawnień do tego!"));
            return true;
        }

        if (!module.isEnabled()) {
            sender.sendMessage(ChatUtils.getMessage(plugin.getConfig(), "messages.custom_items_disabled", "&cModuł specjalnych przedmiotów jest aktualnie wyłączony!"));
            return true;
        }

        if (args.length < 3 || !"give".equalsIgnoreCase(args[0])) {
            sender.sendMessage(ChatUtils.getMessage(
                    plugin.getConfig(),
                    "messages.customitem_usage",
                    "&cUżycie: /customitem give <gracz> <id> [ilość]"
            ));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            target = Bukkit.getPlayer(args[1]);
        }
        if (target == null) {
            sender.sendMessage(ChatUtils.getMessage(plugin.getConfig(), "messages.player_not_found", "&cNie znaleziono gracza!"));
            return true;
        }

        String itemId = args[2].toLowerCase(Locale.ROOT);
        int amount = 1;
        if (args.length >= 4) {
            try {
                amount = Integer.parseInt(args[3]);
            } catch (NumberFormatException ignored) {
                sender.sendMessage(ChatUtils.getMessage(plugin.getConfig(), "messages.invalid_level", "&cIlość musi być liczbą całkowitą z zakresu 1-64!"));
                return true;
            }
        }
        if (amount < 1 || amount > 64) {
            sender.sendMessage(ChatUtils.getMessage(plugin.getConfig(), "messages.invalid_level", "&cIlość musi być liczbą całkowitą z zakresu 1-64!"));
            return true;
        }

        ItemStack stack = module.createCustomItem(itemId, amount);
        if (stack == null) {
            sender.sendMessage(ChatUtils.getMessage(plugin.getConfig(), "messages.invalid_item_type", "&cTaki specjalny przedmiot nie istnieje w custom-items.yml!"));
            return true;
        }

        ArmorCommand.giveItems(plugin, target, List.of(stack));
        target.sendMessage(ChatUtils.getMessage(
                        plugin.getConfig(),
                        "messages.customitem_given",
                        "&aOtrzymałeś specjalny przedmiot: &e%item% &8(&ex%amount%&8)&a!"
                )
                .replace("%item%", itemId)
                .replace("%amount%", String.valueOf(amount)));
        sender.sendMessage(ChatUtils.getMessage(
                        plugin.getConfig(),
                        "messages.customitem_given_sender",
                        "&aNadano &e%item% x%amount% &agraczowi &f%player%&a."
                )
                .replace("%item%", itemId)
                .replace("%amount%", String.valueOf(amount))
                .replace("%player%", target.getName()));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("aradium.admin") || !module.isEnabled()) {
            return List.of();
        }
        return switch (args.length) {
            case 1 -> "give".startsWith(args[0].toLowerCase(Locale.ROOT)) ? List.of("give") : List.of();
            case 2 -> {
                String prefix = args[1].toLowerCase(Locale.ROOT);
                yield Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix))
                        .toList();
            }
            case 3 -> {
                String prefix = args[2].toLowerCase(Locale.ROOT);
                yield module.getAllCustomItemIds().stream()
                        .filter(id -> id.startsWith(prefix))
                        .sorted()
                        .toList();
            }
            case 4 -> SUGGESTED_AMOUNTS.stream()
                    .filter(a -> a.startsWith(args[3]))
                    .toList();
            default -> List.of();
        };
    }
}
