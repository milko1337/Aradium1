package pl.nauka.aradium.core;

import org.bukkit.configuration.file.FileConfiguration;
import pl.nauka.aradium.AradiumPlugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.logging.Logger;

public class ModuleManager {

    private final Supplier<FileConfiguration> configSupplier;
    private final Logger logger;
    private final Runnable configSaver;
    private final Map<String, PluginModule> modules = new LinkedHashMap<>();

    public ModuleManager(AradiumPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        this.configSupplier = plugin::getConfig;
        this.logger = plugin.getLogger();
        this.configSaver = plugin::saveConfig;
    }

    public ModuleManager(FileConfiguration config, Logger logger, Runnable configSaver) {
        Objects.requireNonNull(config, "config");
        this.configSupplier = () -> config;
        this.logger = Objects.requireNonNull(logger, "logger");
        this.configSaver = configSaver;
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
                logger.info("Włączono moduł: " + module.getId());
            } else {
                logger.info("Pominięto wyłączony w config.yml moduł: " + module.getId());
            }
        }
    }

    public void disableAll() {
        List<PluginModule> registered = new ArrayList<>(modules.values());
        Collections.reverse(registered);
        for (PluginModule module : registered) {
            if (module.isEnabled()) {
                module.disable();
                logger.info("Wyłączono moduł: " + module.getId());
            }
        }
    }

    public void reloadAll() {
        disableAll();
        enableAll();
    }

    public boolean isConfiguredEnabled(String moduleId) {
        if (moduleId == null || moduleId.isBlank()) {
            return false;
        }
        FileConfiguration config = configSupplier.get();
        return config == null || config.getBoolean("modules." + moduleId.trim().toLowerCase(Locale.ROOT), true);
    }

    public Optional<PluginModule> getModule(String moduleId) {
        if (moduleId == null || moduleId.isBlank()) {
            return Optional.empty();
        }
        String normalized = moduleId.trim().toLowerCase(Locale.ROOT);
        PluginModule direct = modules.get(normalized);
        if (direct != null) {
            return Optional.of(direct);
        }
        for (PluginModule module : modules.values()) {
            if (module.getId().equalsIgnoreCase(normalized)) {
                return Optional.of(module);
            }
        }
        return Optional.empty();
    }

    public boolean setModuleEnabled(String moduleId, boolean enabled, boolean saveToConfig) {
        Optional<PluginModule> moduleOpt = getModule(moduleId);
        if (moduleOpt.isEmpty()) {
            return false;
        }
        PluginModule module = moduleOpt.get();
        if (enabled && !module.isEnabled()) {
            module.enable();
            logger.info("Ręcznie włączono moduł: " + module.getId());
        } else if (!enabled && module.isEnabled()) {
            module.disable();
            logger.info("Ręcznie wyłączono moduł: " + module.getId());
        }

        if (saveToConfig) {
            FileConfiguration config = configSupplier.get();
            if (config != null) {
                config.set("modules." + module.getId(), enabled);
            }
            if (configSaver != null) {
                configSaver.run();
            }
        }
        return true;
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
