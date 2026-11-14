package pl.nauka.aradium.modules.armor;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import pl.nauka.aradium.AradiumPlugin;
import pl.nauka.aradium.utils.ChatUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Komenda administracyjna /magicarmor nadająca pełne zestawy lub pojedyncze elementy magicznych zbroi.
 */
public class ArmorCommand implements TabExecutor {

    private static final List<String> PIECE_OPTIONS = List.of("all", "helmet", "chestplate", "leggings", "boots");

    private final AradiumPlugin plugin;
    private final ArmorModule module;

    public ArmorCommand(AradiumPlugin plugin, ArmorModule module) {
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
            sender.sendMessage(ChatUtils.getMessage(plugin.getConfig(), "messages.invalid_item_type", "&cModuł magicznych zbroi jest wyłączony!"));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatUtils.getMessage(
                    plugin.getConfig(),
                    "messages.magicarmor_usage",
                    "&cUżycie: /magicarmor <gracz> <zestaw|przedmiot> [tier] [all|helmet|chestplate|leggings|boots]"
            ));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            target = Bukkit.getPlayer(args[0]);
        }
        if (target == null) {
            sender.sendMessage(ChatUtils.getMessage(plugin.getConfig(), "messages.player_not_found", "&cNie znaleziono gracza!"));
            return true;
        }

        String setId = args[1].toLowerCase(Locale.ROOT);
        SpecialArmorDefinition specialDef = module.getSpecialDefinition(setId);
        if (specialDef != null) {
            return giveSpecialArmor(plugin, sender, target, module, specialDef);
        }

        if (args.length < 3) {
            sender.sendMessage(ChatUtils.getMessage(
                    plugin.getConfig(),
                    "messages.magicarmor_usage",
                    "&cUżycie: /magicarmor <gracz> <zestaw|przedmiot> [tier] [all|helmet|chestplate|leggings|boots]"
            ));
            return true;
        }

        ArmorSetDefinition definition = module.getDefinition(setId);
        if (definition == null) {
            sender.sendMessage(ChatUtils.getMessage(plugin.getConfig(), "messages.invalid_item_type", "&cTaki zestaw zbroi nie istnieje w armor.yml!"));
            return true;
        }

        int tier;
        try {
            tier = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatUtils.getMessage(plugin.getConfig(), "messages.invalid_level", "&cTier musi być liczbą całkowitą!"));
            return true;
        }

        if (definition.getTier(tier) == null) {
            sender.sendMessage(ChatUtils.color("&cZestaw &f" + definition.displayName() + " &cnie posiada tieru &f" + tier + "&c!"));
            return true;
        }

        String partArg = args.length >= 4 ? args[3].toLowerCase(Locale.ROOT) : "all";
        List<ItemStack> itemsToGive;
        if ("all".equals(partArg)) {
            itemsToGive = module.createFullSet(setId, tier);
        } else {
            ArmorPieceType pieceType = ArmorPieceType.fromString(partArg);
            if (pieceType == null) {
                sender.sendMessage(ChatUtils.getMessage(
                        plugin.getConfig(),
                        "messages.magicarmor_usage",
                        "&cUżycie: /magicarmor <gracz> <zestaw> <tier> [all|helmet|chestplate|leggings|boots]"
                ));
                return true;
            }
            ItemStack piece = module.createArmorPiece(setId, tier, pieceType);
            itemsToGive = piece != null ? List.of(piece) : List.of();
        }

        if (itemsToGive.isEmpty()) {
            sender.sendMessage(ChatUtils.getMessage(plugin.getConfig(), "messages.invalid_item_type", "&cNie udało się utworzyć zbroi!"));
            return true;
        }

        giveItems(plugin, target, itemsToGive);

        String targetMsg = ChatUtils.getMessage(
                        plugin.getConfig(),
                        "messages.magicarmor_given",
                        "&aOtrzymałeś magiczną zbroję: &e%set% &8(&fTier %tier%&8)&a!"
                )
                .replace("%set%", definition.displayName())
                .replace("%tier%", String.valueOf(tier));
        target.sendMessage(targetMsg);

        String senderMsg = ChatUtils.getMessage(
                        plugin.getConfig(),
                        "messages.magicarmor_given_sender",
                        "&aPomyślnie nadano zbroję &e%set% (Tier %tier%) &agraczowi &f%player%&a."
                )
                .replace("%set%", definition.displayName())
                .replace("%tier%", String.valueOf(tier))
                .replace("%player%", target.getName());
        sender.sendMessage(senderMsg);
        return true;
    }

    public static boolean giveSpecialArmor(
            AradiumPlugin plugin,
            CommandSender sender,
            Player target,
            ArmorModule module,
            SpecialArmorDefinition specialDef
    ) {
        ItemStack specialItem = module.createSpecialArmor(specialDef.id());
        if (specialItem == null) {
            sender.sendMessage(ChatUtils.getMessage(plugin.getConfig(), "messages.invalid_item_type", "&cNie udało się utworzyć specjalnej zbroi!"));
            return true;
        }
        giveItems(plugin, target, List.of(specialItem));

        String cleanName = ChatUtils.color(specialDef.name());
        target.sendMessage(ChatUtils.color("&aOtrzymałeś specjalną zbroję: " + cleanName + "&a!"));
        sender.sendMessage(ChatUtils.color("&aPomyślnie nadano " + cleanName + " &agraczowi &f" + target.getName() + "&a."));
        return true;
    }

    public static void giveItems(AradiumPlugin plugin, Player target, List<ItemStack> items) {
        boolean dropped = false;
        for (ItemStack item : items) {
            Map<Integer, ItemStack> overflow = target.getInventory().addItem(item);
            if (!overflow.isEmpty()) {
                dropped = true;
                for (ItemStack leftover : overflow.values()) {
                    target.getWorld().dropItemNaturally(target.getLocation(), leftover);
                }
            }
        }
        if (dropped) {
            target.sendMessage(ChatUtils.getMessage(
                    plugin.getConfig(),
                    "messages.inventory_full_dropped",
                    "&eTwój ekwipunek był pełny — część przedmiotów została upuszczona pod nogi!"
            ));
        }
        plugin.getArmorModule().ifPresent(armorModule -> armorModule.syncPlayerArmorLore(target));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("aradium.admin") || !module.isEnabled()) {
            return List.of();
        }

        return switch (args.length) {
            case 1 -> {
                String prefix = args[0].toLowerCase(Locale.ROOT);
                yield Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                        .toList();
            }
            case 2 -> {
                String prefix = args[1].toLowerCase(Locale.ROOT);
                List<String> suggestions = new ArrayList<>();
                module.getSetIds().stream()
                        .filter(id -> id.startsWith(prefix))
                        .forEach(suggestions::add);
                module.getSpecialArmorIds().stream()
                        .filter(id -> id.startsWith(prefix) && !suggestions.contains(id))
                        .forEach(suggestions::add);
                yield suggestions;
            }
            case 3 -> {
                if (module.getSpecialDefinition(args[1]) != null) {
                    yield "1".startsWith(args[2]) ? List.of("1") : List.of();
                }
                ArmorSetDefinition def = module.getDefinition(args[1]);
                if (def == null) {
                    yield List.of("1", "2", "3", "4", "5");
                }
                String prefix = args[2];
                List<String> tiers = new ArrayList<>();
                for (Integer tier : def.tiers().keySet()) {
                    String text = String.valueOf(tier);
                    if (text.startsWith(prefix)) {
                        tiers.add(text);
                    }
                }
                yield tiers;
            }
            case 4 -> {
                String prefix = args[3].toLowerCase(Locale.ROOT);
                yield PIECE_OPTIONS.stream()
                        .filter(opt -> opt.startsWith(prefix))
                        .toList();
            }
            default -> List.of();
        };
    }
}
