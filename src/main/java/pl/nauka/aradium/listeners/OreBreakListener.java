package pl.nauka.aradium.listeners;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import pl.nauka.aradium.AradiumPlugin;

public class OreBreakListener implements Listener {

    private final AradiumPlugin plugin;

    public OreBreakListener(AradiumPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Material type = block.getType();
        
        // Sprawdzamy czy ruda została wykopana w przypisanym jej regionie
        if (plugin.getOreRegionManager().isInRegion(type, block.getLocation())) {
            
            // Opóźnione zadanie na głównym wątku podmieniające powietrze z powrotem na blok
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                block.setType(type);
            }, 20L * 10); // 10 sekund
            
        }
    }
}
