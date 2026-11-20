package pl.nauka.aradium.modules.customitems;

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
 * Komenda /recipes (/receptury, /kraftingi) otwierająca przeglądarkę receptur dla graczy
 * oraz panel tworzenia/edycji receptur dla administratorów.
 */
public class CustomRecipeCommand implements TabExecutor {

    private final AradiumPlugin plugin;
    private final CustomItemModule module;

    public CustomRecipeCommand(AradiumPlugin plugin, CustomItemModule module) {
        this.plugin = plugin;
        this.module = module;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!module.isEnabled() || module.getRecipeManager() == null || module.getRecipeGUI() == null) {
            sender.sendMessage(ChatUtils.getMessage(
                    plugin.getConfig(),
                    "messages.custom_items_disabled",
                    "&cModuł specjalnych przedmiotów i receptur jest aktualnie wyłączony!"
            ));
            return true;
        }

        if (!(sender instanceof Player player)) {
            if (args.length >= 1 && "reload".equalsIgnoreCase(args[0]) && sender.hasPermission("aradium.admin")) {
                module.getRecipeManager().reload();
                sender.sendMessage(ChatUtils.color("&aPrzeładowano recipes.yml ("
                        + module.getRecipeManager().getAllRecipes().size() + " receptur)."));
                return true;
            }
            sender.sendMessage(ChatUtils.getMessage(plugin.getConfig(), "messages.player_only", "&cKomenda tylko dla graczy!"));
            return true;
        }

        if (!player.hasPermission("aradium.player")) {
            player.sendMessage(ChatUtils.getMessage(plugin.getConfig(), "messages.no_permission", "&cNie masz uprawnień do tego!"));
            return true;
        }

        CustomRecipeGUI gui = module.getRecipeGUI();
        CustomRecipeManager manager = module.getRecipeManager();

        if (args.length == 0) {
            gui.openPlayerCategories(player);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if ("view".equals(sub)) {
            if (args.length < 2) {
                player.sendMessage(ChatUtils.color("&cUżycie: /receptury view <id>"));
                return true;
            }
            gui.openPlayerRecipeView(player, args[1], null, 0);
            return true;
        }

        if (!player.hasPermission("aradium.admin")) {
            player.sendMessage(ChatUtils.getMessage(plugin.getConfig(), "messages.no_permission", "&cNie masz uprawnień do tego!"));
            return true;
        }

        switch (sub) {
            case "admin", "list" -> gui.openAdminList(player, 0);
            case "create", "new" -> {
                String desiredId = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_") : null;
                gui.openAdminEditor(player, desiredId);
            }
            case "edit", "editor" -> {
                String targetId = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : null;
                gui.openAdminEditor(player, targetId);
            }
            case "delete", "remove" -> {
                if (args.length < 2) {
                    player.sendMessage(ChatUtils.color("&cUżycie: /receptury delete <id>"));
                    return true;
                }
                if (manager.deleteRecipe(args[1])) {
                    player.sendMessage(ChatUtils.color("&eUsunięto recepturę &f" + args[1] + "&e z recipes.yml."));
                } else {
                    player.sendMessage(ChatUtils.color("&cNie znaleziono receptury: &f" + args[1] + "&c!"));
                }
            }
            case "reload" -> {
                manager.reload();
                player.sendMessage(ChatUtils.color("&aPrzeładowano recipes.yml &8(&e"
                        + manager.getAllRecipes().size() + " receptur&8)&a."));
            }
            default -> sendUsage(player);
        }
        return true;
    }

    private static void sendUsage(Player player) {
        player.sendMessage(ChatUtils.color("&6Komendy Receptur (/receptury):"));
        player.sendMessage(ChatUtils.color("&e/receptury &8- &7Otwiera menu kategorii craftingów dla graczy"));
        player.sendMessage(ChatUtils.color("&e/receptury view <id> &8- &7Podgląd konkretnej receptury"));
        player.sendMessage(ChatUtils.color("&c/receptury admin &8- &7Panel administratora (tworzenie/edycja/usuwanie)"));
        player.sendMessage(ChatUtils.color("&c/receptury create <id> | edit <id> | delete <id> | reload"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!module.isEnabled() || module.getRecipeManager() == null) {
            return List.of();
        }
        boolean isAdmin = sender.hasPermission("aradium.admin");
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> subs = new ArrayList<>();
            subs.add("view");
            if (isAdmin) {
                subs.addAll(List.of("admin", "list", "create", "edit", "delete", "reload"));
            }
            return subs.stream().filter(s -> s.startsWith(prefix)).toList();
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if ("view".equals(sub) || (isAdmin && ("edit".equals(sub) || "editor".equals(sub) || "delete".equals(sub) || "remove".equals(sub)))) {
                String prefix = args[1].toLowerCase(Locale.ROOT);
                return module.getRecipeManager().getAllRecipes().stream()
                        .map(CustomRecipeDefinition::id)
                        .filter(id -> id.startsWith(prefix))
                        .toList();
            }
        }
        return List.of();
    }
}
