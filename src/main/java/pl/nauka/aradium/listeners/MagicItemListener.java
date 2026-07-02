package pl.nauka.aradium.listeners;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import pl.nauka.aradium.AradiumPlugin;
import pl.nauka.aradium.utils.ChatUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MagicItemListener implements Listener {

    private final AradiumPlugin plugin;
    // UUID -> (MagicType -> ExpiresAt)
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();

    public MagicItemListener(AradiumPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (!event.getAction().name().contains("RIGHT")) return;
        
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null || !item.hasItemMeta()) return;

        NamespacedKey typeKey = new NamespacedKey(plugin, "magic_type");
        NamespacedKey levelKey = new NamespacedKey(plugin, "magic_level");

        if (!item.getItemMeta().getPersistentDataContainer().has(typeKey, PersistentDataType.STRING)) {
            return; // To nie jest nasz magiczny przedmiot
        }

        String type = item.getItemMeta().getPersistentDataContainer().get(typeKey, PersistentDataType.STRING);
        Integer level = item.getItemMeta().getPersistentDataContainer().get(levelKey, PersistentDataType.INTEGER);
        
        if (type == null || level == null) return;

        String path = "magic_items." + type;
        if (!plugin.getConfig().contains(path)) return; // Usunięte z configu
        
        // Cooldown check
        long currentTime = System.currentTimeMillis();
        int cooldownSeconds = plugin.getConfig().getInt(path + ".cooldown", 120);
        
        Map<String, Long> playerCooldowns = cooldowns.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());
        if (playerCooldowns.containsKey(type)) {
            long expiresAt = playerCooldowns.get(type);
            if (currentTime < expiresAt) {
                long remaining = (expiresAt - currentTime) / 1000;
                String msg = plugin.getConfig().getString("messages.cooldown_wait").replace("%time%", String.valueOf(remaining));
                player.sendMessage(ChatUtils.color(msg));
                return;
            }
        }

        // Apply effect
        String effectName = plugin.getConfig().getString(path + ".effect_type");
        PotionEffectType potionEffectType = PotionEffectType.getByName(effectName);
        
        if (potionEffectType != null) {
            int durationTicks = plugin.getConfig().getInt(path + ".duration", 600);
            player.addPotionEffect(new PotionEffect(potionEffectType, durationTicks, level));
        }

        player.sendMessage(ChatUtils.color(plugin.getConfig().getString("messages.effect_applied")));
        playerCooldowns.put(type, currentTime + (cooldownSeconds * 1000L));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Czyszczenie pamięci (naprawa memory leak)
        cooldowns.remove(event.getPlayer().getUniqueId());
    }
}
