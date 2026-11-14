package pl.nauka.aradium.modules.magicitems;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import pl.nauka.aradium.AradiumPlugin;
import pl.nauka.aradium.modules.armor.ArmorCommand;
import pl.nauka.aradium.modules.armor.ArmorModule;
import pl.nauka.aradium.modules.armor.ArmorSetDefinition;
import pl.nauka.aradium.modules.armor.SpecialArmorDefinition;
import pl.nauka.aradium.utils.ChatUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class MagicItemCommand implements TabExecutor {

    private static final List<String> SUGGESTED_LEVELS = List.of("1", "2", "3", "5");

    private final AradiumPlugin plugin;
    private final MagicItemModule module;

    public MagicItemCommand(AradiumPlugin plugin, MagicItemModule module) {
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
            sender.sendMessage(ChatUtils.getMessage(plugin.getConfig(), "messages.invalid_item_type", "&cModuł magicznych przedmiotów jest obecnie wyłączony!"));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatUtils.getMessage(plugin.getConfig(), "messages.magicitem_usage", "&cUżycie: /magicitem <gracz> <typ> <poziom>"));
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

        String type = args[1].toLowerCase(Locale.ROOT);
        if (tryGiveSpecialArmor(sender, target, type)) {
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(ChatUtils.getMessage(plugin.getConfig(), "messages.magicitem_usage", "&cUżycie: /magicitem <gracz> <typ> <poziom>"));
            return true;
        }

        int level;
        try {
            level = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatUtils.getMessage(plugin.getConfig(), "messages.invalid_level", "&cPoziom musi być liczbą całkowitą z zakresu 1-255!"));
            return true;
        }

        if (level < 1 || level > 255) {
            sender.sendMessage(ChatUtils.getMessage(plugin.getConfig(), "messages.invalid_level", "&cPoziom musi być liczbą całkowitą z zakresu 1-255!"));
            return true;
        }

        ItemStack item = module.createMagicItem(type, level);
        if (item == null) {
            if (tryGiveArmorSet(sender, target, type, level)) {
                return true;
            }
            sender.sendMessage(ChatUtils.getMessage(plugin.getConfig(), "messages.invalid_item_type", "&cTaki typ przedmiotu nie istnieje w config.yml!"));
            return true;
        }

        ArmorCommand.giveItems(plugin, target, List.of(item));

        String givenMsg = ChatUtils.getMessage(
                        plugin.getConfig(),
                        "messages.magicitem_given",
                        "&aOtrzymałeś magiczny przedmiot: %item% (poziom %level%)!"
                )
                .replace("%item%", type)
                .replace("%level%", String.valueOf(level));
        target.sendMessage(givenMsg);

        String senderMsg = ChatUtils.getMessage(
                        plugin.getConfig(),
                        "messages.item_given_sender",
                        "&aPomyślnie nadano przedmiot %item% (poziom %level%) graczowi %player%."
                )
                .replace("%item%", type)
                .replace("%level%", String.valueOf(level))
                .replace("%player%", target.getName());
        sender.sendMessage(senderMsg);
        return true;
    }

    private boolean tryGiveSpecialArmor(CommandSender sender, Player target, String type) {
        Optional<ArmorModule> armorOpt = plugin.getArmorModule().filter(ArmorModule::isEnabled);
        if (armorOpt.isEmpty()) {
            return false;
        }
        ArmorModule armorModule = armorOpt.get();
        SpecialArmorDefinition specialDef = armorModule.getSpecialDefinition(type);
        if (specialDef == null) {
            return false;
        }
        return ArmorCommand.giveSpecialArmor(plugin, sender, target, armorModule, specialDef);
    }

    private boolean tryGiveArmorSet(CommandSender sender, Player target, String type, int tier) {
        Optional<ArmorModule> armorOpt = plugin.getArmorModule();
        if (armorOpt.isEmpty() || !armorOpt.get().isEnabled()) {
            return false;
        }
        ArmorModule armorModule = armorOpt.get();
        ArmorSetDefinition def = armorModule.getDefinition(type);
        if (def == null) {
            return false;
        }
        if (def.getTier(tier) == null) {
            sender.sendMessage(ChatUtils.color("&cZestaw &f" + def.displayName() + " &cnie posiada tieru &f" + tier + "&c!"));
            return true;
        }
        List<ItemStack> fullSet = armorModule.createFullSet(type, tier);
        if (fullSet.isEmpty()) {
            return false;
        }

        ArmorCommand.giveItems(plugin, target, fullSet);
        target.sendMessage(ChatUtils.getMessage(
                        plugin.getConfig(),
                        "messages.magicarmor_given",
                        "&aOtrzymałeś magiczną zbroję: &e%set% &8(&fTier %tier%&8)&a!"
                )
                .replace("%set%", def.displayName())
                .replace("%tier%", String.valueOf(tier)));
        sender.sendMessage(ChatUtils.getMessage(
                        plugin.getConfig(),
                        "messages.magicarmor_given_sender",
                        "&aPomyślnie nadano zbroję &e%set% (Tier %tier%) &agraczowi &f%player%&a."
                )
                .replace("%set%", def.displayName())
                .replace("%tier%", String.valueOf(tier))
                .replace("%player%", target.getName()));
        return true;
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
                module.getItemIds().stream()
                        .filter(id -> id.startsWith(prefix))
                        .forEach(suggestions::add);
                plugin.getArmorModule()
                        .filter(ArmorModule::isEnabled)
                        .ifPresent(armorModule -> {
                            armorModule.getSetIds().stream()
                                    .filter(id -> id.startsWith(prefix) && !suggestions.contains(id))
                                    .forEach(suggestions::add);
                            armorModule.getSpecialArmorIds().stream()
                                    .filter(id -> id.startsWith(prefix) && !suggestions.contains(id))
                                    .forEach(suggestions::add);
                        });
                yield suggestions;
            }
            case 3 -> {
                String prefix = args[2];
                Optional<ArmorModule> armorOpt = plugin.getArmorModule().filter(ArmorModule::isEnabled);
                if (armorOpt.isPresent()) {
                    if (armorOpt.get().getSpecialDefinition(args[1]) != null) {
                        yield "1".startsWith(prefix) ? List.of("1") : List.of();
                    }
                    ArmorSetDefinition armorDef = armorOpt.get().getDefinition(args[1]);
                    if (armorDef != null) {
                        yield armorDef.tiers().keySet().stream()
                                .map(String::valueOf)
                                .filter(lvl -> lvl.startsWith(prefix))
                                .toList();
                    }
                }
                yield SUGGESTED_LEVELS.stream()
                        .filter(lvl -> lvl.startsWith(prefix))
                        .toList();
            }
            default -> List.of();
        };
    }
}
