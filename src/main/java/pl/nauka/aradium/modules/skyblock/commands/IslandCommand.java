package pl.nauka.aradium.modules.skyblock.commands;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import pl.nauka.aradium.AradiumPlugin;
import pl.nauka.aradium.modules.skyblock.SkyBlockModule;
import pl.nauka.aradium.modules.skyblock.island.BorderColor;
import pl.nauka.aradium.modules.skyblock.island.Island;
import pl.nauka.aradium.modules.skyblock.island.IslandManager;
import pl.nauka.aradium.modules.skyblock.island.IslandPermission;
import pl.nauka.aradium.modules.skyblock.island.IslandRole;
import pl.nauka.aradium.modules.skyblock.island.IslandSchematic;
import pl.nauka.aradium.modules.skyblock.island.IslandWarp;
import pl.nauka.aradium.utils.ChatUtils;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Główna komenda wysp /is (/island, /wyspa) zarządzająca wszystkimi operacjami Modułu 3.
 */
public class IslandCommand implements TabExecutor {

    private static final List<String> SUBCOMMANDS = List.of(
            "create", "home", "sethome", "panel", "level", "top", "upgrades",
            "roles", "privacy", "border", "warp", "setwarp", "delwarp",
            "invite", "join", "kick", "leave", "trust", "untrust", "transfer", "delete"
    );

    private final AradiumPlugin plugin;
    private final SkyBlockModule module;

    public IslandCommand(AradiumPlugin plugin, SkyBlockModule module) {
        this.plugin = plugin;
        this.module = module;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!module.isEnabled() || module.getIslandManager() == null) {
            sender.sendMessage(ChatUtils.getMessage(plugin.getConfig(), "messages.skyblock_disabled", "&cModuł SkyBlock jest aktualnie wyłączony!"));
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

        IslandManager islandManager = module.getIslandManager();
        if (args.length == 0) {
            islandManager.getPlayerIsland(player.getUniqueId()).ifPresentOrElse(
                    island -> module.getPanelGUI().open(player, island),
                    () -> module.getSchematicGUI().open(player)
            );
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "create" -> handleCreate(player, args);
            case "home", "go" -> handleHome(player);
            case "sethome" -> handleSetHome(player);
            case "panel", "menu" -> handlePanel(player);
            case "level", "poziom" -> handleLevel(player);
            case "top", "ranking" -> module.getTopGUI().open(player);
            case "upgrades", "upgrade", "ulepszenia" -> handleUpgrades(player);
            case "roles", "role", "perms" -> handleRoles(player);
            case "privacy" -> handlePrivacy(player, args);
            case "border" -> handleBorder(player, args);
            case "warp", "warps" -> handleWarp(player, args);
            case "setwarp" -> handleSetWarp(player, args);
            case "delwarp" -> handleDelWarp(player, args);
            case "invite" -> handleInvite(player, args);
            case "join", "accept" -> handleJoin(player, args);
            case "kick" -> handleKick(player, args);
            case "leave" -> handleLeave(player);
            case "trust" -> handleTrust(player, args);
            case "untrust" -> handleUntrust(player, args);
            case "transfer" -> handleTransfer(player, args);
            case "delete", "disband" -> handleDelete(player, args);
            default -> sendUsage(player);
        }
        return true;
    }

    private void handleCreate(Player player, String[] args) {
        IslandManager manager = module.getIslandManager();
        if (manager.getPlayerIsland(player.getUniqueId()).isPresent()) {
            player.sendMessage(ChatUtils.getMessage(plugin.getConfig(), "messages.island_already_exists", "&cPosiadasz już swoją wyspę! Użyj &e/is home&c."));
            return;
        }
        if (args.length < 2) {
            module.getSchematicGUI().open(player);
            return;
        }
        IslandSchematic schematic = IslandSchematic.parse(args[1]);
        Island created = manager.createIsland(player, schematic);
        if (created != null) {
            player.sendMessage(ChatUtils.getMessage(
                            plugin.getConfig(),
                            "messages.island_created",
                            "&aPomyślnie utworzono Twoją wyspę (&e%schematic%&a)! Miłej gry!"
                    )
                    .replace("%schematic%", ChatUtils.color(schematic.getDisplayName())));
        }
    }

