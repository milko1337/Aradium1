package pl.nauka.aradium.modules.spawners;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.util.BoundingBox;

import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Task cykliczny obsługujący spawner z wykupionym ulepszeniem GROUND_IMMUNITY ("Spawn w powietrzu").
 * Omija restrykcje SpawnPlacements vanilla Minecrafta (trawa, grzybnia, woda, lawa, pełne podłoże),
 * umożliwiając niezawodny spawn wszystkich 85 mobów nawet w próżni lub nad lejami.
 */
public class SpawnerAirSpawnTask implements Runnable {

    public static final int TICK_INTERVAL = 20;
    private static final double SPAWN_RADIUS_XZ = 3.5;
    private static final double NEARBY_CHECK_RADIUS_XZ = 4.5;
    private static final double NEARBY_CHECK_RADIUS_Y = 2.5;
    private static final int MAX_POSITION_ATTEMPTS = 12;

    private final SpawnerModule module;
    private final Map<SpawnerBlockKey, Integer> remainingTicks = new ConcurrentHashMap<>();

    public SpawnerAirSpawnTask(SpawnerModule module) {
        this.module = Objects.requireNonNull(module, "module");
    }

    public record SpawnerBlockKey(UUID worldId, int x, int y, int z) {
        public static SpawnerBlockKey fromBlock(Block block) {
            return new SpawnerBlockKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        }
    }

    public void trackSpawner(CreatureSpawner spawner, SpawnerState state) {
        if (spawner == null || state == null || !spawner.isPlaced()) {
            return;
        }
        SpawnerBlockKey key = SpawnerBlockKey.fromBlock(spawner.getBlock());
        if (!state.isGroundImmune()) {
            remainingTicks.remove(key);
            return;
        }
        int maxDelay = Math.max(TICK_INTERVAL, spawner.getMaxSpawnDelay());
        remainingTicks.compute(key, (k, existing) -> {
            if (existing == null) {
                return pickRandomDelay(spawner);
            }
            return Math.min(existing, maxDelay);
        });
    }

    public void untrackSpawner(Block block) {
        if (block != null) {
            remainingTicks.remove(SpawnerBlockKey.fromBlock(block));
        }
    }

    public void resetCooldownOnVanillaSpawn(CreatureSpawner spawner) {
        if (spawner == null || !spawner.isPlaced()) {
            return;
        }
        SpawnerDataManager dataManager = module.getDataManager();
        if (dataManager == null) {
            return;
        }
        SpawnerState state = dataManager.getSpawnerState(spawner);
        SpawnerBlockKey key = SpawnerBlockKey.fromBlock(spawner.getBlock());
        if (!state.isGroundImmune()) {
            remainingTicks.remove(key);
            return;
        }
        remainingTicks.put(key, pickRandomDelay(spawner));
    }

