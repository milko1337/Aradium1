package pl.nauka.aradium.modules.magicitems;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.GrindstoneInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import pl.nauka.aradium.AradiumPlugin;
import pl.nauka.aradium.utils.ChatUtils;

import java.util.UUID;

public class MagicItemListener implements Listener {

    private final AradiumPlugin plugin;
    private final MagicItemModule module;

    public MagicItemListener(AradiumPlugin plugin, MagicItemModule module) {
        this.plugin = plugin;
        this.module = module;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (!event.getAction().isRightClick()) {
            return;
        }

        Player player = event.getPlayer();
        if (event.getHand() == EquipmentSlot.OFF_HAND) {
            if (module.isMagicItem(event.getItem())
                    || module.isMagicItem(player.getInventory().getItemInMainHand())) {
                denyInteraction(event);
            }
            return;
        }

        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        ItemStack item = event.getItem();
        if (item == null || !item.hasItemMeta()) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        String type = module.getMagicType(meta);
        Integer level = module.getMagicLevel(meta);
        if (type == null || level == null) {
            return;
        }

        denyInteraction(event);

        MagicItemDefinition definition = module.getDefinition(type);
        if (definition == null) {
            return;
        }

        long remainingSeconds = module.getRemainingCooldownSeconds(player.getUniqueId(), definition.id());
        if (remainingSeconds > 0) {
            String msg = ChatUtils.getMessage(
                    plugin.getConfig(),
                    "messages.cooldown_wait",
                    "&cMusisz poczekać jeszcze %time% sekund przed kolejnym użyciem!"
            ).replace("%time%", String.valueOf(remainingSeconds));
            player.sendMessage(msg);
            return;
        }

        if (definition.isGhostAbility()) {
            module.activateGhostMode(player, definition, level);
        } else {
            if (definition.effectType() == null) {
                plugin.getLogger().warning("Brak prawidłowego efektu dla magicznego przedmiotu '" + definition.id() + "' — aktywacja przerwana.");
                return;
            }
            int amplifier = Math.max(0, level - 1);
            player.addPotionEffect(new PotionEffect(definition.effectType(), definition.durationTicks(), amplifier));
        }

        player.sendMessage(ChatUtils.getMessage(plugin.getConfig(), "messages.effect_applied", "&aAktywowałeś specjalną umiejętność!"));
        module.setCooldown(player.getUniqueId(), definition.id(), definition.cooldownSeconds());
    }

    private void denyInteraction(PlayerInteractEvent event) {
        event.setCancelled(true);
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (module.isMagicItem(event.getItemInHand())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (module.isMagicItem(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        for (ItemStack ingredient : event.getInventory().getMatrix()) {
            if (module.isMagicItem(ingredient)) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }

    @EventHandler
    public void onPrepareGrindstone(PrepareGrindstoneEvent event) {
        GrindstoneInventory inventory = event.getInventory();
        if (module.isMagicItem(inventory.getUpperItem()) || module.isMagicItem(inventory.getLowerItem())) {
            event.setResult(null);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpectatorTeleport(PlayerTeleportEvent event) {
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.SPECTATE
                && module.isInGhostMode(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        if (module.isInGhostMode(playerId)) {
            module.finishGhostMode(playerId, true);
        }
        module.cleanupExpiredCooldowns(playerId);
    }
}
