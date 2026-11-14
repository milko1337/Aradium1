package pl.nauka.aradium.modules.spawners;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import pl.nauka.aradium.utils.ChatUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Menadżer operacji PersistentDataContainer (NBT) dla ulepszanych spawnerów
 * oraz bezpiecznego transferu metadanych pomiędzy blokiem CreatureSpawner a ItemStackiem (Silk Touch).
 */
public class SpawnerDataManager {

    private static final int DEFAULT_TIER = 1;
    private static final EntityType DEFAULT_ENTITY_TYPE = EntityType.PIG;

    private final NamespacedKey tierKey;
    private final NamespacedKey entityKey;

    public SpawnerDataManager(Plugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        this.tierKey = new NamespacedKey(plugin, "spawner_tier");
        this.entityKey = new NamespacedKey(plugin, "spawner_entity");
    }

    public int getSpawnerTier(CreatureSpawner spawner) {
        if (spawner == null) {
            return DEFAULT_TIER;
        }
        Integer stored = spawner.getPersistentDataContainer().get(tierKey, PersistentDataType.INTEGER);
        return stored != null && stored >= DEFAULT_TIER ? stored : DEFAULT_TIER;
    }

    public void setSpawnerTier(CreatureSpawner spawner, int tier) {
        if (spawner == null) {
            return;
        }
        int safeTier = Math.max(DEFAULT_TIER, tier);
        spawner.getPersistentDataContainer().set(tierKey, PersistentDataType.INTEGER, safeTier);
        spawner.update(true, false);
    }

    public EntityType getSpawnerEntityType(CreatureSpawner spawner) {
        if (spawner == null) {
            return DEFAULT_ENTITY_TYPE;
        }
        String storedEntity = spawner.getPersistentDataContainer().get(entityKey, PersistentDataType.STRING);
        EntityType parsed = parseEntityType(storedEntity);
        if (parsed != null) {
            return parsed;
        }
        EntityType spawned = spawner.getSpawnedType();
        return spawned != null ? spawned : DEFAULT_ENTITY_TYPE;
    }

    /**
     * Aplikuje parametry i tagi PDC danego tieru bezpośrednio na blok CreatureSpawner.
     */
    public void applyTierToBlock(CreatureSpawner spawner, SpawnerTierDefinition tierDef, EntityType entityType) {
        if (spawner == null || tierDef == null) {
            return;
        }

        EntityType resolvedEntity = resolveValidEntityType(entityType, spawner.getSpawnedType());
        PersistentDataContainer pdc = spawner.getPersistentDataContainer();
        pdc.set(tierKey, PersistentDataType.INTEGER, Math.max(DEFAULT_TIER, tierDef.tier()));
        pdc.set(entityKey, PersistentDataType.STRING, resolvedEntity.name());

        if (isSpawnableEntity(resolvedEntity)) {
            spawner.setSpawnedType(resolvedEntity);
        }

        applySpawnDelaysSafely(spawner, tierDef.minSpawnDelay(), tierDef.maxSpawnDelay());
        spawner.setSpawnCount(Math.max(1, tierDef.spawnCount()));
        spawner.setMaxNearbyEntities(Math.max(1, tierDef.maxNearbyEntities()));
        spawner.setRequiredPlayerRange(Math.max(1, tierDef.requiredPlayerRange()));
        spawner.update(true, false);
    }

    /**
     * Wyznacza kolejność ustawiania opóźnień spawnera, aby uniknąć IllegalArgumentException (min > max).
     */
    public static boolean shouldApplyMaxDelayFirst(int currentMaxDelay, int targetMinDelay) {
        return targetMinDelay > currentMaxDelay;
    }

    private void applySpawnDelaysSafely(CreatureSpawner spawner, int minDelay, int maxDelay) {
        int safeMin = Math.max(1, Math.min(minDelay, maxDelay));
        int safeMax = Math.max(safeMin, Math.max(minDelay, maxDelay));

        if (shouldApplyMaxDelayFirst(spawner.getMaxSpawnDelay(), safeMin)) {
            spawner.setMaxSpawnDelay(safeMax);
            spawner.setMinSpawnDelay(safeMin);
        } else {
            spawner.setMinSpawnDelay(safeMin);
            spawner.setMaxSpawnDelay(safeMax);
        }
    }

