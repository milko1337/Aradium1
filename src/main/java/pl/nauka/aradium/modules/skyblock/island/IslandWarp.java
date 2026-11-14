package pl.nauka.aradium.modules.skyblock.island;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.Objects;

/**
 * Publiczny warp zdefiniowany przez właściciela wyspy.
 */
public record IslandWarp(
        String name,
        String worldName,
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        Material icon
) {
    public IslandWarp {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(worldName, "worldName");
        icon = icon != null ? icon : Material.ENDER_PEARL;
    }

    public Location toLocation() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        return new Location(world, x, y, z, yaw, pitch);
    }
}