    private void handleHome(Player player) {
        Island island = requirePlayerIsland(player);
        if (island == null) {
            return;
        }
        Location home = island.getHomeLocation();
        if (home != null) {
            player.teleport(home);
            module.getIslandManager().refreshWorldBorder(player, true);
            player.sendMessage(ChatUtils.getMessage(plugin.getConfig(), "messages.island_teleported_home", "&aPrzeteleportowano na dom Twojej wyspy!"));
        }
    }

    private void handleSetHome(Player player) {
        Island island = requirePlayerIsland(player);
        if (island == null) {
            return;
        }
        if (!island.hasPermission(player.getUniqueId(), IslandPermission.SETHOME)) {
            player.sendMessage(ChatUtils.getMessage(plugin.getConfig(), "messages.island_no_permission", "&cNie masz uprawnień do tej akcji na tej wyspie!"));
            return;
        }
        if (!island.containsLocation(player.getLocation())) {
            player.sendMessage(ChatUtils.getMessage(plugin.getConfig(), "messages.island_home_outside", "&cDom wyspy musi znajdować się wewnątrz granic Twojej wyspy!"));
            return;
        }
        island.setHomeLocation(player.getLocation());
        module.getIslandManager().saveIsland(island);
        player.sendMessage(ChatUtils.getMessage(plugin.getConfig(), "messages.island_home_set", "&aUstawiono nową lokalizację domu wyspy (&e/is home&a)!"));
    }

    private void handlePanel(Player player) {
        Island island = requirePlayerIsland(player);
        if (island != null) {
            module.getPanelGUI().open(player, island);
        }
    }

    private void handleRoles(Player player) {
        Island island = requirePlayerIsland(player);
        if (island != null) {
            module.getRolesGUI().open(player, island, IslandRole.MEMBER);
        }
    }

    private void handlePrivacy(Player player, String[] args) {
        Island island = requirePlayerIsland(player);
        if (island == null) {
            return;
        }
        if (!island.getPlayerRole(player.getUniqueId()).isAtLeast(IslandRole.CO_OWNER)) {
            player.sendMessage(ChatUtils.getMessage(plugin.getConfig(), "messages.island_no_permission", "&cNie masz uprawnień do tej akcji na tej wyspie!"));
            return;
        }
        boolean nextPublic = args.length >= 2
                ? "public".equalsIgnoreCase(args[1]) || "publiczna".equalsIgnoreCase(args[1])
                : !island.isPublicAccess();
        island.setPublicAccess(nextPublic);
        module.getIslandManager().saveIsland(island);
        player.sendMessage(ChatUtils.getMessage(
                        plugin.getConfig(),
                        "messages.island_privacy_changed",
                        "&aStatus wyspy został zmieniony na: %status%&a."
                )
                .replace("%status%", ChatUtils.color(nextPublic ? "&aPUBLICZNA" : "&cPRYWATNA")));
    }

    private void handleBorder(Player player, String[] args) {
        Island island = requirePlayerIsland(player);
        if (island == null) {
            return;
        }
        if (!island.getPlayerRole(player.getUniqueId()).isAtLeast(IslandRole.CO_OWNER)) {
            player.sendMessage(ChatUtils.getMessage(plugin.getConfig(), "messages.island_no_permission", "&cNie masz uprawnień do tej akcji na tej wyspie!"));
            return;
        }
        BorderColor nextColor = args.length >= 2 ? BorderColor.parse(args[1]) : island.getBorderColor().next();
        island.setBorderColor(nextColor);
        module.getIslandManager().saveIsland(island);
        module.getIslandManager().refreshBordersOnIsland(island);
        player.sendMessage(ChatUtils.getMessage(
                        plugin.getConfig(),
                        "messages.island_border_changed",
                        "&aKolor granicy WorldBorder został zmieniony na: %color%&a."
                )
                .replace("%color%", ChatUtils.color(nextColor.getDisplayName())));
    }

