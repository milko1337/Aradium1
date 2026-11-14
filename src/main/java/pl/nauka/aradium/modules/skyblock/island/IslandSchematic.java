package pl.nauka.aradium.modules.skyblock.island;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;

/**
 * Dostępne schematy startowe wysp generowane w świecie skyblock_world.
 */
public enum IslandSchematic {
    CLASSIC(
            "classic",
            "&a&lKlasyczna Wyspa",
            Material.GRASS_BLOCK,
            List.of(
                    "&8» &7Tradycyjna trawiasta wyspa z dębem",
                    "&8» &7i pełną skrzynią startową.",
                    "",
                    "&eKliknij, aby utworzyć!"
            )
    ),
    DESERT(
            "desert",
            "&e&lPustynna Oaza",
            Material.SAND,
            List.of(
                    "&8» &7Słoneczna wyspa piaskowa z kaktusem,",
                    "&8» &7trzciną cukrową i oazą wodną.",
                    "",
                    "&eKliknij, aby utworzyć!"
            )
    ),
    NETHER(
            "nether",
            "&c&lPiekielna Wyspa",
            Material.CRIMSON_NYLIUM,
            List.of(
                    "&8» &7Surowa wyspa Netheru z szkarłatnym",
                    "&8» &7drzewem i cennymi surowcami.",
                    "",
                    "&eKliknij, aby utworzyć!"
            )
    );

    private final String id;
    private final String displayName;
    private final Material icon;
    private final List<String> lore;

    IslandSchematic(String id, String displayName, Material icon, List<String> lore) {
        this.id = id;
        this.displayName = displayName;
        this.icon = icon;
        this.lore = lore;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Material getIcon() {
        return icon;
    }

    public List<String> getLore() {
        return lore;
    }

    public void generate(World world, int centerX, int baseY, int centerZ) {
        if (world == null) {
            return;
        }
        switch (this) {
            case CLASSIC -> generateClassic(world, centerX, baseY, centerZ);
            case DESERT -> generateDesert(world, centerX, baseY, centerZ);
            case NETHER -> generateNether(world, centerX, baseY, centerZ);
        }
    }

    private void generateClassic(World world, int cx, int y, int cz) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                world.getBlockAt(cx + dx, y - 2, cz + dz).setType(Material.STONE, false);
                world.getBlockAt(cx + dx, y - 1, cz + dz).setType(Material.DIRT, false);
                world.getBlockAt(cx + dx, y, cz + dz).setType(Material.GRASS_BLOCK, false);
            }
        }
        world.getBlockAt(cx, y - 2, cz).setType(Material.BEDROCK, false);
        placeSimpleTree(world, cx - 2, y + 1, cz - 2, Material.OAK_LOG, Material.OAK_LEAVES);
        placeStarterChest(world, cx + 2, y + 1, cz, Material.OAK_SAPLING, Material.WHEAT_SEEDS);
    }

    private void generateDesert(World world, int cx, int y, int cz) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                world.getBlockAt(cx + dx, y - 2, cz + dz).setType(Material.SANDSTONE, false);
                world.getBlockAt(cx + dx, y - 1, cz + dz).setType(Material.SANDSTONE, false);
                world.getBlockAt(cx + dx, y, cz + dz).setType(Material.SAND, false);
            }
        }
        world.getBlockAt(cx, y - 2, cz).setType(Material.BEDROCK, false);
        world.getBlockAt(cx - 2, y, cz + 2).setType(Material.WATER, false);
        world.getBlockAt(cx - 1, y + 1, cz + 2).setType(Material.SUGAR_CANE, false);
        world.getBlockAt(cx - 2, y + 1, cz - 2).setType(Material.CACTUS, false);
        world.getBlockAt(cx - 2, y + 2, cz - 2).setType(Material.CACTUS, false);
        placeStarterChest(world, cx + 2, y + 1, cz, Material.ACACIA_SAPLING, Material.PUMPKIN_SEEDS);
    }

    private void generateNether(World world, int cx, int y, int cz) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                world.getBlockAt(cx + dx, y - 2, cz + dz).setType(Material.BLACKSTONE, false);
                world.getBlockAt(cx + dx, y - 1, cz + dz).setType(Material.NETHERRACK, false);
                world.getBlockAt(cx + dx, y, cz + dz).setType(Material.CRIMSON_NYLIUM, false);
            }
        }
        world.getBlockAt(cx, y - 2, cz).setType(Material.BEDROCK, false);
        world.getBlockAt(cx - 1, y, cz + 2).setType(Material.SOUL_SAND, false);
        world.getBlockAt(cx - 1, y + 1, cz + 2).setType(Material.NETHER_WART, false);
        placeSimpleTree(world, cx - 2, y + 1, cz - 2, Material.CRIMSON_STEM, Material.NETHER_WART_BLOCK);
        placeStarterChest(world, cx + 2, y + 1, cz, Material.CRIMSON_FUNGUS, Material.MELON_SEEDS);
    }

    private void placeSimpleTree(World world, int x, int y, int z, Material trunk, Material leaves) {
        for (int dy = 0; dy < 4; dy++) {
            world.getBlockAt(x, y + dy, z).setType(trunk, false);
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = 2; dy <= 4; dy++) {
                    if (dx == 0 && dz == 0 && dy < 4) {
                        continue;
                    }
                    world.getBlockAt(x + dx, y + dy, z + dz).setType(leaves, false);
                }
            }
        }
        world.getBlockAt(x, y + 5, z).setType(leaves, false);
    }

    private void placeStarterChest(World world, int x, int y, int z, Material sapling, Material seeds) {
        Block chestBlock = world.getBlockAt(x, y, z);
        chestBlock.setType(Material.CHEST, false);
        if (chestBlock.getState() instanceof Chest chest) {
            Inventory inv = chest.getBlockInventory();
            inv.addItem(
                    new ItemStack(Material.ICE, 2),
                    new ItemStack(Material.LAVA_BUCKET, 1),
                    new ItemStack(Material.BREAD, 8),
                    new ItemStack(Material.BONE_MEAL, 12),
                    new ItemStack(sapling, 2),
                    new ItemStack(seeds, 4),
                    new ItemStack(Material.TORCH, 8)
            );
        }
    }

    public static void clearStarterRegion(World world, int centerX, int baseY, int centerZ) {
        if (world == null) {
            return;
        }
        for (int dx = -16; dx <= 16; dx++) {
            for (int dz = -16; dz <= 16; dz++) {
                for (int dy = -12; dy <= 24; dy++) {
                    Block block = world.getBlockAt(centerX + dx, baseY + dy, centerZ + dz);
                    if (block.getType() != Material.AIR) {
                        block.setType(Material.AIR, false);
                    }
                }
            }
        }
    }

    public static IslandSchematic parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return CLASSIC;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return CLASSIC;
        }
    }
}
