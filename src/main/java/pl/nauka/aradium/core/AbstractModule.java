package pl.nauka.aradium.core;

import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import pl.nauka.aradium.AradiumPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public abstract class AbstractModule implements PluginModule {

    protected final AradiumPlugin plugin;
    private final String id;
    private final List<Listener> listeners = new ArrayList<>();
    private boolean enabled;

    protected AbstractModule(AradiumPlugin plugin, String id) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.id = Objects.requireNonNull(id, "id");
    }

    /**
     * Konstruktor testowy umożliwiający tworzenie instancji modułu bez uruchomionego serwera Paper.
     */
    protected AbstractModule(String id) {
        this.plugin = null;
        this.id = Objects.requireNonNull(id, "id");
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public final void enable() {
        if (enabled) {
            return;
        }
        onEnable();
        enabled = true;
    }

    @Override
    public final void disable() {
        if (!enabled) {
            return;
        }
        try {
            onDisable();
        } finally {
            for (Listener listener : listeners) {
                HandlerList.unregisterAll(listener);
            }
            listeners.clear();
            enabled = false;
        }
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    protected abstract void onEnable();

    protected void onDisable() {
    }

    protected void registerListener(Listener listener) {
        if (plugin == null) {
            return;
        }
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        listeners.add(listener);
    }

    protected void registerCommand(String commandName, TabExecutor executor) {
        if (plugin == null) {
            return;
        }
        PluginCommand command = plugin.getCommand(commandName);
        if (command == null) {
            plugin.getLogger().warning("Brak komendy '" + commandName + "' w plugin.yml (moduł: " + id + ").");
            return;
        }
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }
}
