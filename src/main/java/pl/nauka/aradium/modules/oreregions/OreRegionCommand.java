package pl.nauka.aradium.modules.oreregions;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import pl.nauka.aradium.AradiumPlugin;
import pl.nauka.aradium.utils.ChatUtils;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class OreRegionCommand implements TabExecutor {

    private static final List<String> CORNERS = List.of("min", "max");
    private static final List<String> SUGGESTED_ORES = Arrays.stream(Material.values())
            .filter(Material::isBlock)
            .map(Material::name)
            .filter(name -> name.endsWith("_ORE")
                    || name.equals("ANCIENT_DEBRIS")
                    || (name.startsWith("RAW_") && name.endsWith("_BLOCK")))
            .sorted()
            .toList();

    private final AradiumPlugin plugin;
    private final OreRegionModule module;

    public OreRegionCommand(AradiumPlugin plugin, OreRegionModule module) {
        this.plugin = plugin;
        this.module = module;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatUtils.getMessage(plugin.getConfig(), "messages.player_only", "&cKomenda tylko dla graczy!"));
            return true;
        }

        if (!player.hasPermission("aradium.admin")) {
            player.sendMessage(ChatUtils.getMessage(plugin.getConfig(), "messages.no_permission", "&cNie masz uprawnień do tego!"));
            return true;
        }

        if (!module.isEnabled()) {
            player.sendMessage(ChatUtils.color("&cModuł stref rud jest obecnie wyłączony!"));
            return true;
        }

        if (args.length < 2) {
            player.sendMessage(ChatUtils.getMessage(plugin.getConfig(), "messages.region_usage", "&cUżycie: /oreregion <ruda> <min|max> [strefa]"));
            return true;
        }

        Material oreType = Material.matchMaterial(args[0]);
        if (oreType == null || !oreType.isBlock() || oreType.isAir()) {
            String msg = ChatUtils.getMessage(
                    plugin.getConfig(),
                    "messages.invalid_ore",
                    "&cRuda %ore% nie istnieje lub nie jest blokiem! (Wpisz np. DIAMOND_ORE)"
            ).replace("%ore%", args[0]);
            player.sendMessage(msg);
            return true;
        }

        String corner = args[1].toLowerCase(Locale.ROOT);
        String zoneName = args.length >= 3 ? args[2] : "default";

        if ("min".equals(corner)) {
            if (module.setMin(oreType, player.getLocation(), zoneName)) {
                String msg = ChatUtils.getMessage(
                        plugin.getConfig(),
                        "messages.region_min_set",
                        "&aPomyślnie ustawiono punkt MIN dla rudy: %ore%!"
                ).replace("%ore%", oreType.name());
                player.sendMessage(msg);
            }
        } else if ("max".equals(corner)) {
            if (module.setMax(oreType, player.getLocation(), zoneName)) {
                String msg = ChatUtils.getMessage(
                        plugin.getConfig(),
                        "messages.region_max_set",
                        "&aPomyślnie ustawiono punkt MAX dla rudy: %ore%!"
                ).replace("%ore%", oreType.name());
                player.sendMessage(msg);
            }
        } else {
            player.sendMessage(ChatUtils.getMessage(plugin.getConfig(), "messages.region_usage", "&cUżycie: /oreregion <ruda> <min|max> [strefa]"));
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("aradium.admin") || !module.isEnabled()) {
            return List.of();
        }

        return switch (args.length) {
            case 1 -> {
                String prefix = args[0].toUpperCase(Locale.ROOT);
                yield SUGGESTED_ORES.stream()
                        .filter(name -> name.startsWith(prefix))
                        .toList();
            }
            case 2 -> {
                String prefix = args[1].toLowerCase(Locale.ROOT);
                yield CORNERS.stream()
                        .filter(corner -> corner.startsWith(prefix))
                        .toList();
            }
            case 3 -> {
                String prefix = args[2].toLowerCase(Locale.ROOT);
                Set<String> zones = new LinkedHashSet<>();
                zones.add("default");
                Material oreType = Material.matchMaterial(args[0]);
                if (oreType != null && oreType.isBlock()) {
                    zones.addAll(module.getZoneNames(oreType));
                }
                yield zones.stream()
                        .filter(zone -> zone.startsWith(prefix))
                        .toList();
            }
            default -> List.of();
        };
    }
}
