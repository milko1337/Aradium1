package pl.nauka.aradium.core;

public interface PluginModule {

    String getId();

    void enable();

    void disable();

    default void reload() {
        disable();
        enable();
    }

    boolean isEnabled();
}