    private void handleWarp(Player player, String[] args) {
        if (args.length < 2) {
            module.getWarpsGUI().open(player);
            return;
        }
        String warpName = args[1].trim();
        IslandManager manager = module.getIslandManager();
        for (Island island : manager.getAllIslands()) {
            if (!island.isPublicAccess() && !island.isPermanentMember(player.getUniqueId())) {
                continue;
            }
            IslandWarp warp = island.getWarp(warpName);
            if (warp != null) {
                Location loc = warp.toLocation();
                if (loc != null) {
                    player.teleport(loc);
                    manager.refreshWorldBorder(player, true);
                    player.sendMessage(ChatUtils.getMessage(
                                    plugin.getConfig(),
                                    "messages.island_warp_teleported",
                                    "&aPrzeteleportowano na warp &e%warp%&a!"
                            )
                            .replace("%warp%", warp.name()));
                }
                return;
            }
        }
        player.sendMessage(ChatUtils.color("&cNie znaleziono publicznego warpa o nazwie: &f" + warpName + "&c!"));
    }

    private void handleSetWarp(Player player, String[] args) {
        Island island = requirePlayerIsland(player);
        if (island == null) {
            return;
        }
        if (!island.hasPermission(player.getUniqueId(), IslandPermission.SETHOME)) {
            player.sendMessage(ChatUtils.getMessage(plugin.getConfig(), "messages.island_no_permission", "&cNie masz uprawnień do tej akcji na tej wyspie!"));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(ChatUtils.color("&cUżycie: /is setwarp <nazwa>"));
            return;
        }
        Location loc = player.getLocation();
        if (!island.containsLocation(loc)) {
            player.sendMessage(ChatUtils.getMessage(plugin.getConfig(), "messages.island_home_outside", "&cWarp musi znajdować się wewnątrz granic Twojej wyspy!"));
            return;
        }

        String rawName = args[1].trim();
        IslandManager manager = module.getIslandManager();
        if (island.getWarp(rawName) == null && island.getWarpsSnapshot().size() >= manager.getMaxWarpsPerIsland()) {
            player.sendMessage(ChatUtils.color("&cOsiągnąłeś maksymalny limit warpów na wyspie (" + manager.getMaxWarpsPerIsland() + ")!"));
            return;
        }

        ItemStack handItem = player.getInventory().getItemInMainHand();
        Material icon = (handItem != null && handItem.getType() != Material.AIR) ? handItem.getType() : Material.ENDER_PEARL;
        IslandWarp warp = new IslandWarp(
                rawName,
                loc.getWorld().getName(),
                loc.getX(),
                loc.getY(),
                loc.getZ(),
                loc.getYaw(),
                loc.getPitch(),
                icon
        );
        island.putWarp(warp);
        manager.saveIsland(island);
        player.sendMessage(ChatUtils.getMessage(plugin.getConfig(), "messages.island_warp_set", "&aUtworzono publiczny warp wyspy: &e%warp%&a!")
                .replace("%warp%", rawName));
    }

    private void handleDelWarp(Player player, String[] args) {
        Island island = requirePlayerIsland(player);
        if (island == null) {
            return;
        }
        if (!island.hasPermission(player.getUniqueId(), IslandPermission.SETHOME)) {
            player.sendMessage(ChatUtils.getMessage(plugin.getConfig(), "messages.island_no_permission", "&cNie masz uprawnień do tej akcji na tej wyspie!"));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(ChatUtils.color("&cUżycie: /is delwarp <nazwa>"));
            return;
        }
        IslandWarp removed = island.removeWarp(args[1].trim());
        if (removed == null) {
            player.sendMessage(ChatUtils.color("&cTwoja wyspa nie posiada warpa o nazwie: &f" + args[1] + "&c!"));
            return;
        }
        module.getIslandManager().saveIsland(island);
        player.sendMessage(ChatUtils.getMessage(plugin.getConfig(), "messages.island_warp_deleted", "&eUsunięto warp wyspy: &f%warp%&e.")
                .replace("%warp%", removed.name()));
    }

