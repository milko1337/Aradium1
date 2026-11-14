package pl.nauka.aradium.modules.skyblock.listeners;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.Inventory;
import pl.nauka.aradium.AradiumPlugin;
import pl.nauka.aradium.modules.skyblock.SkyBlockModule;
import pl.nauka.aradium.modules.skyblock.gui.SkyBlockGuiHolder;
import pl.nauka.aradium.modules.skyblock.island.Island;
import pl.nauka.aradium.modules.skyblock.island.IslandManager;
import pl.nauka.aradium.modules.skyblock.island.IslandPermission;
import pl.nauka.aradium.utils.ChatUtils;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listener ochrony wysp SkyBlock (uprawnienia ról, prywatność, WorldBorder),
 * obsługi GUI oraz czyszczenia tymczasowych ról TRUSTED po wylogowaniu.
 */
public class IslandProtectionListener implements Listener {

    private static final long NOTICE_COOLDOWN_MS = 2000L;

    private final AradiumPlugin plugin;
    private final SkyBlockModule module;
    private final Map<UUID, Long> lastNoticeMillis = new ConcurrentHashMap<>();

    public IslandProtectionListener(AradiumPlugin plugin, SkyBlockModule module) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.module = Objects.requireNonNull(module, "module");
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!module.isEnabled()) {
            return;
        }
        Player player = event.getPlayer();
        Block block = event.getBlock();
        IslandPermission perm = block.getType() == Material.SPAWNER
                ? IslandPermission.SPAWNERS
                : IslandPermission.BREAK;

        if (!checkLocationPermission(player, block.getLocation(), perm)) {
            event.setCancelled(true);
            sendThrottledNoPermission(player);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!module.isEnabled()) {
            return;
        }
        Player player = event.getPlayer();
        Block block = event.getBlockPlaced();
        IslandPermission perm = block.getType() == Material.SPAWNER
                ? IslandPermission.SPAWNERS
                : IslandPermission.PLACE;

        if (!checkLocationPermission(player, block.getLocation(), perm)) {
            event.setCancelled(true);
            sendThrottledNoPermission(player);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!module.isEnabled()) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.PHYSICAL) {
            return;
        }
        Block clicked = event.getClickedBlock();
        if (clicked == null) {
            return;
        }

        IslandPermission required;
        if (clicked.getType() == Material.SPAWNER) {
            required = IslandPermission.SPAWNERS;
        } else if (clicked.getState(false) instanceof Container) {
            required = IslandPermission.CONTAINERS;
        } else {
            required = IslandPermission.INTERACT;
        }

        Player player = event.getPlayer();
        if (!checkLocationPermission(player, clicked.getLocation(), required)) {
            event.setCancelled(true);
            sendThrottledNoPermission(player);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!module.isEnabled()) {
            return;
        }
        Player player = event.getPlayer();
        if (!checkLocationPermission(player, event.getRightClicked().getLocation(), IslandPermission.INTERACT)) {
            event.setCancelled(true);
            sendThrottledNoPermission(player);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!module.isEnabled() || !(event.getDamager() instanceof Player attacker)) {
            return;
        }
        if (!checkLocationPermission(attacker, event.getEntity().getLocation(), IslandPermission.INTERACT)) {
            event.setCancelled(true);
            sendThrottledNoPermission(attacker);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!module.isEnabled()) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || (from.getBlockX() == to.getBlockX() && from.getBlockZ() == to.getBlockZ())) {
            return;
        }

        IslandManager islandManager = module.getIslandManager();
        if (to.getWorld() == null || !to.getWorld().getName().equals(islandManager.getWorldName())) {
            return;
        }

        Player player = event.getPlayer();
        Optional<Island> targetIsland = islandManager.getIslandAt(to);
        if (targetIsland.isPresent()
                && !targetIsland.get().canVisit(player.getUniqueId())
                && !player.hasPermission("aradium.admin")) {
            event.setCancelled(true);
            sendThrottledPrivateBlocked(player);
            return;
        }

        islandManager.refreshWorldBorder(player, false);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (!module.isEnabled() || event.getTo() == null || event.getTo().getWorld() == null) {
            return;
        }
        IslandManager islandManager = module.getIslandManager();
        Player player = event.getPlayer();
        if (event.getTo().getWorld().getName().equals(islandManager.getWorldName())) {
            Optional<Island> targetIsland = islandManager.getIslandAt(event.getTo());
            if (targetIsland.isPresent()
                    && !targetIsland.get().canVisit(player.getUniqueId())
                    && !player.hasPermission("aradium.admin")) {
                event.setCancelled(true);
                sendThrottledPrivateBlocked(player);
                return;
            }
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> islandManager.refreshWorldBorder(player, true));
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (module.isEnabled()) {
            module.getIslandManager().refreshWorldBorder(event.getPlayer(), true);
        }
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        if (module.isEnabled()) {
            module.getIslandManager().refreshWorldBorder(event.getPlayer(), true);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        lastNoticeMillis.remove(playerId);
        if (module.getIslandManager() != null) {
            module.getIslandManager().clearSessionTrusts(playerId);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory topInventory = event.getView().getTopInventory();
        if (!(topInventory.getHolder() instanceof SkyBlockGuiHolder holder)) {
            return;
        }

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !module.isEnabled()) {
            return;
        }

        Inventory clickedInventory = event.getClickedInventory();
        if (clickedInventory == null || !clickedInventory.equals(topInventory)) {
            return;
        }

        int slot = event.getSlot();
        switch (holder.getGuiType()) {
            case SCHEMATIC_SELECT -> module.getSchematicGUI().handleClick(player, slot);
            case ISLAND_PANEL -> module.getPanelGUI().handleClick(player, holder, slot);
            case ROLES_MENU -> module.getRolesGUI().handleClick(player, holder, slot);
            case WARPS_MENU -> module.getWarpsGUI().handleClick(player, holder, slot);
            case UPGRADES_MENU -> module.getUpgradesGUI().handleClick(player, holder, slot);
            case TOP_MENU -> module.getTopGUI().handleClick(player, holder, slot);
            case QUESTS_MENU -> module.getQuestGUI().handleClick(player, holder, slot);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof SkyBlockGuiHolder) {
            event.setCancelled(true);
        }
    }

    private boolean checkLocationPermission(Player player, Location location, IslandPermission permission) {
        if (location == null || location.getWorld() == null) {
            return true;
        }
        IslandManager islandManager = module.getIslandManager();
        if (!location.getWorld().getName().equals(islandManager.getWorldName())) {
            return true;
        }
        if (player.hasPermission("aradium.admin")) {
            return true;
        }
        Optional<Island> island = islandManager.getIslandAt(location);
        return island.map(value -> value.hasPermission(player.getUniqueId(), permission)).orElse(false);
    }

    private void sendThrottledNoPermission(Player player) {
        if (canSendNotice(player.getUniqueId())) {
            player.sendMessage(ChatUtils.getMessage(
                    plugin.getConfig(),
                    "messages.island_no_permission",
                    "&cNie masz uprawnień do tej akcji na tej wyspie!"
            ));
        }
    }

    private void sendThrottledPrivateBlocked(Player player) {
        if (canSendNotice(player.getUniqueId())) {
            player.sendMessage(ChatUtils.getMessage(
                    plugin.getConfig(),
                    "messages.island_private_blocked",
                    "&cTa wyspa jest prywatna! Właściciel zablokował dostęp odwiedzającym."
            ));
        }
    }

    private boolean canSendNotice(UUID playerId) {
        long now = System.currentTimeMillis();
        Long previous = lastNoticeMillis.get(playerId);
        if (previous != null && now - previous < NOTICE_COOLDOWN_MS) {
            return false;
        }
        lastNoticeMillis.put(playerId, now);
        return true;
    }
}
