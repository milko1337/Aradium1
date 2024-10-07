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
import static nauka1.Aradium1.imports.material.itemdirtMaterial;
import static nauka1.Aradium1.imports.permisions.permissiondirt;

public class Dirt {
    public static final String USAGE = "Usage: /dirtregion <min|max>";

    public static class DirtRespawnTask extends BukkitRunnable implements Listener {
        private final World world;
        private final Location min;
        private final Location max;
        private static final long INTERVAL = 20L * 10;

        public DirtRespawnTask(World world, Location min, Location max) {
            this.world = world;
            this.min = min;
            this.max = max;
        }

        @Override
        public void run() {
            // Implement task logic here
        }
    }

    public static class DirtRegionCommand implements CommandExecutor, Listener {
        private static Location min;
        private static Location max;

        public static void setCoordinates(Location min, Location max) {
            DirtRegionCommand.min = min;
            DirtRegionCommand.max = max;
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

            Player player = (Player) sender;
            Location location = player.getLocation();

            if (args[0].equalsIgnoreCase("min")) {
                min = location;
                sender.sendMessage(mini);
            } else if (args[0].equalsIgnoreCase("max")) {
                max = location;
                sender.sendMessage(maxi);
            } else {
                sender.sendMessage(USAGE);
                return true;
            }

            if (min != null && max != null) {
                World world = min.getWorld();
                BukkitRunnable task = new DirtRespawnTask(world, min, max);
                Nauka plugin = (Nauka) Bukkit.getPluginManager().getPlugin("nauka");
                assert plugin != null;
                task.runTaskTimer(plugin, 20L, DirtRespawnTask.INTERVAL);
                sender.sendMessage(place);
                saveCoordinatesToFile(min, max);
            }
            return true;
        }

        private boolean isPlayerAndHasPermission(CommandSender sender) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(console);
                return false;
            }

            if (!sender.hasPermission(permissiondirt)) {
                sender.sendMessage(permissions);
                return false;
            }

            return true;
        }
    }

    private static void saveCoordinatesToFile(Location min, Location max) {
        File file = new File("plugins/Aradium/coordinates.yml");
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
        File file = new File("plugins/Aradium/coordinates.yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);

        if (config.contains("min") && config.contains("max")) {
            Location min = Location.deserialize(Objects.requireNonNull(config.getConfigurationSection("min")).getValues(true));
            Location max = Location.deserialize(Objects.requireNonNull(config.getConfigurationSection("max")).getValues(true));
            DirtRegionCommand.setCoordinates(min, max);
        }
    }
}