    public void scanLoadedChunks(SpawnerDataManager dataManager) {
        if (dataManager == null) {
            return;
        }
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                scanChunk(chunk, dataManager);
            }
        }
    }

    public void scanChunk(Chunk chunk, SpawnerDataManager dataManager) {
        if (chunk == null || dataManager == null) {
            return;
        }
        for (BlockState tile : chunk.getTileEntities()) {
            if (tile instanceof CreatureSpawner spawner && dataManager.hasInitializedState(spawner)) {
                SpawnerState state = dataManager.getSpawnerState(spawner);
                if (state.isGroundImmune()) {
                    trackSpawner(spawner, state);
                }
            }
        }
    }

    public void untrackChunk(Chunk chunk) {
        if (chunk == null) {
            return;
        }
        UUID worldId = chunk.getWorld().getUID();
        int chunkX = chunk.getX();
        int chunkZ = chunk.getZ();
        remainingTicks.keySet().removeIf(key ->
                key.worldId().equals(worldId)
                        && (key.x() >> 4) == chunkX
                        && (key.z() >> 4) == chunkZ);
    }

    public void clear() {
        remainingTicks.clear();
    }

    public int getTrackedCount() {
        return remainingTicks.size();
    }

    @Override
    public void run() {
        if (!module.isEnabled() || remainingTicks.isEmpty()) {
            return;
        }
        SpawnerDataManager dataManager = module.getDataManager();
        if (dataManager == null) {
            return;
        }

        Iterator<Map.Entry<SpawnerBlockKey, Integer>> iterator = remainingTicks.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<SpawnerBlockKey, Integer> entry = iterator.next();
            SpawnerBlockKey key = entry.getKey();

            World world = Bukkit.getWorld(key.worldId());
            if (world == null) {
                iterator.remove();
                continue;
            }
            if (!world.isChunkLoaded(key.x() >> 4, key.z() >> 4)) {
                continue;
            }

            Block block = world.getBlockAt(key.x(), key.y(), key.z());
            if (block.getType() != Material.SPAWNER || !(block.getState() instanceof CreatureSpawner spawner)) {
                iterator.remove();
                continue;
            }

            SpawnerState state = dataManager.getSpawnerState(spawner);
            if (!state.isGroundImmune()) {
                iterator.remove();
                continue;
            }

            int requiredRange = Math.max(1, spawner.getRequiredPlayerRange());
            if (!isPlayerWithinRange(world, key.x() + 0.5, key.y() + 0.5, key.z() + 0.5, requiredRange)) {
                continue;
            }

            int updatedTicks = entry.getValue() - TICK_INTERVAL;
            if (updatedTicks > 0) {
                entry.setValue(updatedTicks);
                continue;
            }

            int nextDelay = pickRandomDelay(spawner);
            entry.setValue(nextDelay);
            attemptMidAirWave(world, block, spawner, state, nextDelay);
        }
    }

    private void attemptMidAirWave(
            World world,
            Block spawnerBlock,
            CreatureSpawner spawner,
            SpawnerState state,
            int nextDelay
    ) {
        EntityType entityType = state.activeEntity();
        if (!SpawnerDataManager.isSpawnableEntity(entityType)
                || SpawnerModule.FORBIDDEN_BOSS_ENTITIES.contains(entityType)) {
            return;
        }

        int maxNearby = Math.max(1, spawner.getMaxNearbyEntities());
        int nearbyCount = countNearbyMatchingEntities(world, spawnerBlock, entityType);
        if (nearbyCount >= maxNearby) {
            return;
        }

        int spawnCount = Math.max(1, spawner.getSpawnCount());
        int availableSlots = Math.min(spawnCount, maxNearby - nearbyCount);
        int spawnedAny = 0;

        for (int i = 0; i < availableSlots; i++) {
            Location candidate = findPassableAirLocation(world, spawnerBlock, entityType, state.isLightImmune());
            if (candidate == null) {
                continue;
            }
            Entity spawned = world.spawnEntity(candidate, entityType, CreatureSpawnEvent.SpawnReason.SPAWNER);
            if (spawned.isValid()) {
                spawnedAny++;
            }
        }

        if (spawnedAny > 0) {
            spawner.setDelay(Math.max(TICK_INTERVAL, nextDelay));
            spawner.update(false, false);

            Location particleCenter = spawnerBlock.getLocation().add(0.5, 0.5, 0.5);
            world.spawnParticle(Particle.FLAME, particleCenter, 10, 0.4, 0.4, 0.4, 0.02);
            world.spawnParticle(Particle.SMOKE, particleCenter, 6, 0.35, 0.35, 0.35, 0.01);
        }
    }

    private Location findPassableAirLocation(
            World world,
            Block spawnerBlock,
            EntityType entityType,
            boolean ignoreLight
    ) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int spawnerX = spawnerBlock.getX();
        int spawnerY = spawnerBlock.getY();
        int spawnerZ = spawnerBlock.getZ();
        int minY = world.getMinHeight() + 1;
        int maxY = world.getMaxHeight() - 2;

        for (int attempt = 0; attempt < MAX_POSITION_ATTEMPTS; attempt++) {
            double x = spawnerX + 0.5 + random.nextDouble(-SPAWN_RADIUS_XZ, SPAWN_RADIUS_XZ);
            double y = spawnerY + random.nextInt(-1, 2);
            double z = spawnerZ + 0.5 + random.nextDouble(-SPAWN_RADIUS_XZ, SPAWN_RADIUS_XZ);

            int blockX = (int) Math.floor(x);
            int blockY = (int) Math.floor(y);
            int blockZ = (int) Math.floor(z);

            if (blockY < minY || blockY > maxY) {
                continue;
            }
            if (blockX == spawnerX && blockY == spawnerY && blockZ == spawnerZ) {
                continue;
            }

            Block feetBlock = world.getBlockAt(blockX, blockY, blockZ);
            Block headBlock = world.getBlockAt(blockX, blockY + 1, blockZ);
            if (!feetBlock.isPassable() || !headBlock.isPassable()) {
                continue;
            }
            if (!canSpawnInFluid(feetBlock.getType(), entityType)) {
                continue;
            }
            if (!ignoreLight && !passesLightRequirement(feetBlock, entityType)) {
                continue;
            }

            return new Location(world, x, y, z, random.nextFloat() * 360.0F, 0.0F);
        }
        return null;
    }

    private static boolean canSpawnInFluid(Material feetMaterial, EntityType entityType) {
        if (feetMaterial != Material.LAVA) {
            return true;
        }
        return entityType == EntityType.STRIDER
                || entityType == EntityType.MAGMA_CUBE
                || entityType == EntityType.BLAZE
                || entityType == EntityType.ZOMBIFIED_PIGLIN
                || entityType == EntityType.WITHER_SKELETON;
    }

    private static boolean passesLightRequirement(Block spawnBlock, EntityType entityType) {
        byte light = spawnBlock.getLightLevel();
        Class<? extends Entity> entityClass = entityType.getEntityClass();
        if (entityClass != null && Monster.class.isAssignableFrom(entityClass)) {
            if (entityType == EntityType.BLAZE) {
                return light <= 11;
            }
            if (entityType == EntityType.SLIME || entityType == EntityType.MAGMA_CUBE) {
                return true;
            }
            return light <= 7;
        }
        if (entityClass != null && Animals.class.isAssignableFrom(entityClass) && entityType != EntityType.HOGLIN) {
            return light >= 9;
        }
        return true;
    }

    private static int countNearbyMatchingEntities(World world, Block spawnerBlock, EntityType entityType) {
        BoundingBox box = BoundingBox.of(
                spawnerBlock.getLocation().add(0.5, 0.5, 0.5),
                NEARBY_CHECK_RADIUS_XZ,
                NEARBY_CHECK_RADIUS_Y,
                NEARBY_CHECK_RADIUS_XZ
        );
        int count = 0;
        for (Entity nearby : world.getNearbyEntities(box, entity -> entity.getType() == entityType)) {
            if (nearby.isValid() && !nearby.isDead()) {
                count++;
            }
        }
        return count;
    }

    private static boolean isPlayerWithinRange(World world, double cx, double cy, double cz, int range) {
        double maxDistSq = (double) range * (double) range;
        for (Player player : world.getPlayers()) {
            if (player.isDead() || player.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            Location loc = player.getLocation();
            double dx = loc.getX() - cx;
            double dy = loc.getY() - cy;
            double dz = loc.getZ() - cz;
            if ((dx * dx + dy * dy + dz * dz) <= maxDistSq) {
                return true;
            }
        }
        return false;
    }

    private static int pickRandomDelay(CreatureSpawner spawner) {
        int minDelay = Math.max(TICK_INTERVAL, spawner.getMinSpawnDelay());
        int maxDelay = Math.max(minDelay, spawner.getMaxSpawnDelay());
        if (minDelay == maxDelay) {
            return minDelay;
        }
        return ThreadLocalRandom.current().nextInt(minDelay, maxDelay + 1);
    }
}
