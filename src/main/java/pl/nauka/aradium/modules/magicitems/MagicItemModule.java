package pl.nauka.aradium.modules.magicitems;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import pl.nauka.aradium.AradiumPlugin;
import pl.nauka.aradium.core.AbstractModule;
import pl.nauka.aradium.utils.ChatUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public class MagicItemModule extends AbstractModule {

    private static final float DEFAULT_FLY_SPEED = 0.1f;

    private static final Map<String, String> LEGACY_EFFECT_ALIASES = Map.of(
            "INCREASE_DAMAGE", "STRENGTH",
            "FAST_DIGGING", "HASTE",
            "JUMP", "JUMP_BOOST",
            "DAMAGE_RESISTANCE", "RESISTANCE",
            "SLOW", "SLOWNESS",
            "SLOW_DIGGING", "MINING_FATIGUE",
            "CONFUSION", "NAUSEA",
            "HARM", "INSTANT_DAMAGE",
            "HEAL", "INSTANT_HEALTH"
    );

    private record GhostSession(
            GameMode previousGameMode,
            float previousFlySpeed,
            int level,
            MagicItemDefinition definition,
            BukkitTask task
    ) {
    }

    private final NamespacedKey typeKey;
    private final NamespacedKey levelKey;
    private final Map<String, MagicItemDefinition> definitions = new LinkedHashMap<>();
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();
    private final Map<UUID, GhostSession> activeGhostSessions = new HashMap<>();
    private File cooldownsFile;

    public MagicItemModule(AradiumPlugin plugin) {
        super(plugin, "magic_items");
        this.typeKey = new NamespacedKey(plugin, "magic_type");
        this.levelKey = new NamespacedKey(plugin, "magic_level");
    }

    @Override
    protected void onEnable() {
        loadDefinitions();
        loadCooldowns();
        registerCommand("magicitem", new MagicItemCommand(plugin, this));
        registerListener(new MagicItemListener(plugin, this));
    }

    @Override
    protected void onDisable() {
        restoreAllGhostSessions();
        saveCooldowns();
        definitions.clear();
    }

    private void loadDefinitions() {
        definitions.clear();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("magic_items");
        if (section == null) {
            return;
        }

        for (String key : section.getKeys(false)) {
            ConfigurationSection itemSection = section.getConfigurationSection(key);
            if (itemSection == null) {
                continue;
            }

            String id = key.toLowerCase(Locale.ROOT);
            String name = itemSection.getString("name", key);
            String matName = itemSection.getString("material", "DIRT");
            Material material = Material.matchMaterial(matName);
            if (material == null) {
                plugin.getLogger().warning("Nieprawidłowy materiał '" + matName + "' dla magic_items." + key + " — używam DIRT.");
                material = Material.DIRT;
            }

            String lore = itemSection.getString("lore", "");
            int cooldown = Math.max(0, itemSection.getInt("cooldown", 120));
            String ability = itemSection.getString("ability", "");
            int duration = Math.max(1, itemSection.getInt("duration", 600));

            String effectName = itemSection.getString("effect_type", "");
            PotionEffectType effectType = resolvePotionEffectType(effectName);

            int spectatorSeconds = Math.max(1, itemSection.getInt("spectator_seconds", 2));
            double flySpeed = itemSection.getDouble("fly_speed", 3.0);
            int blindnessSeconds = Math.max(0, itemSection.getInt("blindness_seconds", 3));
            int speedSeconds = Math.max(0, itemSection.getInt("speed_seconds", 2));

            MagicItemDefinition definition = new MagicItemDefinition(
                    id, name, material, lore, cooldown, ability, effectType, duration,
                    spectatorSeconds, flySpeed, blindnessSeconds, speedSeconds
            );

            if (!definition.isGhostAbility() && effectType == null) {
                plugin.getLogger().warning("Nieprawidłowy effect_type '" + effectName + "' dla magic_items." + key + ".");
            }

            definitions.put(id, definition);
        }
    }

    private PotionEffectType resolvePotionEffectType(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return null;
        }
        String trimmed = rawName.trim();
        String upper = trimmed.toUpperCase(Locale.ROOT);
        String normalized = LEGACY_EFFECT_ALIASES.getOrDefault(upper, trimmed).toLowerCase(Locale.ROOT);

        NamespacedKey key = NamespacedKey.fromString(normalized);
        if (key == null) {
            return null;
        }
        return Registry.MOB_EFFECT.get(key);
    }

    public static float toBukkitFlySpeed(double configuredSpeed) {
        if (configuredSpeed <= 0.0 || Double.isNaN(configuredSpeed) || Double.isInfinite(configuredSpeed)) {
            return DEFAULT_FLY_SPEED;
        }
        if (configuredSpeed > 1.0) {
            return Math.min(1.0f, (float) (configuredSpeed / 10.0));
        }
        return (float) configuredSpeed;
    }

    public void activateGhostMode(Player player, MagicItemDefinition definition, int level) {
        UUID playerId = player.getUniqueId();
        GhostSession existing = activeGhostSessions.remove(playerId);

        GameMode previousMode;
        float previousFlySpeed;
        if (existing != null) {
            existing.task().cancel();
            previousMode = existing.previousGameMode();
            previousFlySpeed = existing.previousFlySpeed();
        } else {
            previousMode = player.getGameMode();
            previousFlySpeed = player.getFlySpeed();
        }

        player.setGameMode(GameMode.SPECTATOR);
        player.setFlySpeed(toBukkitFlySpeed(definition.flySpeed()));

        long delayTicks = Math.max(1L, definition.spectatorSeconds()) * 20L;
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> finishGhostMode(playerId, true), delayTicks);
        activeGhostSessions.put(playerId, new GhostSession(previousMode, previousFlySpeed, level, definition, task));
    }

    public void finishGhostMode(UUID playerId, boolean applyPostEffects) {
        GhostSession session = activeGhostSessions.remove(playerId);
        if (session == null) {
            return;
        }
        session.task().cancel();

        Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            return;
        }

        player.setGameMode(session.previousGameMode());
        player.setFlySpeed(session.previousFlySpeed());

        if (applyPostEffects) {
            MagicItemDefinition def = session.definition();
            if (def.blindnessSeconds() > 0) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, def.blindnessSeconds() * 20, 0));
            }
            if (def.speedSeconds() > 0) {
                int speedAmplifier = Math.max(0, session.level() - 1);
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, def.speedSeconds() * 20, speedAmplifier));
            }
        }
    }

    public boolean isInGhostMode(UUID playerId) {
        return playerId != null && activeGhostSessions.containsKey(playerId);
    }

    private void restoreAllGhostSessions() {
        for (UUID playerId : new ArrayList<>(activeGhostSessions.keySet())) {
            finishGhostMode(playerId, false);
        }
    }

    public ItemStack createMagicItem(String type, int level) {
        if (type == null || level < 1 || level > 255) {
            return null;
        }

        MagicItemDefinition definition = definitions.get(type.toLowerCase(Locale.ROOT));
        if (definition == null) {
            return null;
        }

        ItemStack item = new ItemStack(definition.material());
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        meta.displayName(ChatUtils.itemComponent(definition.name() + " " + level));
        if (definition.lore() != null && !definition.lore().isEmpty()) {
            meta.lore(List.of(ChatUtils.itemComponent(definition.lore())));
        }

        meta.addEnchant(Enchantment.UNBREAKING, level, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(typeKey, PersistentDataType.STRING, definition.id());
        pdc.set(levelKey, PersistentDataType.INTEGER, level);

        item.setItemMeta(meta);
        return item;
    }

    public boolean isMagicItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return getMagicType(meta) != null && getMagicLevel(meta) != null;
    }

    public MagicItemDefinition getDefinition(String id) {
        if (id == null) {
            return null;
        }
        return definitions.get(id.toLowerCase(Locale.ROOT));
    }

    public Set<String> getItemIds() {
        return Collections.unmodifiableSet(definitions.keySet());
    }

    public String getMagicType(ItemMeta meta) {
        if (meta == null) {
            return null;
        }
        return meta.getPersistentDataContainer().get(typeKey, PersistentDataType.STRING);
    }

    public Integer getMagicLevel(ItemMeta meta) {
        if (meta == null) {
            return null;
        }
        return meta.getPersistentDataContainer().get(levelKey, PersistentDataType.INTEGER);
    }

    public static String normalizeItemId(String typeId) {
        return typeId == null ? null : typeId.toLowerCase(Locale.ROOT);
    }

    public static long calculateRemainingSeconds(long expiresAtMillis, long nowMillis) {
        long remainingMs = expiresAtMillis - nowMillis;
        if (remainingMs <= 0L) {
            return 0L;
        }
        return (remainingMs + 999L) / 1000L;
    }

    public long getRemainingCooldownSeconds(UUID playerId, String typeId) {
        String normalizedId = normalizeItemId(typeId);
        if (playerId == null || normalizedId == null) {
            return 0L;
        }
        Map<String, Long> playerCooldowns = cooldowns.get(playerId);
        if (playerCooldowns == null) {
            return 0L;
        }
        Long expiresAt = playerCooldowns.get(normalizedId);
        if (expiresAt == null) {
            return 0L;
        }
        long remainingSeconds = calculateRemainingSeconds(expiresAt, System.currentTimeMillis());
        if (remainingSeconds <= 0L) {
            playerCooldowns.remove(normalizedId);
            if (playerCooldowns.isEmpty()) {
                cooldowns.remove(playerId);
            }
            return 0L;
        }
        return remainingSeconds;
    }

    public void setCooldown(UUID playerId, String typeId, int cooldownSeconds) {
        String normalizedId = normalizeItemId(typeId);
        if (playerId == null || normalizedId == null || cooldownSeconds <= 0) {
            return;
        }
        long expiresAt = System.currentTimeMillis() + (cooldownSeconds * 1000L);
        cooldowns.computeIfAbsent(playerId, ignored -> new HashMap<>()).put(normalizedId, expiresAt);
    }

    public void cleanupExpiredCooldowns(UUID playerId) {
        if (playerId == null) {
            return;
        }
        Map<String, Long> playerCooldowns = cooldowns.get(playerId);
        if (playerCooldowns == null) {
            return;
        }
        long now = System.currentTimeMillis();
        boolean removed = playerCooldowns.entrySet().removeIf(entry -> entry.getValue() <= now);
        if (playerCooldowns.isEmpty()) {
            cooldowns.remove(playerId);
        }
        if (removed || cooldowns.containsKey(playerId)) {
            saveCooldowns();
        }
    }

    private void loadCooldowns() {
        cooldowns.clear();
        cooldownsFile = new File(plugin.getDataFolder(), "cooldowns.yml");
        if (!cooldownsFile.exists()) {
            return;
        }

        FileConfiguration yaml = YamlConfiguration.loadConfiguration(cooldownsFile);
        ConfigurationSection section = yaml.getConfigurationSection("cooldowns");
        if (section == null) {
            return;
        }

        long now = System.currentTimeMillis();
        for (String uuidText : section.getKeys(false)) {
            UUID playerId;
            try {
                playerId = UUID.fromString(uuidText);
            } catch (IllegalArgumentException ignored) {
                continue;
            }

            ConfigurationSection playerSection = section.getConfigurationSection(uuidText);
            if (playerSection == null) {
                continue;
            }

            Map<String, Long> active = new HashMap<>();
            for (String typeId : playerSection.getKeys(false)) {
                long expiresAt = playerSection.getLong(typeId, 0L);
                if (expiresAt > now) {
                    active.put(typeId.toLowerCase(Locale.ROOT), expiresAt);
                }
            }
            if (!active.isEmpty()) {
                cooldowns.put(playerId, active);
            }
        }
    }

    private void saveCooldowns() {
        if (cooldownsFile == null) {
            cooldownsFile = new File(plugin.getDataFolder(), "cooldowns.yml");
        }

        long now = System.currentTimeMillis();
        cooldowns.entrySet().removeIf(entry -> {
            entry.getValue().entrySet().removeIf(cd -> cd.getValue() <= now);
            return entry.getValue().isEmpty();
        });

        FileConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, Map<String, Long>> playerEntry : cooldowns.entrySet()) {
            String playerPath = "cooldowns." + playerEntry.getKey();
            for (Map.Entry<String, Long> cdEntry : playerEntry.getValue().entrySet()) {
                yaml.set(playerPath + "." + cdEntry.getKey(), cdEntry.getValue());
            }
        }

        try {
            if (cooldownsFile.getParentFile() != null) {
                cooldownsFile.getParentFile().mkdirs();
            }
            yaml.save(cooldownsFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Nie udało się zapisać pliku cooldowns.yml", e);
        }
    }
}
