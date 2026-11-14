package pl.nauka.aradium.modules.oreregions;

import org.bukkit.Location;
import org.bukkit.World;

public record CuboidRegion(
        String worldName,
        int minX,
        int minY,
        int minZ,
        int maxX,
        int maxY,
        int maxZ
) {

    public static CuboidRegion ofCoordinates(String worldName, int x1, int y1, int z1, int x2, int y2, int z2) {
        if (worldName == null || worldName.isBlank()) {
            return null;
        }
        return new CuboidRegion(
                worldName,
                Math.min(x1, x2),
                Math.min(y1, y2),
                Math.min(z1, z2),
                Math.max(x1, x2),
                Math.max(y1, y2),
                Math.max(z1, z2)
        );
    }

    public static CuboidRegion of(Location a, Location b) {
        if (a == null || b == null) {
            return null;
        }
        World worldA = a.getWorld();
        World worldB = b.getWorld();
        if (worldA == null || worldB == null || !worldA.getName().equals(worldB.getName())) {
            return null;
        }
        return ofCoordinates(
                worldA.getName(),
                a.getBlockX(), a.getBlockY(), a.getBlockZ(),
                b.getBlockX(), b.getBlockY(), b.getBlockZ()
        );
    }

    public boolean contains(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return false;
        }
        return contains(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    public boolean contains(String world, int x, int y, int z) {
        if (world == null || !worldName.equals(world)) {
            return false;
        }
        return x >= minX && x <= maxX
                && y >= minY && y <= maxY
                && z >= minZ && z <= maxZ;
    }
}
