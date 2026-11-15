package pl.nauka.aradium.modules.spawners;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.spawner.SpawnRule;
import org.bukkit.block.spawner.SpawnerEntry;
import org.bukkit.entity.EntitySnapshot;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import pl.nauka.aradium.utils.ChatUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Menadżer operacji PersistentDataContainer (NBT) dla ulepszanych spawnerów,
 * obsługujący 4 niezależne ścieżki ulepszeń, listę odblokowanych mobów
 * oraz bezpieczny transfer metadanych pomiędzy blokiem CreatureSpawner a ItemStackiem (Silk Touch).
 */
public class SpawnerDataManager {

    private static final int DEFAULT_TIER = 1;
    private static final EntityType DEFAULT_ENTITY_TYPE = EntityType.PIG;

    private final NamespacedKey tierKey;
    private final NamespacedKey entityKey;
    private final NamespacedKey unlockedMobsKey;
    private final NamespacedKey delayLevelKey;
    private final NamespacedKey amountLevelKey;
    private final NamespacedKey rangeLevelKey;
    private final NamespacedKey lightLevelKey;

    public SpawnerDataManager(Plugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        this.tierKey = new NamespacedKey(plugin, "spawner_tier");
        this.entityKey = new NamespacedKey(plugin, "spawner_entity");
        this.unlockedMobsKey = new NamespacedKey(plugin, "spawner_unlocked_mobs");
        this.delayLevelKey = new NamespacedKey(plugin, "spawner_delay_lvl");
        this.amountLevelKey = new NamespacedKey(plugin, "spawner_amount_lvl");
        this.rangeLevelKey = new NamespacedKey(plugin, "spawner_range_lvl");
        this.lightLevelKey = new NamespacedKey(plugin, "spawner_light_lvl");
    }

    public boolean hasInitializedState(CreatureSpawner spawner) {
        if (spawner == null) {
            return false;
        }
        PersistentDataContainer pdc = spawner.getPersistentDataContainer();
        return pdc.has(entityKey, PersistentDataType.STRING) || pdc.has(delayLevelKey, PersistentDataType.INTEGER);
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
        if (parsed != null && !SpawnerModule.FORBIDDEN_BOSS_ENTITIES.contains(parsed)) {
            return parsed;
        }
        return DEFAULT_ENTITY_TYPE;
    }

    public SpawnerState getSpawnerState(CreatureSpawner spawner) {
        if (spawner == null) {
            return SpawnerState.defaultPig();
        }
        return readStateFromContainer(spawner.getPersistentDataContainer(), DEFAULT_ENTITY_TYPE);
    }

    public SpawnerState getItemState(ItemStack item) {
        if (item == null || item.getType() != Material.SPAWNER || !item.hasItemMeta()) {
            return SpawnerState.defaultPig();
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return SpawnerState.defaultPig();
        }
        PersistentDataContainer itemPdc = meta.getPersistentDataContainer();
        if (hasAnySpawnerKey(itemPdc)) {
            return readStateFromContainer(itemPdc, DEFAULT_ENTITY_TYPE);
        }
        if (meta instanceof BlockStateMeta blockStateMeta
                && blockStateMeta.getBlockState() instanceof CreatureSpawner itemSpawner
                && hasAnySpawnerKey(itemSpawner.getPersistentDataContainer())) {
            return readStateFromContainer(itemSpawner.getPersistentDataContainer(), DEFAULT_ENTITY_TYPE);
        }
        return SpawnerState.defaultPig();
    }

    private boolean hasAnySpawnerKey(PersistentDataContainer pdc) {
        return pdc.has(entityKey, PersistentDataType.STRING)
                || pdc.has(delayLevelKey, PersistentDataType.INTEGER)
                || pdc.has(tierKey, PersistentDataType.INTEGER);
    }

    private SpawnerState readStateFromContainer(PersistentDataContainer pdc, EntityType fallbackEntity) {
        EntityType storedEntity = parseEntityType(pdc.get(entityKey, PersistentDataType.STRING));
        EntityType activeEntity = (storedEntity != null && !SpawnerModule.FORBIDDEN_BOSS_ENTITIES.contains(storedEntity))
                ? storedEntity
                : fallbackEntity;

        Integer legacyTierRaw = pdc.get(tierKey, PersistentDataType.INTEGER);
        int legacyTier = legacyTierRaw != null && legacyTierRaw >= 1 ? legacyTierRaw : 1;

        Integer delayRaw = pdc.get(delayLevelKey, PersistentDataType.INTEGER);
        Integer amountRaw = pdc.get(amountLevelKey, PersistentDataType.INTEGER);
        Integer rangeRaw = pdc.get(rangeLevelKey, PersistentDataType.INTEGER);
        Integer lightRaw = pdc.get(lightLevelKey, PersistentDataType.INTEGER);

        int delayLevel = delayRaw != null ? Math.max(1, delayRaw) : legacyTier;
        int amountLevel = amountRaw != null ? Math.max(1, amountRaw) : legacyTier;
        int rangeLevel = rangeRaw != null ? Math.max(1, rangeRaw) : legacyTier;
        int lightLevel = lightRaw != null ? Math.max(0, lightRaw) : 0;

        Set<EntityType> unlocked = deserializeUnlockedEntities(
                pdc.get(unlockedMobsKey, PersistentDataType.STRING),
                activeEntity
        );

        return new SpawnerState(activeEntity, unlocked, delayLevel, amountLevel, rangeLevel, lightLevel);
    }

