package pl.nauka.aradium.modules.spawners;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.Objects;

/**
 * Dedykowany InventoryHolder przypisany do konkretnych współrzędnych bloku spawnera (Anti-Dupe).
 */
public class SpawnerUpgradeHolder implements InventoryHolder {

    private final String worldName;
    private final int blockX;
    private final int blockY;
    private final int blockZ;
    private Inventory inventory;

    public SpawnerUpgradeHolder(String worldName, int blockX, int blockY, int blockZ) {
        this.worldName = Objects.requireNonNull(worldName, "worldName");
        this.blockX = blockX;
        this.blockY = blockY;
        this.blockZ = blockZ;
    }

    public static SpawnerUpgradeHolder forBlock(Block block) {
        Objects.requireNonNull(block, "block");
        return new SpawnerUpgradeHolder(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
    }

    public boolean matchesBlock(Block block) {
        return block != null
                && block.getWorld().getName().equals(worldName)
                && block.getX() == blockX
                && block.getY() == blockY
                && block.getZ() == blockZ;
    }

    public boolean matchesLocation(String otherWorld, int otherX, int otherY, int otherZ) {
        return worldName.equals(otherWorld)
                && blockX == otherX
                && blockY == otherY
                && blockZ == otherZ;
    }

    public CreatureSpawner resolveSpawner() {
        World world = Bukkit.getWorld(worldName);
        if (world == null || !world.isChunkLoaded(blockX >> 4, blockZ >> 4)) {
            return null;
        }
        Block block = world.getBlockAt(blockX, blockY, blockZ);
        if (block.getType() != Material.SPAWNER) {
            return null;
        }
        return block.getState() instanceof CreatureSpawner spawner ? spawner : null;
    }

    public String getWorldName() {
        return worldName;
    }

    public int getBlockX() {
        return blockX;
    }

    public int getBlockY() {
        return blockY;
    }

    public int getBlockZ() {
        return blockZ;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