    private void handleInvite(Player player, String[] args) {
        Island island = requirePlayerIsland(player);
        if (island == null) {
            return;
        }
        if (!island.hasPermission(player.getUniqueId(), IslandPermission.INVITE)) {
            player.sendMessage(ChatUtils.getMessage(plugin.getConfig(), "messages.island_no_permission", "&cNie masz uprawnień do tej akcji na tej wyspie!"));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(ChatUtils.color("&cUżycie: /is invite <gracz>"));
            return;
        }
        if (island.getPermanentMemberCount() >= island.getMaxMembers()) {
            player.sendMessage(ChatUtils.color("&cOsiągnięto limit członków wyspy (" + island.getMaxMembers() + ")!"));
            return;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            target = Bukkit.getPlayer(args[1]);
        }
        if (target == null) {
            player.sendMessage(ChatUtils.getMessage(plugin.getConfig(), "messages.player_not_found", "&cNie znaleziono gracza!"));
            return;
        }
        if (module.getIslandManager().getPlayerIsland(target.getUniqueId()).isPresent()) {
            player.sendMessage(ChatUtils.color("&cTen gracz należy już do innej wyspy!"));
            return;
        }

        module.getIslandManager().createInvite(target.getUniqueId(), island, player.getName());
        player.sendMessage(ChatUtils.getMessage(plugin.getConfig(), "messages.island_invite_sent", "&aWysłano zaproszenie na wyspę do gracza &f%player%&a.")
                .replace("%player%", target.getName()));
        target.sendMessage(ChatUtils.getMessage(plugin.getConfig(), "messages.island_invite_received", "&aGracz &e%player% &azaprasza Cię na swoją wyspę! Wpisz &e/is join %player%&a.")
                .replace("%player%", player.getName()));
    }

    private void handleJoin(Player player, String[] args) {
        IslandManager manager = module.getIslandManager();
        if (manager.getPlayerIsland(player.getUniqueId()).isPresent()) {
            player.sendMessage(ChatUtils.getMessage(plugin.getConfig(), "messages.island_already_exists", "&cPosiadasz już swoją wyspę! Użyj &e/is home&c."));
            return;
        }
        String filter = args.length >= 2 ? args[1] : null;
        Optional<IslandManager.PendingInvite> inviteOpt = manager.consumeInvite(player.getUniqueId(), filter);
        if (inviteOpt.isEmpty()) {
            player.sendMessage(ChatUtils.color("&cNie masz żadnego aktywnego zaproszenia na wyspę!"));
            return;
        }

        IslandManager.PendingInvite invite = inviteOpt.get();
        Island island = manager.getIslandById(invite.islandId()).orElse(null);
        if (island == null) {
            player.sendMessage(ChatUtils.color("&cTa wyspa już nie istnieje."));
            return;
        }
        if (!manager.addMember(island, player.getUniqueId(), IslandRole.MEMBER)) {
            player.sendMessage(ChatUtils.color("&cNie udało się dołączyć — limit członków wyspy został osiągnięty."));
            return;
        }

        Location home = island.getHomeLocation();
        if (home != null) {
            player.teleport(home);
            manager.refreshWorldBorder(player, true);
        }
        player.sendMessage(ChatUtils.getMessage(plugin.getConfig(), "messages.island_joined", "&aDołączyłeś do wyspy gracza &e%player%&a!")
                .replace("%player%", invite.inviterName()));
    }

    private void handleKick(Player player, String[] args) {
        Island island = requirePlayerIsland(player);
        if (island == null) {
            return;
        }
        IslandRole actorRole = island.getPlayerRole(player.getUniqueId());
        if (!actorRole.isAtLeast(IslandRole.CO_OWNER)) {
            player.sendMessage(ChatUtils.getMessage(plugin.getConfig(), "messages.island_no_permission", "&cNie masz uprawnień do tej akcji na tej wyspie!"));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(ChatUtils.color("&cUżycie: /is kick <gracz>"));
            return;
        }

        UUID targetId = resolveMemberUuid(island, args[1]);
        if (targetId == null) {
            player.sendMessage(ChatUtils.color("&cTen gracz nie jest członkiem Twojej wyspy."));
            return;
        }
        IslandRole targetRole = island.getPlayerRole(targetId);
        if (targetRole.isAtLeast(actorRole)) {
            player.sendMessage(ChatUtils.color("&cNie możesz wyrzucić gracza o równej lub wyższej roli!"));
            return;
        }

        module.getIslandManager().removeMember(island, targetId);
        OfflinePlayer offline = Bukkit.getOfflinePlayer(targetId);
        String name = offline.getName() != null ? offline.getName() : args[1];
        player.sendMessage(ChatUtils.getMessage(plugin.getConfig(), "messages.island_kicked", "&eWyrzucono gracza &f%player% &ez wyspy.")
                .replace("%player%", name));
    }

