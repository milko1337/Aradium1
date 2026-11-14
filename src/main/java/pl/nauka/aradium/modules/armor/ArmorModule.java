package pl.nauka.aradium.modules.armor;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import pl.nauka.aradium.AradiumPlugin;
import pl.nauka.aradium.core.AbstractModule;
import pl.nauka.aradium.utils.ChatUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Moduł odpowiedzialny za magiczne zestawy zbroi oraz aktywację bonusów pełnego setu (4/4).
 */
public class ArmorModule extends AbstractModule {

    /**
     * Aktywny zestaw oraz tier noszony przez gracza.
     */
    public record EquippedSet(String setId, int tier) {

        /**
         * Weryfikuje, czy wszystkie 4 sloty zbroi należą do tego samego zestawu i tego samego tieru.
         */
        public static Optional<EquippedSet> matchFullSet(String[] setIds, int[] tiers) {
            if (setIds == null || tiers == null || setIds.length != 4 || tiers.length != 4) {
                return Optional.empty();
            }
            String expectedSet = setIds[0];
            int expectedTier = tiers[0];
            if (expectedSet == null || expectedSet.isBlank() || expectedTier <= 0) {
                return Optional.empty();
            }
            for (int i = 1; i < 4; i++) {
                if (setIds[i] == null || !expectedSet.equalsIgnoreCase(setIds[i]) || tiers[i] != expectedTier) {
                    return Optional.empty();
                }
            }
            return Optional.of(new EquippedSet(expectedSet.toLowerCase(Locale.ROOT), expectedTier));
        }
    }

    private final NamespacedKey setKey;
    private final NamespacedKey tierKey;
    private final NamespacedKey tagKey;
    private final NamespacedKey equippedCountKey;
    private final NamespacedKey specialArmorKey;
    private final NamespacedKey maxHealthModifierKey;

    private final Map<String, ArmorSetDefinition> definitions = new LinkedHashMap<>();
    private final Map<String, SpecialArmorDefinition> specialDefinitions = new LinkedHashMap<>();
    private final Map<UUID, EquippedSet> activeSets = new HashMap<>();
    private final Set<UUID> flightGrantedPlayers = new HashSet<>();
    private final Set<UUID> fallDamageProtectedPlayers = new HashSet<>();

    public ArmorModule(AradiumPlugin plugin) {
        super(plugin, "armor");
        this.setKey = new NamespacedKey(plugin, "armor_set");
        this.tierKey = new NamespacedKey(plugin, "armor_tier");
        this.tagKey = new NamespacedKey(plugin, "armor_tag");
        this.equippedCountKey = new NamespacedKey(plugin, "armor_equipped_count");
        this.specialArmorKey = new NamespacedKey(plugin, "special_armor");
        this.maxHealthModifierKey = new NamespacedKey(plugin, "armor_set_max_health");
    }

    @Override
    protected void onEnable() {
        saveDefaultArmorConfig();
        loadDefinitions();
        registerCommand("magicarmor", new ArmorCommand(plugin, this));
        registerListener(new ArmorListener(plugin, this));

        for (Player player : Bukkit.getOnlinePlayers()) {
            evaluatePlayerArmor(player);
        }
    }

