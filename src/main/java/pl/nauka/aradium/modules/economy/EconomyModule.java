package pl.nauka.aradium.modules.economy;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitTask;
import pl.nauka.aradium.AradiumPlugin;
import pl.nauka.aradium.core.AbstractModule;

import java.io.File;

/**
 * Moduł Autorskiej Ekonomii zarządzający cyklem życia EconomyManager,
 * okresowym zapisem wsadowym (write-behind) oraz komendami /money, /pay i /eco.
 */
public class EconomyModule extends AbstractModule implements Listener {

    private EconomyManager economyManager;
    private BukkitTask autoSaveTask;

    public EconomyModule(AradiumPlugin plugin) {
        super(plugin, "economy");
    }

    @Override
    protected void onEnable() {
        double startingBalance = plugin.getConfig().getDouble("economy.starting_balance", 100.0);
        long autoSaveSeconds = Math.max(10L, plugin.getConfig().getLong("economy.autosave_interval_seconds", 60L));

        File yamlFallbackFile = new File(plugin.getDataFolder(), "economy.yml");
        this.economyManager = new EconomyManager(
                plugin.getDatabaseManager(),
                yamlFallbackFile,
                plugin.getLogger(),
                startingBalance
        );
        this.economyManager.loadAll();

        for (Player player : Bukkit.getOnlinePlayers()) {
            this.economyManager.getMoney(player.getUniqueId());
        }

        registerListener(this);
        registerCommand("money", new MoneyCommand(plugin, this));
        registerCommand("pay", new PayCommand(plugin, this));
        registerCommand("eco", new EcoAdminCommand(plugin, this));

        long periodTicks = autoSaveSeconds * 20L;
        this.autoSaveTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(
                plugin,
                () -> {
                    if (economyManager != null) {
                        economyManager.flushDirtyAccounts();
                    }
                },
                periodTicks,
                periodTicks
        );
    }

    @Override
    protected void onDisable() {
        if (autoSaveTask != null) {
            autoSaveTask.cancel();
            autoSaveTask = null;
        }
        if (economyManager != null) {
            economyManager.flushDirtyAccounts();
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (economyManager != null) {
            economyManager.getMoney(event.getPlayer().getUniqueId());
        }
    }

    public EconomyManager getEconomyManager() {
        return economyManager;
    }
}