    private void handleLeave(Player player) {
        Island island = requirePlayerIsland(player);
        if (island == null) {
            return;
        }
        if (island.getOwnerUuid().equals(player.getUniqueId())) {
            player.sendMessage(ChatUtils.color("&cJako właściciel musisz najpierw użyć &e/is transfer <gracz> &club &e/is delete&c!"));
            return;
        }
        module.getIslandManager().removeMember(island, player.getUniqueId());
        player.sendMessage(ChatUtils.getMessage(plugin.getConfig(), "messages.island_left", "&eOpuściłeś obecną wyspę."));
    }

    private void handleTrust(Player player, String[] args) {
        Island island = requirePlayerIsland(player);
        if (island == null) {
            return;
        }
        if (!island.hasPermission(player.getUniqueId(), IslandPermission.INVITE)) {
            player.sendMessage(ChatUtils.getMessage(plugin.getConfig(), "messages.island_no_permission", "&cNie masz uprawnień do tej akcji na tej wyspie!"));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(ChatUtils.color("&cUżycie: /is trust <gracz>"));
            return;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            target = Bukkit.getPlayer(args[1]);
        }
        if (target == null) {
            player.sendMessage(ChatUtils.getMessage(plugin.getConfig(), "messages.player_not_found", "&cNie znaleziono gracza!"));
            return;
        }
        if (island.isPermanentMember(target.getUniqueId())) {
            player.sendMessage(ChatUtils.color("&cTen gracz jest już stałym członkiem Twojej wyspy!"));
            return;
        }

        island.addTrusted(target.getUniqueId());
        player.sendMessage(ChatUtils.getMessage(plugin.getConfig(), "messages.island_trusted", "&aGracz &f%player% &aotrzymał tymczasową rolę &eTRUSTED &8(&7do wylogowania&8)&a.")
                .replace("%player%", target.getName()));
        target.sendMessage(ChatUtils.color("&aOtrzymałeś tymczasowe uprawnienia &eTRUSTED &ana wyspie gracza &f" + player.getName() + " &8(&7do wylogowania&8)&a."));
    }

    private void handleUntrust(Player player, String[] args) {
        Island island = requirePlayerIsland(player);
        if (island == null) {
            return;
        }
        if (!island.hasPermission(player.getUniqueId(), IslandPermission.INVITE)) {
            player.sendMessage(ChatUtils.getMessage(plugin.getConfig(), "messages.island_no_permission", "&cNie masz uprawnień do tej akcji na tej wyspie!"));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(ChatUtils.color("&cUżycie: /is untrust <gracz>"));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            target = Bukkit.getPlayer(args[1]);
        }
        if (target == null || !island.removeTrusted(target.getUniqueId())) {
            player.sendMessage(ChatUtils.color("&cTen gracz nie posiada tymczasowego Trust na Twojej wyspie."));
            return;
        }
        player.sendMessage(ChatUtils.getMessage(plugin.getConfig(), "messages.island_untrusted", "&eCofnięto tymczasowe zaufanie dla gracza &f%player%&e.")
                .replace("%player%", target.getName()));
    }

    private void handleTransfer(Player player, String[] args) {
        Island island = requirePlayerIsland(player);
        if (island == null) {
            return;
        }
        if (!island.getOwnerUuid().equals(player.getUniqueId())) {
            player.sendMessage(ChatUtils.color("&cTylko obecny właściciel może przekazać własność wyspy!"));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(ChatUtils.color("&cUżycie: /is transfer <gracz>"));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            target = Bukkit.getPlayer(args[1]);
        }
        if (target == null || !island.getMembersSnapshot().containsKey(target.getUniqueId())) {
            player.sendMessage(ChatUtils.color("&cNowy właściciel musi być online i należeć do Twojej wyspy!"));
            return;
        }

        if (module.getIslandManager().transferOwnership(island, target.getUniqueId())) {
            player.sendMessage(ChatUtils.getMessage(plugin.getConfig(), "messages.island_transferred", "&aPrzekazano własność wyspy graczowi &e%player%&a!")
                    .replace("%player%", target.getName()));
            target.sendMessage(ChatUtils.color("&aZostałeś nowym właścicielem wyspy (poprzedni właściciel: &f" + player.getName() + "&a)!"));
        }
    }