    @Override
    protected void onDisable() {
        for (UUID playerId : new ArrayList<>(activeSets.keySet())) {
            EquippedSet previous = activeSets.remove(playerId);
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && previous != null) {
                removePotionEffects(player, previous);
                updateMaxHealthModifier(player, 0.0);
            }
        }
        for (UUID playerId : new ArrayList<>(flightGrantedPlayers)) {
            flightGrantedPlayers.remove(playerId);
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                revokeFlightIfNotExempt(player);
            }
        }
        activeSets.clear();
        flightGrantedPlayers.clear();
        fallDamageProtectedPlayers.clear();
        definitions.clear();
        specialDefinitions.clear();
    }

    private void saveDefaultArmorConfig() {
        File armorFile = new File(plugin.getDataFolder(), "armor.yml");
        if (!armorFile.exists()) {
            plugin.saveResource("armor.yml", false);
        }
    }

    private void loadDefinitions() {
        definitions.clear();
        specialDefinitions.clear();
        File armorFile = new File(plugin.getDataFolder(), "armor.yml");
        FileConfiguration yaml = YamlConfiguration.loadConfiguration(armorFile);
        ConfigurationSection setsSection = yaml.getConfigurationSection("armor_sets");
        if (setsSection == null) {
            plugin.getLogger().warning("Brak sekcji 'armor_sets' w armor.yml!");
        } else {
            for (String rawSetId : setsSection.getKeys(false)) {
                ConfigurationSection setSection = setsSection.getConfigurationSection(rawSetId);
                if (setSection == null) {
                    continue;
                }

                String setId = rawSetId.toLowerCase(Locale.ROOT);
                String displayName = setSection.getString("display_name", rawSetId);
                String materialPrefix = setSection.getString("material_prefix", "IRON").toUpperCase(Locale.ROOT);
                String nbtTag = setSection.getString("nbt_tag", "aradium_armor_" + setId);
                int customModelDataBase = setSection.getInt("custom_model_data_base", 1000);

                Map<ArmorPieceType, String> pieceNames = parsePieceNames(setSection.getConfigurationSection("pieces"), displayName);
                Map<Integer, ArmorTierBonus> tiers = parseTiers(setId, setSection.getConfigurationSection("tiers"), customModelDataBase);

                definitions.put(setId, new ArmorSetDefinition(
                        setId, displayName, materialPrefix, nbtTag, customModelDataBase, pieceNames, tiers
                ));
            }
        }

        ConfigurationSection specialSection = yaml.getConfigurationSection("special_armor");
        if (specialSection == null) {
            specialSection = loadFallbackSpecialArmorSection();
        }
        if (specialSection != null) {
            parseSpecialArmorDefinitions(specialSection);
        }
    }

    private ConfigurationSection loadFallbackSpecialArmorSection() {
        try (InputStream stream = plugin.getResource("armor.yml")) {
            if (stream == null) {
                return null;
            }
            YamlConfiguration fallbackYaml = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8)
            );
            return fallbackYaml.getConfigurationSection("special_armor");
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Nie udało się wczytać domyślnej sekcji special_armor z armor.yml.", e);
            return null;
        }
    }

    private void parseSpecialArmorDefinitions(ConfigurationSection specialSection) {
        for (String rawId : specialSection.getKeys(false)) {
            ConfigurationSection itemSection = specialSection.getConfigurationSection(rawId);
            if (itemSection == null) {
                continue;
            }
            String id = rawId.toLowerCase(Locale.ROOT);
            String name = itemSection.getString("name", rawId);
            String matName = itemSection.getString("material", "LEATHER_BOOTS");
            Material material = Material.matchMaterial(matName);
            if (material == null) {
                plugin.getLogger().warning("Nieprawidłowy materiał '" + matName + "' dla special_armor." + rawId + " — używam LEATHER_BOOTS.");
                material = Material.LEATHER_BOOTS;
            }

            EquipmentSlot slot = parseEquipmentSlot(itemSection.getString("slot", "FEET"));
            boolean unbreakable = itemSection.getBoolean("unbreakable", true);
            String ability = itemSection.getString("ability", "FLIGHT");
            String nbtTag = itemSection.getString("nbt_tag", "aradium_special_" + id);
            int customModelData = itemSection.getInt("custom_model_data", 4001);
            List<String> lore = itemSection.getStringList("lore");

            specialDefinitions.put(id, new SpecialArmorDefinition(
                    id, name, material, slot, unbreakable, ability, nbtTag, customModelData, lore
            ));
        }
    }

    private EquipmentSlot parseEquipmentSlot(String rawSlot) {
        if (rawSlot == null || rawSlot.isBlank()) {
            return EquipmentSlot.FEET;
        }
        try {
            return EquipmentSlot.valueOf(rawSlot.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return EquipmentSlot.FEET;
        }
    }

    private Map<ArmorPieceType, String> parsePieceNames(ConfigurationSection piecesSection, String displayName) {
        Map<ArmorPieceType, String> result = new EnumMap<>(ArmorPieceType.class);
        for (ArmorPieceType pieceType : ArmorPieceType.values()) {
            String fallback = "&f" + displayName + " (" + pieceType.name() + ") &8[&eTier %tier%&8]";
            String name = piecesSection != null ? piecesSection.getString(pieceType.name(), fallback) : fallback;
            result.put(pieceType, name);
        }
        return result;
    }

    private Map<Integer, ArmorTierBonus> parseTiers(String setId, ConfigurationSection tiersSection, int cmdBase) {
        Map<Integer, ArmorTierBonus> result = new TreeMap<>();
        if (tiersSection == null) {
            return result;
        }

        for (String tierKeyText : tiersSection.getKeys(false)) {
            int tier;
            try {
                tier = Integer.parseInt(tierKeyText);
            } catch (NumberFormatException ignored) {
                plugin.getLogger().warning("Nieprawidłowy numer tieru '" + tierKeyText + "' w zestawie " + setId + ".");
                continue;
            }
            if (tier < 1) {
                continue;
            }

            ConfigurationSection tierSection = tiersSection.getConfigurationSection(tierKeyText);
            if (tierSection == null) {
                continue;
            }

            Map<PotionEffectType, Integer> effects = new LinkedHashMap<>();
            ConfigurationSection effectsSection = tierSection.getConfigurationSection("effects");
            if (effectsSection != null) {
                for (String effectName : effectsSection.getKeys(false)) {
                    PotionEffectType effectType = resolvePotionEffectType(effectName);
                    int level = tierSection.getInt("effects." + effectName, 0);
                    if (effectType == null) {
                        plugin.getLogger().warning("Nieznany efekt '" + effectName + "' w zestawie " + setId + " tier " + tier + ".");
                        continue;
                    }
                    if (level > 0) {
                        effects.put(effectType, level);
                    }
                }
            }

            int maxHealthLevels = Math.max(0, tierSection.getInt("max_health_levels", 0));
            List<String> lore = tierSection.getStringList("lore");
            int customModelData = tierSection.getInt("custom_model_data", cmdBase + tier);
            result.put(tier, new ArmorTierBonus(tier, effects, maxHealthLevels, lore, customModelData));
        }
        return result;
    }

    private PotionEffectType resolvePotionEffectType(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return null;
        }
        NamespacedKey key = NamespacedKey.fromString(rawName.trim().toLowerCase(Locale.ROOT));
        if (key == null) {
            return null;
        }
        return Registry.MOB_EFFECT.get(key);
    }

    public ItemStack createArmorPiece(String setId, int tier, ArmorPieceType pieceType) {
        if (setId == null || pieceType == null) {
            return null;
        }
        ArmorSetDefinition definition = definitions.get(setId.toLowerCase(Locale.ROOT));
        if (definition == null) {
            return null;
        }
        ArmorTierBonus tierBonus = definition.getTier(tier);
        if (tierBonus == null) {
            return null;
        }
        Material material = pieceType.resolveMaterial(definition.materialPrefix());
        if (material == null) {
            plugin.getLogger().warning("Nieprawidłowy materiał dla " + definition.materialPrefix() + "_" + pieceType.getMaterialSuffix());
            return null;
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        List<Component> loreComponents = tierBonus.lore().isEmpty() ? List.of() : buildLoreComponents(tierBonus, 0);
        applyCommonItemMeta(
                meta,
                definition.getPieceName(pieceType, tier),
                loreComponents,
                tier,
                tierBonus.customModelData(),
                definition.nbtTag() + "_t" + tier
        );

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(setKey, PersistentDataType.STRING, definition.id());
        pdc.set(tierKey, PersistentDataType.INTEGER, tier);
        pdc.set(tagKey, PersistentDataType.STRING, definition.nbtTag() + "_tier_" + tier);
        pdc.set(equippedCountKey, PersistentDataType.INTEGER, 0);

        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createSpecialArmor(String specialId) {
        if (specialId == null) {
            return null;
        }
        SpecialArmorDefinition definition = specialDefinitions.get(specialId.toLowerCase(Locale.ROOT));
        if (definition == null) {
            return null;
        }

        ItemStack item = new ItemStack(definition.material());
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        List<Component> loreComponents = new ArrayList<>(definition.lore().size());
        for (String line : definition.lore()) {
            loreComponents.add(ChatUtils.itemComponent(line));
        }
        applyCommonItemMeta(
                meta,
                definition.name(),
                loreComponents,
                1,
                definition.customModelData(),
                definition.nbtTag()
        );
        meta.setUnbreakable(definition.unbreakable());

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(specialArmorKey, PersistentDataType.STRING, definition.id());
        pdc.set(tagKey, PersistentDataType.STRING, definition.nbtTag());

        item.setItemMeta(meta);
        return item;
    }

    private void applyCommonItemMeta(
            ItemMeta meta,
            String displayName,
            List<Component> loreComponents,
            int enchantLevel,
            int customModelData,
            String cmdString
    ) {
        meta.displayName(ChatUtils.itemComponent(displayName));
        if (!loreComponents.isEmpty()) {
            meta.lore(loreComponents);
        }

        meta.addEnchant(Enchantment.UNBREAKING, enchantLevel, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

        CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
        cmd.setFloats(List.of((float) customModelData));
        cmd.setStrings(List.of(cmdString));
        meta.setCustomModelDataComponent(cmd);
    }

    /**
     * Formatuje linie opisu zbroi, podstawiając liczbę założonych części zestawu (0..4).
     */
    public static List<String> formatLoreLines(List<String> rawLore, int equippedCount) {
        if (rawLore == null || rawLore.isEmpty()) {
            return List.of();
        }
        int clamped = Math.max(0, Math.min(4, equippedCount));
        String countText = String.valueOf(clamped);
        String fractionText = "(" + countText + "/4)";
        List<String> formatted = new ArrayList<>(rawLore.size());
        for (String line : rawLore) {
            if (line == null) {
                continue;
            }
            formatted.add(line.replace("%equipped%", countText).replace("(4/4)", fractionText));
        }
        return formatted;
    }

    private List<Component> buildLoreComponents(ArmorTierBonus tierBonus, int equippedCount) {
        List<String> formatted = formatLoreLines(tierBonus.lore(), equippedCount);
        List<Component> loreComponents = new ArrayList<>(formatted.size());
        for (String line : formatted) {
            loreComponents.add(ChatUtils.itemComponent(line));
        }
        return loreComponents;
    }

    /**
     * Aktualizuje opis przedmiotu zbroi w oparciu o przekazaną liczbę założonych części zestawu.
     *
     * @return true, jeśli metadane przedmiotu uległy zmianie
     */
    public boolean updateItemEquippedLore(ItemStack item, int equippedCount) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        String setId = getArmorSetId(meta);
        Integer tier = getArmorTier(meta);
        if (setId == null || tier == null) {
            return false;
        }
        ArmorSetDefinition definition = getDefinition(setId);
        ArmorTierBonus tierBonus = definition != null ? definition.getTier(tier) : null;
        if (tierBonus == null || tierBonus.lore().isEmpty()) {
            return false;
        }

        int clamped = Math.max(0, Math.min(4, equippedCount));
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        Integer renderedCount = pdc.get(equippedCountKey, PersistentDataType.INTEGER);
        if (renderedCount != null && renderedCount == clamped) {
            return false;
        }

        meta.lore(buildLoreComponents(tierBonus, clamped));
        pdc.set(equippedCountKey, PersistentDataType.INTEGER, clamped);
        item.setItemMeta(meta);
        return true;
    }

    public void syncPlayerArmorLore(Player player) {
        syncPlayerArmorLore(player, null, null);
    }

    public void syncPlayerArmorLore(Player player, EquipmentSlot changedSlot, ItemStack changedItem) {
        if (player == null) {
            return;
        }
        ItemStack[] effectiveArmorSlots = resolveArmorSlots(player, changedSlot, changedItem);
        Map<EquippedSet, Integer> equippedCounts = countEquippedSets(effectiveArmorSlots);

        boolean anyUpdated = false;
        PlayerInventory inventory = player.getInventory();
        anyUpdated |= syncInventoryBySlot(inventory, equippedCounts, false);

        ItemStack cursor = player.getItemOnCursor();
        if (syncSingleItem(cursor, equippedCounts, false)) {
            player.setItemOnCursor(cursor);
            anyUpdated = true;
        }

        Inventory topInventory = player.getOpenInventory().getTopInventory();
        if (topInventory.getType() != InventoryType.CRAFTING && !topInventory.equals(inventory)) {
            anyUpdated |= syncInventoryBySlot(topInventory, equippedCounts, true);
        }

        if (anyUpdated) {
            player.updateInventory();
        }
    }

    private ItemStack[] resolveArmorSlots(Player player, EquipmentSlot changedSlot, ItemStack changedItem) {
        PlayerInventory inventory = player.getInventory();
        return new ItemStack[]{
                changedSlot == EquipmentSlot.HEAD ? changedItem : inventory.getHelmet(),
                changedSlot == EquipmentSlot.CHEST ? changedItem : inventory.getChestplate(),
                changedSlot == EquipmentSlot.LEGS ? changedItem : inventory.getLeggings(),
                changedSlot == EquipmentSlot.FEET ? changedItem : inventory.getBoots()
        };
    }

    private Map<EquippedSet, Integer> countEquippedSets(ItemStack[] armorSlots) {
        Map<EquippedSet, Integer> counts = new HashMap<>();
        for (ItemStack piece : armorSlots) {
            EquippedSet key = extractSetKey(piece);
            if (key != null) {
                counts.merge(key, 1, Integer::sum);
            }
        }
        return counts;
    }

    private EquippedSet extractSetKey(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        String setId = getArmorSetId(meta);
        Integer tier = getArmorTier(meta);
        if (setId == null || tier == null || tier <= 0) {
            return null;
        }
        return new EquippedSet(setId.toLowerCase(Locale.ROOT), tier);
    }

    private boolean syncInventoryBySlot(Inventory inventory, Map<EquippedSet, Integer> equippedCounts, boolean resetToZero) {
        if (inventory == null) {
            return false;
        }
        boolean updated = false;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (syncSingleItem(item, equippedCounts, resetToZero)) {
                inventory.setItem(slot, item);
                updated = true;
            }
        }
        return updated;
    }

    private boolean syncSingleItem(ItemStack item, Map<EquippedSet, Integer> equippedCounts, boolean resetToZero) {
        EquippedSet key = extractSetKey(item);
        if (key == null) {
            return false;
        }
        int count = resetToZero ? 0 : equippedCounts.getOrDefault(key, 0);
        return updateItemEquippedLore(item, count);
    }

    public List<ItemStack> createFullSet(String setId, int tier) {
        List<ItemStack> pieces = new ArrayList<>(4);
        for (ArmorPieceType pieceType : ArmorPieceType.values()) {
            ItemStack piece = createArmorPiece(setId, tier, pieceType);
            if (piece == null) {
                return List.of();
            }
            pieces.add(piece);
        }
        return pieces;
    }

    public boolean isMagicArmor(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return (getArmorSetId(meta) != null && getArmorTier(meta) != null) || getSpecialArmorId(meta) != null;
    }

    public String getArmorSetId(ItemMeta meta) {
        return meta != null ? meta.getPersistentDataContainer().get(setKey, PersistentDataType.STRING) : null;
    }

    public Integer getArmorTier(ItemMeta meta) {
        return meta != null ? meta.getPersistentDataContainer().get(tierKey, PersistentDataType.INTEGER) : null;
    }

    public String getSpecialArmorId(ItemMeta meta) {
        return meta != null ? meta.getPersistentDataContainer().get(specialArmorKey, PersistentDataType.STRING) : null;
    }

    public ArmorSetDefinition getDefinition(String setId) {
        return setId != null ? definitions.get(setId.toLowerCase(Locale.ROOT)) : null;
    }

    public SpecialArmorDefinition getSpecialDefinition(String specialId) {
        return specialId != null ? specialDefinitions.get(specialId.toLowerCase(Locale.ROOT)) : null;
    }

    public Set<String> getSetIds() {
        return Collections.unmodifiableSet(definitions.keySet());
    }

    public Set<String> getSpecialArmorIds() {
        return Collections.unmodifiableSet(specialDefinitions.keySet());
    }

    public static boolean canKeepFlightByGameMode(GameMode gameMode) {
        return gameMode == GameMode.CREATIVE || gameMode == GameMode.SPECTATOR;
    }

    public static boolean consumeFallProtection(Set<UUID> protectedPlayers, UUID playerId) {
        return playerId != null && protectedPlayers != null && protectedPlayers.remove(playerId);
    }

    public boolean shouldCancelFallDamage(UUID playerId) {
        return consumeFallProtection(fallDamageProtectedPlayers, playerId);
    }

    public Optional<EquippedSet> evaluatePlayerArmor(Player player) {
        return evaluatePlayerArmor(player, null, null);
    }

    public Optional<EquippedSet> evaluatePlayerArmor(Player player, EquipmentSlot changedSlot, ItemStack changedItem) {
        if (player == null) {
            return Optional.empty();
        }
        syncPlayerArmorLore(player, changedSlot, changedItem);
        evaluateSpecialBoots(player, changedSlot, changedItem);

        Optional<EquippedSet> resolved = resolveEquippedFullSet(player, changedSlot, changedItem);
        UUID playerId = player.getUniqueId();
        EquippedSet previous = activeSets.get(playerId);
        EquippedSet next = resolved.orElse(null);

        if (Objects.equals(previous, next)) {
            return resolved;
        }

        if (previous != null) {
            removePotionEffects(player, previous);
            activeSets.remove(playerId);
        }

        if (next != null) {
            ArmorSetDefinition def = definitions.get(next.setId());
            ArmorTierBonus bonus = def != null ? def.getTier(next.tier()) : null;
            if (bonus != null) {
                activeSets.put(playerId, next);
                applySetBonuses(player, bonus);
                sendSetStatusMessage(player, "messages.armor_set_activated",
                        "&aAktywowano bonus pełnego zestawu: &e" + def.displayName() + " Tier " + next.tier() + "&a!",
                        def.displayName(), next.tier());
                return resolved;
            }
        }

        updateMaxHealthModifier(player, 0.0);
        if (previous != null) {
            ArmorSetDefinition prevDef = definitions.get(previous.setId());
            String display = prevDef != null ? prevDef.displayName() : previous.setId();
            sendSetStatusMessage(player, "messages.armor_set_deactivated",
                    "&eDezaktywowano bonus zestawu: &f" + display + " Tier " + previous.tier() + "&e.",
                    display, previous.tier());
        }
        return Optional.empty();
    }

    public void refreshPlayerArmor(Player player) {
        if (player == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        EquippedSet previous = activeSets.remove(playerId);
        if (previous != null) {
            removePotionEffects(player, previous);
        }
        fallDamageProtectedPlayers.remove(playerId);
        updateMaxHealthModifier(player, 0.0);
        evaluatePlayerArmor(player);
    }

    public void handlePlayerQuit(Player player) {
        if (player == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        EquippedSet previous = activeSets.remove(playerId);
        if (previous != null) {
            removePotionEffects(player, previous);
        }
        if (flightGrantedPlayers.remove(playerId)) {
            revokeFlightIfNotExempt(player);
        }
        fallDamageProtectedPlayers.remove(playerId);
        updateMaxHealthModifier(player, 0.0);
    }

    public void evaluateSpecialBoots(Player player) {
        evaluateSpecialBoots(player, null, null);
    }

    public void evaluateSpecialBoots(Player player, EquipmentSlot changedSlot, ItemStack changedItem) {
        if (player == null) {
            return;
        }
        ItemStack boots = changedSlot == EquipmentSlot.FEET ? changedItem : player.getInventory().getBoots();
        UUID playerId = player.getUniqueId();

        if (hasFlightBoots(boots)) {
            flightGrantedPlayers.add(playerId);
            fallDamageProtectedPlayers.remove(playerId);
            if (!player.getAllowFlight()) {
                player.setAllowFlight(true);
            }
            return;
        }

        boolean hadBootsFlight = flightGrantedPlayers.remove(playerId);
        if (hadBootsFlight) {
            Entity playerEntity = player;
            if (player.isFlying() || !playerEntity.isOnGround() || player.getFallDistance() > 0.0f) {
                fallDamageProtectedPlayers.add(playerId);
            }
            revokeFlightIfNotExempt(player);
        }
    }

    private boolean hasFlightBoots(ItemStack boots) {
        if (boots == null || boots.getType().isAir() || !boots.hasItemMeta()) {
            return false;
        }
        String specialId = getSpecialArmorId(boots.getItemMeta());
        if (specialId == null) {
            return false;
        }
        SpecialArmorDefinition def = getSpecialDefinition(specialId);
        return def != null && def.isFlightAbility() && def.slot() == EquipmentSlot.FEET;
    }

    private void revokeFlightIfNotExempt(Player player) {
        if (canKeepFlightByGameMode(player.getGameMode())) {
            return;
        }
        if (player.isFlying()) {
            player.setFlying(false);
        }
        if (player.getAllowFlight()) {
            player.setAllowFlight(false);
        }
    }

    private Optional<EquippedSet> resolveEquippedFullSet(Player player, EquipmentSlot changedSlot, ItemStack changedItem) {
        ItemStack[] pieces = resolveArmorSlots(player, changedSlot, changedItem);
        String[] setIds = new String[4];
        int[] tiers = new int[4];

        for (int i = 0; i < 4; i++) {
            ItemStack piece = pieces[i];
            if (piece == null || piece.getType().isAir() || !piece.hasItemMeta()) {
                return Optional.empty();
            }
            ItemMeta meta = piece.getItemMeta();
            String pieceSetId = getArmorSetId(meta);
            Integer pieceTier = getArmorTier(meta);
            if (pieceSetId == null || pieceTier == null) {
                return Optional.empty();
            }
            setIds[i] = pieceSetId;
            tiers[i] = pieceTier;
        }

        Optional<EquippedSet> matched = EquippedSet.matchFullSet(setIds, tiers);
        if (matched.isEmpty()) {
            return Optional.empty();
        }
        EquippedSet candidate = matched.get();
        ArmorSetDefinition def = definitions.get(candidate.setId());
        if (def == null || def.getTier(candidate.tier()) == null) {
            return Optional.empty();
        }
        return matched;
    }

    private void applySetBonuses(Player player, ArmorTierBonus bonus) {
        for (Map.Entry<PotionEffectType, Integer> entry : bonus.effects().entrySet()) {
            int amplifier = Math.max(0, entry.getValue() - 1);
            PotionEffect effect = new PotionEffect(
                    entry.getKey(),
                    PotionEffect.INFINITE_DURATION,
                    amplifier,
                    false,
                    false,
                    true
            );
            player.addPotionEffect(effect);
        }
        updateMaxHealthModifier(player, ArmorTierBonus.toMaxHealthBonus(bonus.maxHealthLevels()));
    }

    private void removePotionEffects(Player player, EquippedSet previous) {
        ArmorSetDefinition definition = definitions.get(previous.setId());
        if (definition == null) {
            return;
        }
        ArmorTierBonus bonus = definition.getTier(previous.tier());
        if (bonus == null) {
            return;
        }
        for (PotionEffectType effectType : bonus.effects().keySet()) {
            player.removePotionEffect(effectType);
        }
    }

    private void updateMaxHealthModifier(Player player, double healthBonus) {
        AttributeInstance maxHealthAttr = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealthAttr == null) {
            return;
        }
        maxHealthAttr.removeModifier(maxHealthModifierKey);
        if (healthBonus > 0.0) {
            AttributeModifier modifier = new AttributeModifier(
                    maxHealthModifierKey,
                    healthBonus,
                    AttributeModifier.Operation.ADD_NUMBER
            );
            maxHealthAttr.addTransientModifier(modifier);
        }

        double maxAllowed = maxHealthAttr.getValue();
        if (player.getHealth() > maxAllowed) {
            player.setHealth(maxAllowed);
        }
    }

    private void sendSetStatusMessage(Player player, String path, String fallback, String setName, int tier) {
        String raw = plugin.getConfig().getString(path, fallback);
        if (raw == null || raw.isBlank()) {
            return;
        }
        String formatted = ChatUtils.color(raw)
                .replace("%set%", setName)
                .replace("%tier%", String.valueOf(tier));
        player.sendMessage(formatted);
    }
}
