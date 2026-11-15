package pl.nauka.aradium.modules.spawners;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import pl.nauka.aradium.AradiumPlugin;
import pl.nauka.aradium.utils.ChatUtils;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * Komenda administracyjna /spawner give <gracz> <mob> [poziom].
 */
public class SpawnerCommand implements TabExecutor {

    private final AradiumPlugin plugin;
    private final SpawnerModule module;

    public SpawnerCommand(AradiumPlugin plugin, SpawnerModule module) {
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
            sender.sendMessage(ChatUtils.getMessage(plugin.getConfig(), "messages.spawner_disabled", "&cModuł spawnerów jest aktualnie wyłączony!"));
            return true;
        }

        if (args.length < 3 || !"give".equalsIgnoreCase(args[0])) {
            sender.sendMessage(ChatUtils.getMessage(plugin.getConfig(), "messages.spawner_usage", "&cUżycie: /spawner give <gracz> <mob> [poziom]"));
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

        EntityType entityType = SpawnerDataManager.parseEntityType(args[2]);
        if (entityType == null || !SpawnerDataManager.isSpawnableEntity(entityType) || !module.isAllowedEntity(entityType)) {
            sender.sendMessage(ChatUtils.getMessage(
                            plugin.getConfig(),
                            "messages.spawner_invalid_entity",
                            "&cNieprawidłowy lub niedozwolony typ moba: &f%entity%&c!"
                    )
                    .replace("%entity%", args[2]));
            return true;
        }

        int maxLevel = Math.max(1, module.getMaxTrackLevel(SpawnerUpgradeTrack.DELAY));
        int level = 1;
        if (args.length >= 4) {
            try {
                level = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatUtils.getMessage(
                                plugin.getConfig(),
                                "messages.spawner_invalid_tier",
                                "&cNieprawidłowy poziom spawnera (1-" + maxLevel + "): &f%tier%&c!"
                        )
                        .replace("%tier%", args[3]));
                return true;
            }
        }

        if (level < 1 || level > maxLevel) {
            sender.sendMessage(ChatUtils.getMessage(
                            plugin.getConfig(),
                            "messages.spawner_invalid_tier",
                            "&cNieprawidłowy poziom spawnera (1-" + maxLevel + "): &f%tier%&c!"
                    )
                    .replace("%tier%", String.valueOf(level)));
            return true;
        }

        int maxAmount = Math.max(1, module.getMaxTrackLevel(SpawnerUpgradeTrack.AMOUNT));
        int maxRange = Math.max(1, module.getMaxTrackLevel(SpawnerUpgradeTrack.RANGE));
        int lightLvl = level >= 3 ? Math.min(1, module.getMaxTrackLevel(SpawnerUpgradeTrack.LIGHT_IMMUNITY)) : 0;
        int groundLvl = level >= 4 ? Math.min(1, module.getMaxTrackLevel(SpawnerUpgradeTrack.GROUND_IMMUNITY)) : 0;
        SpawnerState state = new SpawnerState(
                entityType,
                Set.of(EntityType.PIG, entityType),
                level,
                Math.min(level, maxAmount),
                Math.min(level, maxRange),
                lightLvl,
                groundLvl
        );

        ItemStack spawnerItem = module.getDataManager().createSpawnerItem(state, module);
        Map<Integer, ItemStack> overflow = target.getInventory().addItem(spawnerItem);
        if (!overflow.isEmpty()) {
            for (ItemStack leftover : overflow.values()) {
                target.getWorld().dropItemNaturally(target.getLocation(), leftover);
            }
            target.sendMessage(ChatUtils.getMessage(
                    plugin.getConfig(),
                    "messages.inventory_full_dropped",
                    "&eTwój ekwipunek był pełny — część przedmiotów została upuszczona pod nogi!"
            ));
        }

        String entityDisplay = module.formatMobDisplayName(entityType);
        target.sendMessage(ChatUtils.getMessage(
                        plugin.getConfig(),
                        "messages.spawner_given",
                        "&aOtrzymałeś spawner: &e%entity% &8(&fPoziom %tier%&8)&a!"
                )
                .replace("%entity%", entityDisplay)
                .replace("%tier%", String.valueOf(level)));

        sender.sendMessage(ChatUtils.getMessage(
                        plugin.getConfig(),
                        "messages.spawner_given_sender",
                        "&aNadano spawner &e%entity% (Poziom %tier%) &agraczowi &f%player%&a."
                )
                .replace("%entity%", entityDisplay)
                .replace("%tier%", String.valueOf(level))
                .replace("%player%", target.getName()));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("aradium.admin") || !module.isEnabled()) {
            return List.of();
        }
        int maxLevel = Math.max(1, module.getMaxTrackLevel(SpawnerUpgradeTrack.DELAY));
        return switch (args.length) {
            case 1 -> "give".startsWith(args[0].toLowerCase(Locale.ROOT)) ? List.of("give") : List.of();
            case 2 -> {
                String prefix = args[1].toLowerCase(Locale.ROOT);
                yield Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                        .toList();
            }
            case 3 -> {
                String prefix = args[2].toUpperCase(Locale.ROOT);
                yield module.getAllowedEntityNames().stream()
                        .filter(name -> name.startsWith(prefix))
                        .sorted()
                        .toList();
            }
            case 4 -> {
                String prefix = args[3];
                yield IntStream.rangeClosed(1, maxLevel)
                        .mapToObj(String::valueOf)
                        .filter(t -> t.startsWith(prefix))
                        .toList();
            }
            default -> List.of();
        };
    }
}