    private void handleDelete(Player player, String[] args) {
        Island island = requirePlayerIsland(player);
        if (island == null) {
            return;
        }
        if (!island.getOwnerUuid().equals(player.getUniqueId())) {
            player.sendMessage(ChatUtils.color("&cTylko właściciel może usunąć wyspę!"));
            return;
        }
        IslandManager manager = module.getIslandManager();
        boolean confirmedArg = args.length >= 2 && "potwierdz".equalsIgnoreCase(args[1]);
        if (!confirmedArg || !manager.hasValidDeleteConfirmation(player.getUniqueId())) {
            manager.markDeleteConfirmation(player.getUniqueId());
            player.sendMessage(ChatUtils.getMessage(
                    plugin.getConfig(),
                    "messages.island_delete_confirm",
                    "&cUwaga! Usunięcie wyspy jest nieodwracalne! Wpisz &e/is delete potwierdz &cw ciągu 30s."
            ));
            return;
        }

        manager.deleteIsland(island);
        player.sendMessage(ChatUtils.getMessage(plugin.getConfig(), "messages.island_deleted", "&eTwoja wyspa została trwale usunięta."));
    }

    private void handleLevel(Player player) {
        Island island = requirePlayerIsland(player);
        if (island != null && module.getLevelCalculator() != null) {
            module.getLevelCalculator().requestScan(player, island);
        }
    }

    private void handleUpgrades(Player player) {
        Island island = requirePlayerIsland(player);
        if (island != null && module.getUpgradesGUI() != null) {
            module.getUpgradesGUI().open(player, island);
        }
    }

    private Island requirePlayerIsland(Player player) {
        Optional<Island> island = module.getIslandManager().getPlayerIsland(player.getUniqueId());
        if (island.isEmpty()) {
            player.sendMessage(ChatUtils.getMessage(
                    plugin.getConfig(),
                    "messages.island_none",
                    "&cNie posiadasz jeszcze wyspy! Użyj &e/is create&c, aby wybrać schemat."
            ));
            return null;
        }
        return island.get();
    }

    private static UUID resolveMemberUuid(Island island, String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null && island.getMembersSnapshot().containsKey(online.getUniqueId())) {
            return online.getUniqueId();
        }
        for (Map.Entry<UUID, IslandRole> entry : island.getMembersSnapshot().entrySet()) {
            OfflinePlayer offline = Bukkit.getOfflinePlayer(entry.getKey());
            if (offline.getName() != null && offline.getName().equalsIgnoreCase(name)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private static void sendUsage(Player player) {
        player.sendMessage(ChatUtils.color("&6Komendy Wyspy (/is):"));
        player.sendMessage(ChatUtils.color("&e/is &8- &7Otwiera panel wyspy lub wybór schematu"));
        player.sendMessage(ChatUtils.color("&e/is create | home | sethome | panel | level | top | upgrades | roles"));
        player.sendMessage(ChatUtils.color("&e/is privacy | border | warp | setwarp | delwarp"));
        player.sendMessage(ChatUtils.color("&e/is invite | join | kick | leave | trust | untrust | transfer | delete"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!module.isEnabled() || !sender.hasPermission("aradium.player")) {
            return List.of();
        }
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return SUBCOMMANDS.stream().filter(s -> s.startsWith(prefix)).toList();
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return switch (sub) {
                case "create" -> List.of("classic", "desert", "nether").stream()
                        .filter(s -> s.startsWith(prefix)).toList();
                case "border" -> List.of("blue", "green", "red").stream()
                        .filter(s -> s.startsWith(prefix)).toList();
                case "privacy" -> List.of("public", "private").stream()
                        .filter(s -> s.startsWith(prefix)).toList();
                case "delete" -> "potwierdz".startsWith(prefix) ? List.of("potwierdz") : List.of();
                case "invite", "join", "kick", "trust", "untrust", "transfer" -> Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix))
                        .toList();
                default -> List.of();
            };
        }
        return List.of();
    }
}
