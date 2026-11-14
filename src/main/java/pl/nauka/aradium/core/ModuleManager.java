package pl.nauka.aradium.core;

import pl.nauka.aradium.AradiumPlugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ModuleManager {

    private final AradiumPlugin plugin;
    private final Map<String, PluginModule> modules = new LinkedHashMap<>();

    public ModuleManager(AradiumPlugin plugin) {
        this.plugin = plugin;
    }

    public void registerModule(PluginModule module) {
        if (modules.containsKey(module.getId())) {
            throw new IllegalArgumentException("Moduł o identyfikatorze '" + module.getId() + "' jest już zarejestrowany.");
        }
        modules.put(module.getId(), module);
    }

    public void enableAll() {
        for (PluginModule module : modules.values()) {
            if (isConfiguredEnabled(module.getId())) {
                module.enable();
                plugin.getLogger().info("Włączono moduł: " + module.getId());
            } else {
                plugin.getLogger().info("Pominięto wyłączony w config.yml moduł: " + module.getId());
            }
        }
    }

    public void disableAll() {
        List<PluginModule> registered = new ArrayList<>(modules.values());
        Collections.reverse(registered);
        for (PluginModule module : registered) {
            if (module.isEnabled()) {
                module.disable();
                plugin.getLogger().info("Wyłączono moduł: " + module.getId());
            }
        }
    }

    public void reloadAll() {
        disableAll();
        enableAll();
    }

    public boolean isConfiguredEnabled(String moduleId) {
        return plugin.getConfig().getBoolean("modules." + moduleId, true);
    }

    public <T extends PluginModule> Optional<T> getModule(Class<T> moduleClass) {
        for (PluginModule module : modules.values()) {
            if (moduleClass.isInstance(module)) {
                return Optional.of(moduleClass.cast(module));
            }
        }
        return Optional.empty();
    }

    public Collection<PluginModule> getModules() {
        return Collections.unmodifiableCollection(modules.values());
    }
}
