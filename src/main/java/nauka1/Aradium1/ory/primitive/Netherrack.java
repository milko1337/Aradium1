package nauka1.Aradium1.ory.primitive;

import nauka1.Aradium1.Nauka;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

import static nauka1.Aradium1.imports.general.*;
import static nauka1.Aradium1.imports.material.itemnetherrackMaterial;
import static nauka1.Aradium1.imports.permisions.permissionnetherrack;

public class Netherrack {
    public static final String USAGE = "Usage: /netherrackregion <min|max>";
    public static final String PLUGIN_NAME = "nauka";
    public static final String COORDINATES_FILE_PATH = "plugins/Aradium/coordinates.yml";
    public static final long TASK_START_DELAY = 20L;

    public static class NetherrackRespawnTask extends BukkitRunnable implements Listener {
        private final World world;
        private final Location min;
        private final Location max;
        private static final long INTERVAL = 20L * 10;

        public NetherrackRespawnTask(World world, Location min, Location max) {
            this.world = world;
            this.min = min;
            this.max = max;
        }

        @Override
        public void run() {
            if (world.isThundering()) {
                return;
            }
            respawnNetherrack();
        }

        private void respawnNetherrack() {
            int minX = Math.min(min.getBlockX(), max.getBlockX());
            int minY = Math.min(min.getBlockY(), max.getBlockY());
            int minZ = Math.min(min.getBlockZ(), max.getBlockZ());
            int maxX = Math.max(min.getBlockX(), max.getBlockX());
            int maxY = Math.max(min.getBlockY(), max.getBlockY());
            int maxZ = Math.max(min.getBlockZ(), max.getBlockZ());

            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        Block block = world.getBlockAt(x, y, z);
                        if (block.getType().equals(Material.AIR)) {
                            block.setType(itemnetherrackMaterial);
                        }
                    }
                }
            }
        }
    }

    public static class NetherrackRegionCommand implements CommandExecutor, Listener {
        private static Location min;
        private static Location max;

        public static void setCoordinates(Location min, Location max) {
            NetherrackRegionCommand.min = min;
            NetherrackRegionCommand.max = max;
        }

        @Override
        public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
            if (!isPlayerAndHasPermission(sender)) {
                return true;
            }
            if (args.length != 1) {
                sender.sendMessage(USAGE);
                return true;
            }
            handleCommand(sender, args[0]);
            return true;
        }

        private boolean isPlayerAndHasPermission(CommandSender sender) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(console);
                return false;
            }
            if (!sender.hasPermission(permissionnetherrack)) {
                sender.sendMessage(permissions);
                return false;
            }
            return true;
        }

        private void handleCommand(CommandSender sender, String arg) {
            Player player = (Player) sender;
            Location location = player.getLocation();
            if (arg.equalsIgnoreCase("min")) {
                min = location;
                sender.sendMessage(mini);
            } else if (arg.equalsIgnoreCase("max")) {
                max = location;
                sender.sendMessage(maxi);
            } else {
                sender.sendMessage(USAGE);
                return;
            }
            if (min != null && max != null) {
                startRespawnTask(sender);
                saveCoordinatesToFile(min, max);
            }
        }

        private void startRespawnTask(CommandSender sender) {
            World world = min.getWorld();
            BukkitRunnable task = new NetherrackRespawnTask(world, min, max);
            Nauka plugin = (Nauka) Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
            assert plugin != null;
            task.runTaskTimer(plugin, TASK_START_DELAY, NetherrackRespawnTask.INTERVAL);
            sender.sendMessage(place);
        }
    }

    private static void saveCoordinatesToFile(Location min, Location max) {
        File file = new File(COORDINATES_FILE_PATH);
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        config.set("min", min.serialize());
        config.set("max", max.serialize());
        try {
            config.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void loadCoordinatesFromFile() {
        File file = new File(COORDINATES_FILE_PATH);
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        if (config.contains("min") && config.contains("max")) {
            Location min = Location.deserialize(Objects.requireNonNull(config.getConfigurationSection("min")).getValues(true));
            Location max = Location.deserialize(Objects.requireNonNull(config.getConfigurationSection("max")).getValues(true));
            NetherrackRegionCommand.setCoordinates(min, max);
        }
    }
}