    /**
     * Tworzy przedmiot SPAWNER z zapisanym w PDC typem moba oraz Tierem ulepszenia.
     */
    public ItemStack createSpawnerItem(EntityType entityType, SpawnerTierDefinition tierDef) {
        ItemStack item = new ItemStack(Material.SPAWNER);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        EntityType resolvedEntity = resolveValidEntityType(entityType, DEFAULT_ENTITY_TYPE);
        int safeTier = tierDef != null ? Math.max(DEFAULT_TIER, tierDef.tier()) : DEFAULT_TIER;

        if (meta instanceof BlockStateMeta blockStateMeta
                && blockStateMeta.getBlockState() instanceof CreatureSpawner itemSpawner) {
            if (isSpawnableEntity(resolvedEntity)) {
                itemSpawner.setSpawnedType(resolvedEntity);
            }
            itemSpawner.getPersistentDataContainer().set(tierKey, PersistentDataType.INTEGER, safeTier);
            itemSpawner.getPersistentDataContainer().set(entityKey, PersistentDataType.STRING, resolvedEntity.name());
            blockStateMeta.setBlockState(itemSpawner);
        }

        PersistentDataContainer itemPdc = meta.getPersistentDataContainer();
        itemPdc.set(tierKey, PersistentDataType.INTEGER, safeTier);
        itemPdc.set(entityKey, PersistentDataType.STRING, resolvedEntity.name());

        String entityDisplay = formatEntityName(resolvedEntity);
        String baseName = tierDef != null ? tierDef.displayName() : "&e&lSpawner &8[&7Tier " + safeTier + "&8]";
        meta.displayName(ChatUtils.itemComponent(baseName + " &8(&f" + entityDisplay + "&8)"));

        if (tierDef != null && !tierDef.lore().isEmpty()) {
            List<Component> lore = new ArrayList<>(tierDef.lore().size() + 1);
            lore.add(ChatUtils.itemComponent("&8» &7Mob: &e" + entityDisplay));
            for (String line : tierDef.lore()) {
                lore.add(ChatUtils.itemComponent(line));
            }
            meta.lore(lore);
        }

        item.setItemMeta(meta);
        return item;
    }

    public boolean isCustomSpawnerItem(ItemStack item) {
        if (item == null || item.getType() != Material.SPAWNER || !item.hasItemMeta()) {
            return false;
        }
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        return pdc.has(tierKey, PersistentDataType.INTEGER) || pdc.has(entityKey, PersistentDataType.STRING);
    }

    public int getItemTier(ItemStack item) {
        if (item == null || item.getType() != Material.SPAWNER || !item.hasItemMeta()) {
            return DEFAULT_TIER;
        }
        ItemMeta meta = item.getItemMeta();
        Integer stored = meta.getPersistentDataContainer().get(tierKey, PersistentDataType.INTEGER);
        if (stored != null && stored >= DEFAULT_TIER) {
            return stored;
        }
        if (meta instanceof BlockStateMeta blockStateMeta
                && blockStateMeta.getBlockState() instanceof CreatureSpawner itemSpawner) {
            return getSpawnerTier(itemSpawner);
        }
        return DEFAULT_TIER;
    }

    public EntityType getItemEntityType(ItemStack item) {
        if (item == null || item.getType() != Material.SPAWNER || !item.hasItemMeta()) {
            return DEFAULT_ENTITY_TYPE;
        }
        ItemMeta meta = item.getItemMeta();
        String stored = meta.getPersistentDataContainer().get(entityKey, PersistentDataType.STRING);
        EntityType parsed = parseEntityType(stored);
        if (parsed != null) {
            return parsed;
        }
        if (meta instanceof BlockStateMeta blockStateMeta
                && blockStateMeta.getBlockState() instanceof CreatureSpawner itemSpawner) {
            return getSpawnerEntityType(itemSpawner);
        }
        return DEFAULT_ENTITY_TYPE;
    }

    /**
     * Przenosi typ moba oraz tier z niszczonego bloku (Silk Touch) na ItemStack.
     */
    public ItemStack transferBlockToItem(CreatureSpawner spawner, SpawnerTierDefinition tierDef) {
        EntityType entityType = getSpawnerEntityType(spawner);
        return createSpawnerItem(entityType, tierDef);
    }

    /**
     * Przenosi zapisany tier i typ moba z postawionego ItemStacka na nowy blok CreatureSpawner.
     */
    public void transferItemToBlock(ItemStack itemInHand, CreatureSpawner placedSpawner, SpawnerTierDefinition tierDef) {
        if (placedSpawner == null) {
            return;
        }
        EntityType entityType = getItemEntityType(itemInHand);
        if (tierDef != null) {
            applyTierToBlock(placedSpawner, tierDef, entityType);
            return;
        }

        int fallbackTier = getItemTier(itemInHand);
        PersistentDataContainer pdc = placedSpawner.getPersistentDataContainer();
        pdc.set(tierKey, PersistentDataType.INTEGER, fallbackTier);
        pdc.set(entityKey, PersistentDataType.STRING, entityType.name());
        if (isSpawnableEntity(entityType)) {
            placedSpawner.setSpawnedType(entityType);
        }
        placedSpawner.update(true, false);
    }

    public static boolean isSpawnableEntity(EntityType entityType) {
        if (entityType == null) {
            return false;
        }
        return Bukkit.getServer() == null || entityType.isSpawnable();
    }

    private EntityType resolveValidEntityType(EntityType preferred, EntityType fallback) {
        if (isSpawnableEntity(preferred)) {
            return preferred;
        }
        if (isSpawnableEntity(fallback)) {
            return fallback;
        }
        return DEFAULT_ENTITY_TYPE;
    }

    public static EntityType parseEntityType(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return null;
        }
        try {
            return EntityType.valueOf(rawName.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public static String formatEntityName(EntityType entityType) {
        if (entityType == null) {
            return "Unknown";
        }
        String[] words = entityType.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            String word = words[i];
            if (word.isEmpty()) {
                continue;
            }
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return sb.toString();
    }
}
