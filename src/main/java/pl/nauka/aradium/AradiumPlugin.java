package pl.nauka.aradium;

import org.bukkit.plugin.java.JavaPlugin;
import pl.nauka.aradium.commands.MagicItemCommand;
import pl.nauka.aradium.commands.OreRegionCommand;
import pl.nauka.aradium.listeners.MagicItemListener;
import pl.nauka.aradium.listeners.OreBreakListener;
import pl.nauka.aradium.managers.MagicItemManager;
import pl.nauka.aradium.managers.OreRegionManager;

public class AradiumPlugin extends JavaPlugin {

    private MagicItemManager magicItemManager;
    private OreRegionManager oreRegionManager;

    @Override
    public void onEnable() {
        // Konfiguracja
        saveDefaultConfig();

        // Inicjalizacja Menedżerów
        this.magicItemManager = new MagicItemManager(this);
        this.oreRegionManager = new OreRegionManager(this);

        // Komendy
        if (getCommand("magicitem") != null) {
            getCommand("magicitem").setExecutor(new MagicItemCommand(this));
        }
        if (getCommand("oreregion") != null) {
            getCommand("oreregion").setExecutor(new OreRegionCommand(this));
        }

        // Listenery
        getServer().getPluginManager().registerEvents(new MagicItemListener(this), this);
        getServer().getPluginManager().registerEvents(new OreBreakListener(this), this);

        getLogger().info("Plugin Aradium (v2 Optimized) zostal pomyslnie wlaczony!");
    }

    public MagicItemManager getMagicItemManager() {
        return magicItemManager;
    }

    public OreRegionManager getOreRegionManager() {
        return oreRegionManager;
    }
}
