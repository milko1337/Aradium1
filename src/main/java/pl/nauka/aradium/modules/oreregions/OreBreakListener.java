package pl.nauka.aradium.modules.oreregions;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

public class OreBreakListener implements Listener {

    private final OreRegionModule module;

    public OreBreakListener(OreRegionModule module) {
        this.module = module;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        module.handleBlockBreak(event.getBlock());
    }
}
