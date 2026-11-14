package pl.nauka.aradium.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.file.FileConfiguration;

public final class ChatUtils {

    private static final LegacyComponentSerializer AMPERSAND_SERIALIZER = LegacyComponentSerializer.legacyAmpersand();
    private static final LegacyComponentSerializer SECTION_SERIALIZER = LegacyComponentSerializer.legacySection();

    private ChatUtils() {
    }

    public static String color(String message) {
        if (message == null || message.isEmpty()) {
            return "";
        }
        return SECTION_SERIALIZER.serialize(AMPERSAND_SERIALIZER.deserialize(message));
    }

    public static Component component(String message) {
        if (message == null || message.isEmpty()) {
            return Component.empty();
        }
        return AMPERSAND_SERIALIZER.deserialize(message);
    }

    public static Component itemComponent(String message) {
        if (message == null || message.isEmpty()) {
            return Component.empty();
        }
        return component(message).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static String getMessage(FileConfiguration config, String path, String defaultMessage) {
        if (config == null) {
            return color(defaultMessage);
        }
        String raw = config.getString(path, defaultMessage);
        return color(raw != null ? raw : defaultMessage);
    }
}
