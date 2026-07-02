package pl.nauka.aradium.managers;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import pl.nauka.aradium.AradiumPlugin;

import java.io.File;
import java.io.IOException;

public class OreRegionManager {

    private final AradiumPlugin plugin;
    private File file;
    private FileConfiguration config;

    public OreRegionManager(AradiumPlugin plugin) {
        this.plugin = plugin;
        setup();
    }

    private void setup() {
        file = new File(plugin.getDataFolder(), "regions.yml");
        if (!file.exists()) {
            try {
                file.getParentFile().mkdirs();
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        config = YamlConfiguration.loadConfiguration(file);
    }

    public void setMin(Material ore, Location loc) {
        String path = "regions." + ore.name() + ".min";
        saveLocation(path, loc);
    }

    public void setMax(Material ore, Location loc) {
        String path = "regions." + ore.name() + ".max";
        saveLocation(path, loc);
    }

    private void saveLocation(String path, Location loc) {
        config.set(path + ".world", loc.getWorld().getName());
        config.set(path + ".x", loc.getBlockX());
        config.set(path + ".y", loc.getBlockY());
        config.set(path + ".z", loc.getBlockZ());
        saveConfig();
    }

    private Location getLocation(String path) {
        if (!config.contains(path)) return null;
        World world = plugin.getServer().getWorld(config.getString(path + ".world"));
        if (world == null) return null;
        int x = config.getInt(path + ".x");
        int y = config.getInt(path + ".y");
        int z = config.getInt(path + ".z");
        return new Location(world, x, y, z);
    }

    public boolean isInRegion(Material ore, Location loc) {
        Location min = getLocation("regions." + ore.name() + ".min");
        Location max = getLocation("regions." + ore.name() + ".max");

        if (min == null || max == null) return false;
        if (!min.getWorld().equals(loc.getWorld())) return false;

        int minX = Math.min(min.getBlockX(), max.getBlockX());
        int minY = Math.min(min.getBlockY(), max.getBlockY());
        int minZ = Math.min(min.getBlockZ(), max.getBlockZ());
        
        int maxX = Math.max(min.getBlockX(), max.getBlockX());
        int maxY = Math.max(min.getBlockY(), max.getBlockY());
        int maxZ = Math.max(min.getBlockZ(), max.getBlockZ());

        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();

        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    private void saveConfig() {
        try {
            config.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