    private void writeStateToContainer(PersistentDataContainer pdc, SpawnerState state) {
        pdc.set(tierKey, PersistentDataType.INTEGER, Math.max(DEFAULT_TIER, state.legacyOverallTier()));
        pdc.set(entityKey, PersistentDataType.STRING, state.activeEntity().name());
        pdc.set(unlockedMobsKey, PersistentDataType.STRING, serializeUnlockedEntities(state.unlockedEntities()));
        pdc.set(delayLevelKey, PersistentDataType.INTEGER, state.delayLevel());
        pdc.set(amountLevelKey, PersistentDataType.INTEGER, state.amountLevel());
        pdc.set(rangeLevelKey, PersistentDataType.INTEGER, state.rangeLevel());
        pdc.set(lightLevelKey, PersistentDataType.INTEGER, state.lightLevel());
    }

    /**
     * Aplikuje pełny stan 4 ścieżek ulepszeń, aktywnego moba oraz regułę światła (0..15) na blok spawnera.
     */
    public void applyStateToBlock(CreatureSpawner spawner, SpawnerState state, SpawnerModule module) {
        if (spawner == null) {
            return;
        }
        SpawnerState safeState = state != null ? state : SpawnerState.defaultPig();
        EntityType resolvedEntity = resolveValidEntityType(safeState.activeEntity(), DEFAULT_ENTITY_TYPE);
        SpawnerState normalizedState = resolvedEntity == safeState.activeEntity()
                ? safeState
                : safeState.withActiveEntity(resolvedEntity);

        writeStateToContainer(spawner.getPersistentDataContainer(), normalizedState);

        SpawnerTrackLevelDefinition delayDef = module != null
                ? module.getTrackLevelDefinition(SpawnerUpgradeTrack.DELAY, normalizedState.delayLevel())
                : null;
        SpawnerTrackLevelDefinition amountDef = module != null
                ? module.getTrackLevelDefinition(SpawnerUpgradeTrack.AMOUNT, normalizedState.amountLevel())
                : null;
        SpawnerTrackLevelDefinition rangeDef = module != null
                ? module.getTrackLevelDefinition(SpawnerUpgradeTrack.RANGE, normalizedState.rangeLevel())
                : null;

        int minDelay = delayDef != null ? delayDef.minSpawnDelay() : 200;
        int maxDelay = delayDef != null ? delayDef.maxSpawnDelay() : 600;
        int spawnCount = amountDef != null ? amountDef.spawnCount() : 2;
        int maxNearby = amountDef != null ? amountDef.maxNearbyEntities() : 6;
        int playerRange = rangeDef != null ? rangeDef.requiredPlayerRange() : 16;

        applyEntityAndLightRule(spawner, resolvedEntity, normalizedState.isLightImmune());
        applySpawnDelaysSafely(spawner, minDelay, maxDelay);
        spawner.setSpawnCount(Math.max(1, spawnCount));
        spawner.setMaxNearbyEntities(Math.max(1, maxNearby));
        spawner.setRequiredPlayerRange(Math.max(1, playerRange));
        spawner.update(true, false);
    }

    private void applyEntityAndLightRule(CreatureSpawner spawner, EntityType entityType, boolean ignoreLight) {
        if (isSpawnableEntity(entityType)) {
            spawner.setSpawnedType(entityType);
        }
        if (ignoreLight) {
            try {
                EntitySnapshot snapshot = spawner.getSpawnedEntity();
                if (snapshot != null) {
                    SpawnerEntry entry = new SpawnerEntry(snapshot, 1, createIgnoreLightSpawnRule());
                    spawner.setSpawnedEntity(entry);
                    spawner.setPotentialSpawns(List.of(entry));
                }
            } catch (RuntimeException ignored) {
                // W środowisku testowym lub bez pełnego świata fallback na sam SpawnedType
            }
        } else {
            try {
                if (!spawner.getPotentialSpawns().isEmpty()) {
                    spawner.setPotentialSpawns(List.of());
                    if (isSpawnableEntity(entityType)) {
                        spawner.setSpawnedType(entityType);
                    }
                }
            } catch (RuntimeException ignored) {
            }
        }
    }

    /**
     * Reguła Paper 26.3 zezwalająca na spawn niezależnie od światła bloków i nieba (0-15).
     */
    public static SpawnRule createIgnoreLightSpawnRule() {
        return new SpawnRule(0, 15, 0, 15);
    }

    /**
     * Aplikuje parametry i tagi PDC danego tieru legacy bezpośrednio na blok CreatureSpawner.
     */
    public void applyTierToBlock(CreatureSpawner spawner, SpawnerTierDefinition tierDef, EntityType entityType) {
        if (spawner == null || tierDef == null) {
            return;
        }

        EntityType resolvedEntity = resolveValidEntityType(entityType, getSpawnerEntityType(spawner));
        SpawnerState current = getSpawnerState(spawner);
        int lvl = Math.max(DEFAULT_TIER, tierDef.tier());
        SpawnerState updated = new SpawnerState(
                resolvedEntity,
                current.unlockedEntities(),
                lvl,
                lvl,
                Math.min(lvl, 4),
                current.lightLevel()
        );
        writeStateToContainer(spawner.getPersistentDataContainer(), updated);
        spawner.getPersistentDataContainer().set(tierKey, PersistentDataType.INTEGER, lvl);

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
     * Tworzy przedmiot SPAWNER z pełnym stanem 4 ścieżek ulepszeń, aktywnym mobem i listą odblokowanych mobów.
     */
    public ItemStack createSpawnerItem(SpawnerState state, SpawnerModule module) {
        SpawnerState safeState = state != null ? state : SpawnerState.defaultPig();
        ItemStack item = new ItemStack(Material.SPAWNER);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        EntityType resolvedEntity = resolveValidEntityType(safeState.activeEntity(), DEFAULT_ENTITY_TYPE);
        SpawnerState normalized = resolvedEntity == safeState.activeEntity()
                ? safeState
                : safeState.withActiveEntity(resolvedEntity);

        if (meta instanceof BlockStateMeta blockStateMeta
                && blockStateMeta.getBlockState() instanceof CreatureSpawner itemSpawner) {
            applyEntityAndLightRule(itemSpawner, resolvedEntity, normalized.isLightImmune());
            writeStateToContainer(itemSpawner.getPersistentDataContainer(), normalized);
            blockStateMeta.setBlockState(itemSpawner);
        }

        writeStateToContainer(meta.getPersistentDataContainer(), normalized);

        String mobDisplay = module != null
                ? module.formatMobDisplayName(resolvedEntity)
                : formatEntityName(resolvedEntity);
        meta.displayName(ChatUtils.itemComponent("&e&lSpawner &8(&f" + mobDisplay + "&8)"));

        int maxDelay = module != null ? module.getMaxTrackLevel(SpawnerUpgradeTrack.DELAY) : 5;
        int maxAmount = module != null ? module.getMaxTrackLevel(SpawnerUpgradeTrack.AMOUNT) : 5;
        int maxRange = module != null ? module.getMaxTrackLevel(SpawnerUpgradeTrack.RANGE) : 4;
        int totalMobs = module != null && !module.getMobDefinitions().isEmpty()
                ? module.getMobDefinitions().size()
                : normalized.unlockedEntities().size();

        List<Component> lore = new ArrayList<>();
        lore.add(ChatUtils.itemComponent("&8» &7Aktywny mob: &e" + mobDisplay));
        lore.add(ChatUtils.itemComponent("&8» &7Czas respu: &fPoziom " + normalized.delayLevel() + "/" + maxDelay));
        lore.add(ChatUtils.itemComponent("&8» &7Ilość mobów: &fPoziom " + normalized.amountLevel() + "/" + maxAmount));
        lore.add(ChatUtils.itemComponent("&8» &7Zasięg gracza: &fPoziom " + normalized.rangeLevel() + "/" + maxRange));
        lore.add(ChatUtils.itemComponent("&8» &7Ignorowanie światła: "
                + (normalized.isLightImmune() ? "&aOdblokowane" : "&cWyłączone")));
        lore.add(ChatUtils.itemComponent("&8» &7Odblokowane moby: &e" + normalized.unlockedEntities().size() + "&8/&7" + totalMobs));
        lore.add(Component.empty());
        lore.add(ChatUtils.itemComponent("&7Postaw i kliknij &eShift + PPM&7, aby otworzyć panel spawnera."));
        meta.lore(lore);

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Tworzy przedmiot SPAWNER z zapisanym w PDC typem moba oraz Tierem ulepszenia (kompatybilność wsteczna).
     */
    public ItemStack createSpawnerItem(EntityType entityType, SpawnerTierDefinition tierDef) {
        int lvl = tierDef != null ? Math.max(DEFAULT_TIER, tierDef.tier()) : DEFAULT_TIER;
        EntityType resolved = resolveValidEntityType(entityType, DEFAULT_ENTITY_TYPE);
        SpawnerState state = new SpawnerState(
                resolved,
                Set.of(DEFAULT_ENTITY_TYPE, resolved),
                lvl,
                lvl,
                Math.min(lvl, 4),
                lvl >= 3 ? 1 : 0
        );
        return createSpawnerItem(state, null);
    }

    public boolean isCustomSpawnerItem(ItemStack item) {
        if (item == null || item.getType() != Material.SPAWNER || !item.hasItemMeta()) {
            return false;
        }
        return hasAnySpawnerKey(item.getItemMeta().getPersistentDataContainer());
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
        return getItemState(item).activeEntity();
    }

    /**
     * Przenosi pełny stan spawnera z niszczonego bloku (Silk Touch) na ItemStack.
     */
    public ItemStack transferBlockToItem(CreatureSpawner spawner, SpawnerModule module) {
        SpawnerState state = getSpawnerState(spawner);
        return createSpawnerItem(state, module);
    }

    /**
     * Przenosi typ moba oraz tier z niszczonego bloku (Silk Touch) na ItemStack (kompatybilność wsteczna).
     */
    public ItemStack transferBlockToItem(CreatureSpawner spawner, SpawnerTierDefinition tierDef) {
        SpawnerState state = getSpawnerState(spawner);
        return createSpawnerItem(state, null);
    }

    /**
     * Przenosi zapisany pełny stan spawnera z postawionego ItemStacka na blok CreatureSpawner.
     * Czysty spawner bez PDC uruchamia się bazowo ze Świnią (EntityType.PIG) i poziomami 1/1/1/0.
     */
    public void transferItemToBlock(ItemStack itemInHand, CreatureSpawner placedSpawner, SpawnerModule module) {
        if (placedSpawner == null) {
            return;
        }
        SpawnerState state = getItemState(itemInHand);
        applyStateToBlock(placedSpawner, state, module);
    }

    /**
     * Przenosi zapisany tier i typ moba z postawionego ItemStacka na blok (kompatybilność wsteczna).
     */
    public void transferItemToBlock(ItemStack itemInHand, CreatureSpawner placedSpawner, SpawnerTierDefinition tierDef) {
        if (placedSpawner == null) {
            return;
        }
        SpawnerState state = getItemState(itemInHand);
        if (tierDef != null && !isCustomSpawnerItem(itemInHand)) {
            int lvl = Math.max(DEFAULT_TIER, tierDef.tier());
            state = new SpawnerState(state.activeEntity(), state.unlockedEntities(), lvl, lvl, Math.min(lvl, 4), 0);
        }
        applyStateToBlock(placedSpawner, state, null);
    }

    public static String serializeUnlockedEntities(Set<EntityType> unlocked) {
        if (unlocked == null || unlocked.isEmpty()) {
            return DEFAULT_ENTITY_TYPE.name();
        }
        List<String> names = new ArrayList<>(unlocked.size());
        for (EntityType type : unlocked) {
            if (type != null && !SpawnerModule.FORBIDDEN_BOSS_ENTITIES.contains(type)) {
                names.add(type.name());
            }
        }
        if (names.isEmpty()) {
            return DEFAULT_ENTITY_TYPE.name();
        }
        return String.join(",", names);
    }

    public static Set<EntityType> deserializeUnlockedEntities(String serialized, EntityType activeEntity) {
        Set<EntityType> result = new LinkedHashSet<>();
        result.add(DEFAULT_ENTITY_TYPE);
        if (serialized != null && !serialized.isBlank()) {
            for (String token : serialized.split(",")) {
                EntityType parsed = parseEntityType(token);
                if (parsed != null && !SpawnerModule.FORBIDDEN_BOSS_ENTITIES.contains(parsed)) {
                    result.add(parsed);
                }
            }
        }
        if (activeEntity != null && !SpawnerModule.FORBIDDEN_BOSS_ENTITIES.contains(activeEntity)) {
            result.add(activeEntity);
        }
        return Set.copyOf(result);
    }

    public static boolean isSpawnableEntity(EntityType entityType) {
        if (entityType == null) {
            return false;
        }
        return Bukkit.getServer() == null || entityType.isSpawnable();
    }

    private EntityType resolveValidEntityType(EntityType preferred, EntityType fallback) {
        if (isSpawnableEntity(preferred) && !SpawnerModule.FORBIDDEN_BOSS_ENTITIES.contains(preferred)) {
            return preferred;
        }
        if (isSpawnableEntity(fallback) && !SpawnerModule.FORBIDDEN_BOSS_ENTITIES.contains(fallback)) {
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
