package pl.nauka.aradium.modules.customitems;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Ghast;
import org.bukkit.entity.Hoglin;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Shulker;
import org.bukkit.entity.Slime;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import pl.nauka.aradium.AradiumPlugin;
import pl.nauka.aradium.core.AbstractModule;
import pl.nauka.aradium.core.PluginModule;
import pl.nauka.aradium.modules.armor.ArmorModule;
import pl.nauka.aradium.modules.armor.ArmorPieceType;
import pl.nauka.aradium.modules.armor.ArmorSetDefinition;
import pl.nauka.aradium.utils.ChatUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Moduł dodatkowych funkcji (Etap 6): stawiane generatory cobla i rud,
 * pochodnia anty-mob, własne receptury craftingu oraz zwoje/runy ulepszeń ekwipunku.
 */
public class CustomItemModule extends AbstractModule {

    public record BlockPos(String worldName, int x, int y, int z) {

        public static BlockPos fromBlock(Block block) {
            return new BlockPos(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
        }

        public BlockPos above() {
            return new BlockPos(worldName, x, y + 1, z);
        }

        public BlockPos below() {
            return new BlockPos(worldName, x, y - 1, z);
        }

        public long distanceSquared(int otherX, int otherY, int otherZ) {
            long dx = (long) x - otherX;
            long dy = (long) y - otherY;
            long dz = (long) z - otherZ;
            return dx * dx + dy * dy + dz * dz;
        }

        public String toStorageKey() {
            String encodedWorld = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(worldName.getBytes(StandardCharsets.UTF_8));
            return encodedWorld + "_" + x + "_" + y + "_" + z;
        }

        public Block resolveBlock() {
            World world = Bukkit.getWorld(worldName);
            return world != null ? world.getBlockAt(x, y, z) : null;
        }
    }

    public record ScrollApplyOutcome(
            boolean applied,
            ItemStack updatedTarget,
            String message
    ) {
    }

    private final Logger logger;
    private final NamespacedKey customItemIdKey;
    private final NamespacedKey generatorIdKey;
    private final NamespacedKey antiMobRadiusKey;
    private final NamespacedKey scrollIdKey;

    private final EnumMap<Material, String> materialNames = new EnumMap<>(Material.class);
    private final Map<String, GeneratorDefinition> generators = new LinkedHashMap<>();
    private AntiMobTorchDefinition antiMobTorch;
    private final Map<String, UpgradeScrollDefinition> upgradeScrolls = new LinkedHashMap<>();
    private final List<NamespacedKey> registeredRecipeKeys = new ArrayList<>();

    private final Map<BlockPos, String> placedGenerators = new HashMap<>();
    private final Map<BlockPos, Integer> placedAntiMobTorches = new HashMap<>();
    private final Map<BlockPos, BukkitTask> pendingGeneratorTasks = new HashMap<>();

    private File configFile;
    private File blocksDataFile;

    public CustomItemModule(AradiumPlugin plugin) {
        super(plugin, "custom_items");
        this.logger = plugin.getLogger();
        this.customItemIdKey = new NamespacedKey(plugin, "custom_item_id");
        this.generatorIdKey = new NamespacedKey(plugin, "generator_id");
        this.antiMobRadiusKey = new NamespacedKey(plugin, "anti_mob_radius");
        this.scrollIdKey = new NamespacedKey(plugin, "scroll_id");
    }

    /**
     * Lekki konstruktor testowy do walidacji logiki i YAML bez uruchomionego serwera Bukkit.
     */
    public CustomItemModule(Logger logger) {
        super("custom_items");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.customItemIdKey = null;
        this.generatorIdKey = null;
        this.antiMobRadiusKey = null;
        this.scrollIdKey = null;
    }

    @Override
    protected void onEnable() {
        saveDefaultCustomItemsConfig();
        reload();
        loadPlacedBlocks();
        registerRecipes();
        resumePlacedGenerators();
        registerCommand("customitem", new CustomItemCommand(plugin, this));
        registerListener(new CustomItemListener(plugin, this));
    }

    @Override
    protected void onDisable() {
        cancelAllPendingGeneratorTasks();
        unregisterRecipes();
        savePlacedBlocks();
        placedGenerators.clear();
        placedAntiMobTorches.clear();
        generators.clear();
        upgradeScrolls.clear();
        materialNames.clear();
        antiMobTorch = null;
    }

    private void saveDefaultCustomItemsConfig() {
        this.configFile = new File(plugin.getDataFolder(), "custom-items.yml");
        if (!configFile.exists() && plugin.getResource("custom-items.yml") != null) {
            plugin.saveResource("custom-items.yml", false);
        }
    }

    public void reload() {
        if (configFile != null && configFile.exists()) {
            reload(YamlConfiguration.loadConfiguration(configFile));
        }
    }

    public void reload(FileConfiguration yaml) {
        materialNames.clear();
        generators.clear();
        upgradeScrolls.clear();
        antiMobTorch = null;

        if (yaml == null) {
            return;
        }

        ConfigurationSection namesSec = yaml.getConfigurationSection("material-names");
        if (namesSec != null) {
            for (String key : namesSec.getKeys(false)) {
                Material mat = Material.matchMaterial(key.trim().toUpperCase(Locale.ROOT));
                String label = namesSec.getString(key);
                if (mat != null && label != null && !label.isBlank()) {
                    materialNames.put(mat, label);
                }
            }
        }

        ConfigurationSection genSec = yaml.getConfigurationSection("generators");
        if (genSec != null) {
            for (String rawId : genSec.getKeys(false)) {
                ConfigurationSection sec = genSec.getConfigurationSection(rawId);
                if (sec == null) {
                    continue;
                }
                String id = rawId.toLowerCase(Locale.ROOT);
                String name = sec.getString("name", "&7" + rawId);
                String matName = sec.getString("material", "END_STONE");
                Material material = parseBlockMaterial(matName);
                if (material == null) {
                    logger.warning("Nieprawidłowy materiał generatora '" + matName + "' dla generators." + rawId);
                    continue;
                }
                int delayTicks = Math.max(1, sec.getInt("regen-delay-ticks", 20));
                List<String> lore = sec.getStringList("lore");

                Map<Material, Double> drops = new LinkedHashMap<>();
                double totalWeight = 0.0;
                ConfigurationSection dropsSec = sec.getConfigurationSection("drops");
                if (dropsSec != null) {
                    for (String dropMatKey : dropsSec.getKeys(false)) {
                        double weight = dropsSec.getDouble(dropMatKey, 0.0);
                        if (weight <= 0.0) {
                            continue;
                        }
                        Material dropMat = parseBlockMaterial(dropMatKey);
                        if (dropMat == null) {
                            logger.warning("Nieprawidłowy blok dropu '" + dropMatKey + "' w generators." + rawId);
                            continue;
                        }
                        drops.put(dropMat, weight);
                        totalWeight += weight;
                    }
                }
                if (drops.isEmpty() || totalWeight <= 0.0) {
                    drops.put(Material.COBBLESTONE, 100.0);
                    totalWeight = 100.0;
                }

                RecipeDefinition recipe = RecipeDefinition.parse(
                        sec.getConfigurationSection("recipe"), "generators." + rawId, logger
                );
                generators.put(id, new GeneratorDefinition(id, name, material, delayTicks, lore, drops, totalWeight, recipe));
            }
        }

        ConfigurationSection torchSec = yaml.getConfigurationSection("anti_mob_torch");
        if (torchSec != null) {
            String id = torchSec.getString("id", "anti_mob_torch").toLowerCase(Locale.ROOT);
            String name = torchSec.getString("name", "&b&lPochodnia Anty-Mob");
            String matName = torchSec.getString("material", "SOUL_TORCH");
            Material mat = parseBlockMaterial(matName);
            if (mat == null) {
                logger.warning("Nieprawidłowy materiał pochodni anty-mob: " + matName + " — użyto SOUL_TORCH.");
                mat = Material.SOUL_TORCH;
            }
            int radius = torchSec.getInt("radius", 32);
            List<String> lore = torchSec.getStringList("lore");
            Set<CreatureSpawnEvent.SpawnReason> blockedReasons = EnumSet.noneOf(CreatureSpawnEvent.SpawnReason.class);
            for (String rawReason : torchSec.getStringList("blocked-spawn-reasons")) {
                try {
                    blockedReasons.add(CreatureSpawnEvent.SpawnReason.valueOf(rawReason.trim().toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException ignored) {
                    logger.warning("Nieznany SpawnReason '" + rawReason + "' w anti_mob_torch.blocked-spawn-reasons.");
                }
            }
            RecipeDefinition recipe = RecipeDefinition.parse(
                    torchSec.getConfigurationSection("recipe"), "anti_mob_torch", logger
            );
            this.antiMobTorch = new AntiMobTorchDefinition(id, name, mat, radius, lore, blockedReasons, recipe);
        }

        ConfigurationSection scrollsSec = yaml.getConfigurationSection("upgrade_scrolls");
        if (scrollsSec != null) {
            for (String rawId : scrollsSec.getKeys(false)) {
                ConfigurationSection sec = scrollsSec.getConfigurationSection(rawId);
                if (sec == null) {
                    continue;
                }
                String id = rawId.toLowerCase(Locale.ROOT);
                String name = sec.getString("name", rawId);
                String matName = sec.getString("material", "PAPER");
                Material material = Material.matchMaterial(matName.trim().toUpperCase(Locale.ROOT));
                if (RecipeDefinition.isAirMaterial(material) || material.isLegacy()) {
                    logger.warning("Nieprawidłowy materiał zwoju '" + matName + "' dla upgrade_scrolls." + rawId);
                    continue;
                }
                UpgradeScrollDefinition.TargetCategory targetCategory = UpgradeScrollDefinition.TargetCategory.parse(
                        sec.getString("target-category"), UpgradeScrollDefinition.TargetCategory.BREAKABLE
                );
                UpgradeScrollDefinition.UpgradeMode upgradeMode = UpgradeScrollDefinition.UpgradeMode.parse(
                        sec.getString("upgrade-mode"), UpgradeScrollDefinition.UpgradeMode.ENCHANTMENT
                );
                String enchantmentKey = sec.getString("enchantment");
                int maxLevel = Math.max(1, sec.getInt("max-level", 5));
                Set<String> blocked = new LinkedHashSet<>();
                for (String blockedName : sec.getStringList("blocked-enchantments")) {
                    if (blockedName != null && !blockedName.isBlank()) {
                        blocked.add(blockedName.trim().toUpperCase(Locale.ROOT));
                    }
                }
                List<String> lore = sec.getStringList("lore");
                RecipeDefinition recipe = RecipeDefinition.parse(
                        sec.getConfigurationSection("recipe"), "upgrade_scrolls." + rawId, logger
                );
                upgradeScrolls.put(id, new UpgradeScrollDefinition(
                        id, name, material, targetCategory, upgradeMode, enchantmentKey, maxLevel, blocked, lore, recipe
                ));
            }
        }
    }

    public ItemStack createCustomItem(String rawId) {
        return createCustomItem(rawId, 1);
    }

    public ItemStack createCustomItem(String rawId, int amount) {
        if (rawId == null || rawId.isBlank()) {
            return null;
        }
        String normalized = rawId.trim().toLowerCase(Locale.ROOT);
        if (generators.containsKey(normalized)) {
            return createGeneratorItem(normalized, amount);
        }
        if (antiMobTorch != null && antiMobTorch.id().equals(normalized)) {
            return createAntiMobTorchItem(amount);
        }
        if (upgradeScrolls.containsKey(normalized)) {
            return createUpgradeScrollItem(normalized, amount);
        }
        return null;
    }

    public ItemStack createGeneratorItem(String id, int amount) {
        GeneratorDefinition def = getGenerator(id);
        if (def == null) {
            return null;
        }
        ItemStack stack = new ItemStack(def.material(), Math.max(1, Math.min(64, amount)));
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(ChatUtils.itemComponent(def.name()));
            List<String> rawLore = new ArrayList<>(def.lore());
            rawLore.addAll(def.formatDropsLore(materialNames));
            meta.lore(toComponentList(rawLore));
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            if (customItemIdKey != null) {
                pdc.set(customItemIdKey, PersistentDataType.STRING, def.id());
            }
            if (generatorIdKey != null) {
                pdc.set(generatorIdKey, PersistentDataType.STRING, def.id());
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public ItemStack createAntiMobTorchItem(int amount) {
        if (antiMobTorch == null) {
            return null;
        }
        ItemStack stack = new ItemStack(antiMobTorch.material(), Math.max(1, Math.min(64, amount)));
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(ChatUtils.itemComponent(antiMobTorch.name()));
            if (!antiMobTorch.lore().isEmpty()) {
                meta.lore(toComponentList(antiMobTorch.lore()));
            }
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            if (customItemIdKey != null) {
                pdc.set(customItemIdKey, PersistentDataType.STRING, antiMobTorch.id());
            }
            if (antiMobRadiusKey != null) {
                pdc.set(antiMobRadiusKey, PersistentDataType.INTEGER, antiMobTorch.radius());
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public ItemStack createUpgradeScrollItem(String id, int amount) {
        UpgradeScrollDefinition def = getUpgradeScroll(id);
        if (def == null) {
            return null;
        }
        ItemStack stack = new ItemStack(def.material(), Math.max(1, Math.min(64, amount)));
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(ChatUtils.itemComponent(def.name()));
            if (!def.lore().isEmpty()) {
                meta.lore(toComponentList(def.lore()));
            }
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            if (customItemIdKey != null) {
                pdc.set(customItemIdKey, PersistentDataType.STRING, def.id());
            }
            if (scrollIdKey != null) {
                pdc.set(scrollIdKey, PersistentDataType.STRING, def.id());
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private void registerRecipes() {
        unregisterRecipes();
        for (GeneratorDefinition gen : generators.values()) {
            registerSingleRecipe("gen_" + gen.id(), gen.recipe(), createGeneratorItem(gen.id(), 1));
        }
        if (antiMobTorch != null) {
            registerSingleRecipe("torch_" + antiMobTorch.id(), antiMobTorch.recipe(), createAntiMobTorchItem(1));
        }
        for (UpgradeScrollDefinition scroll : upgradeScrolls.values()) {
            registerSingleRecipe("scroll_" + scroll.id(), scroll.recipe(), createUpgradeScrollItem(scroll.id(), 1));
        }
    }

    private void registerSingleRecipe(String keySuffix, RecipeDefinition recipeDef, ItemStack result) {
        if (plugin == null || recipeDef == null || !recipeDef.isValid() || result == null) {
            return;
        }
        NamespacedKey key = new NamespacedKey(plugin, "custom_" + keySuffix);
        Bukkit.removeRecipe(key);
        ShapedRecipe shaped = new ShapedRecipe(key, result);
        shaped.shape(recipeDef.shape().toArray(String[]::new));
        for (Map.Entry<Character, Material> entry : recipeDef.ingredients().entrySet()) {
            shaped.setIngredient(entry.getKey(), entry.getValue());
        }
        if (Bukkit.addRecipe(shaped)) {
            registeredRecipeKeys.add(key);
        }
    }

    private void unregisterRecipes() {
        if (plugin == null) {
            registeredRecipeKeys.clear();
            return;
        }
        for (NamespacedKey key : registeredRecipeKeys) {
            Bukkit.removeRecipe(key);
        }
        registeredRecipeKeys.clear();
    }

    public ScrollApplyOutcome applyUpgradeScroll(ItemStack scrollItem, ItemStack targetItem) {
        String scrollId = getScrollId(scrollItem);
        if (scrollId == null) {
            return new ScrollApplyOutcome(false, null, null);
        }
        UpgradeScrollDefinition scroll = getUpgradeScroll(scrollId);
        if (scroll == null) {
            return new ScrollApplyOutcome(false, null, "&cTen zwój/runa nie jest już skonfigurowany!");
        }
        if (targetItem == null || targetItem.getType().isAir()) {
            return new ScrollApplyOutcome(false, null, "&cMusisz wskazać przedmiot do ulepszenia!");
        }
        if (isCustomItem(targetItem) || getScrollId(targetItem) != null) {
            return new ScrollApplyOutcome(false, null, "&cNie można ulepszać zwojów, run ani specjalnych bloków!");
        }

        if (scroll.upgradeMode() == UpgradeScrollDefinition.UpgradeMode.ARMOR_TIER) {
            return applyArmorTierRune(scroll, targetItem);
        }
        return applyEnchantmentScroll(scroll, targetItem);
    }

    private ScrollApplyOutcome applyEnchantmentScroll(UpgradeScrollDefinition scroll, ItemStack targetItem) {
        Enchantment targetEnchant = resolveEnchantment(scroll.enchantmentKey());
        if (targetEnchant == null) {
            return new ScrollApplyOutcome(false, null, "&cNieznane zaklęcie w konfiguracji zwoju: " + scroll.enchantmentKey());
        }

        ItemStack copy = targetItem.clone();
        ItemMeta meta = copy.getItemMeta();
        if (meta == null) {
            return new ScrollApplyOutcome(false, null, "&cTen przedmiot nie może przyjąć zaklęć!");
        }

        boolean hasBlockedConflict = false;
        for (String blockedKey : scroll.blockedEnchantments()) {
            Enchantment blocked = resolveEnchantment(blockedKey);
            if (blocked != null && meta.getEnchantLevel(blocked) > 0) {
                hasBlockedConflict = true;
                break;
            }
        }

        int currentLevel = meta.getEnchantLevel(targetEnchant);
        UpgradeScrollDefinition.ScrollEvalResult eval =
                scroll.evaluateEnchantmentUpgrade(copy.getType(), currentLevel, hasBlockedConflict);
        if (!eval.success()) {
            return new ScrollApplyOutcome(false, null, eval.errorMessage());
        }

        meta.addEnchant(targetEnchant, eval.nextLevel(), true);
        copy.setItemMeta(meta);
        String msg = ChatUtils.color("&aNałożono " + scroll.name() + " &8(&ePoziom " + eval.nextLevel() + "/" + scroll.maxLevel() + "&8)&a!");
        return new ScrollApplyOutcome(true, copy, msg);
    }

    private ScrollApplyOutcome applyArmorTierRune(UpgradeScrollDefinition scroll, ItemStack targetItem) {
        if (targetItem.getAmount() != 1) {
            return new ScrollApplyOutcome(false, null, "&cUlepszaj pojedynczy element zbroi!");
        }
        ArmorModule armorModule = plugin != null
                ? plugin.getArmorModule().filter(PluginModule::isEnabled).orElse(null)
                : null;
        if (armorModule == null) {
            return new ScrollApplyOutcome(false, null, "&cModuł magicznych zbroi jest wyłączony!");
        }

        ItemMeta targetMeta = targetItem.getItemMeta();
        if (targetMeta == null) {
            return new ScrollApplyOutcome(false, null, "&cTa runa działa wyłącznie na elementach magicznych zbroi!");
        }
        String setId = armorModule.getArmorSetId(targetMeta);
        Integer currentTier = armorModule.getArmorTier(targetMeta);
        if (setId == null || currentTier == null) {
            return new ScrollApplyOutcome(false, null, "&cTa runa działa wyłącznie na elementach magicznych zbroi (Górnik/Handlarz/Wojownik)!");
        }

        ArmorSetDefinition def = armorModule.getDefinition(setId);
        int nextTier = computeNextArmorTier(def, currentTier, scroll.maxLevel());
        if (nextTier < 0) {
            return new ScrollApplyOutcome(false, null, "&eTen element zbroi osiągnął już maksymalny Tier (&6" + currentTier + "&e)!");
        }

        ArmorPieceType pieceType = resolveArmorPieceType(targetItem.getType());
        if (pieceType == null) {
            return new ScrollApplyOutcome(false, null, "&cNie rozpoznano typu elementu zbroi!");
        }

        ItemStack upgraded = armorModule.createArmorPiece(setId, nextTier, pieceType);
        if (upgraded == null) {
            return new ScrollApplyOutcome(false, null, "&cNie udało się utworzyć kolejnego tieru tej zbroi!");
        }

        ItemMeta upgradedMeta = upgraded.getItemMeta();
        if (upgradedMeta != null) {
            for (Map.Entry<Enchantment, Integer> enchEntry : targetMeta.getEnchants().entrySet()) {
                Enchantment ench = enchEntry.getKey();
                int lvl = enchEntry.getValue();
                if (ench.equals(Enchantment.UNBREAKING)) {
                    upgradedMeta.addEnchant(ench, Math.max(nextTier, lvl), true);
                } else {
                    upgradedMeta.addEnchant(ench, lvl, true);
                }
            }
            if (targetMeta instanceof Damageable oldDmg && upgradedMeta instanceof Damageable newDmg && oldDmg.hasDamage()) {
                newDmg.setDamage(oldDmg.getDamage());
            }
            upgraded.setItemMeta(upgradedMeta);
        }

        String msg = ChatUtils.color("&aAwansowano &e" + def.displayName() + " &az &7Tier " + currentTier + " &ana &6Tier " + nextTier + "&a!");
        return new ScrollApplyOutcome(true, upgraded, msg);
    }

    public static int computeNextArmorTier(ArmorSetDefinition def, int currentTier, int scrollMaxTier) {
        if (def == null || currentTier < 1 || currentTier >= scrollMaxTier) {
            return -1;
        }
        int candidate = currentTier + 1;
        return def.getTier(candidate) != null ? candidate : -1;
    }

    public static ArmorPieceType resolveArmorPieceType(Material material) {
        if (material == null) {
            return null;
        }
        String name = material.name();
        for (ArmorPieceType pieceType : ArmorPieceType.values()) {
            if (name.endsWith("_" + pieceType.getMaterialSuffix())) {
                return pieceType;
            }
        }
        return null;
    }

    public static Enchantment resolveEnchantment(String enchantmentKey) {
        if (enchantmentKey == null || enchantmentKey.isBlank() || Bukkit.getServer() == null) {
            return null;
        }
        NamespacedKey key = NamespacedKey.fromString(enchantmentKey.trim().toLowerCase(Locale.ROOT));
        if (key == null) {
            return null;
        }
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT).get(key);
    }

    public static boolean isHostileEntity(Entity entity) {
        return entity instanceof Enemy
                || entity instanceof Monster
                || entity instanceof Slime
                || entity instanceof Phantom
                || entity instanceof Ghast
                || entity instanceof Shulker
                || entity instanceof Hoglin;
    }

    public boolean shouldBlockMobSpawn(
            boolean isHostile,
            CreatureSpawnEvent.SpawnReason reason,
            String worldName,
            int x,
            int y,
            int z
    ) {
        if (!isHostile || worldName == null || placedAntiMobTorches.isEmpty()) {
            return false;
        }
        if (antiMobTorch != null && !antiMobTorch.blocksReason(reason)) {
            return false;
        }
        if (antiMobTorch == null && !AntiMobTorchDefinition.DEFAULT_BLOCKED_REASONS.contains(reason)) {
            return false;
        }
        for (Map.Entry<BlockPos, Integer> entry : placedAntiMobTorches.entrySet()) {
            BlockPos pos = entry.getKey();
            if (!pos.worldName().equals(worldName)) {
                continue;
            }
            long radius = entry.getValue();
            if (pos.distanceSquared(x, y, z) <= radius * radius) {
                return true;
            }
        }
        return false;
    }

    public void registerPlacedGenerator(Block block, String generatorId) {
        if (block == null || generatorId == null) {
            return;
        }
        String normalized = generatorId.toLowerCase(Locale.ROOT);
        if (!generators.containsKey(normalized)) {
            return;
        }
        BlockPos pos = BlockPos.fromBlock(block);
        placedGenerators.put(pos, normalized);
        savePlacedBlocks();
        scheduleGeneratorBlockSpawn(pos);
    }

    public String unregisterPlacedGenerator(Block block) {
        if (block == null) {
            return null;
        }
        BlockPos pos = BlockPos.fromBlock(block);
        String removed = placedGenerators.remove(pos);
        BukkitTask pending = pendingGeneratorTasks.remove(pos);
        if (pending != null) {
            pending.cancel();
        }
        if (removed != null) {
            savePlacedBlocks();
        }
        return removed;
    }

    public String getPlacedGeneratorAt(Block block) {
        return block != null ? placedGenerators.get(BlockPos.fromBlock(block)) : null;
    }

    public String getPlacedGeneratorAtPos(BlockPos pos) {
        return pos != null ? placedGenerators.get(pos) : null;
    }

    public void scheduleGeneratorBlockSpawn(BlockPos generatorPos) {
        if (plugin == null || generatorPos == null) {
            return;
        }
        String genId = placedGenerators.get(generatorPos);
        GeneratorDefinition def = genId != null ? generators.get(genId) : null;
        if (def == null) {
            return;
        }

        BukkitTask existing = pendingGeneratorTasks.remove(generatorPos);
        if (existing != null) {
            existing.cancel();
        }

        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            pendingGeneratorTasks.remove(generatorPos);
            if (!isEnabled()) {
                return;
            }
            String activeId = placedGenerators.get(generatorPos);
            GeneratorDefinition activeDef = activeId != null ? generators.get(activeId) : null;
            if (activeDef == null) {
                return;
            }
            Block baseBlock = generatorPos.resolveBlock();
            if (baseBlock == null || baseBlock.getType() != activeDef.material()) {
                return;
            }
            Block targetBlock = generatorPos.above().resolveBlock();
            if (targetBlock != null && targetBlock.getType().isAir()) {
                targetBlock.setType(activeDef.rollBlock(Math.random()));
            }
        }, def.regenDelayTicks());
        pendingGeneratorTasks.put(generatorPos, task);
    }

    private void resumePlacedGenerators() {
        for (Map.Entry<BlockPos, String> entry : new ArrayList<>(placedGenerators.entrySet())) {
            GeneratorDefinition def = generators.get(entry.getValue());
            Block baseBlock = entry.getKey().resolveBlock();
            Block aboveBlock = entry.getKey().above().resolveBlock();
            if (def != null && baseBlock != null && baseBlock.getType() == def.material()
                    && aboveBlock != null && aboveBlock.getType().isAir()) {
                scheduleGeneratorBlockSpawn(entry.getKey());
            }
        }
    }

    private void cancelAllPendingGeneratorTasks() {
        for (BukkitTask task : pendingGeneratorTasks.values()) {
            task.cancel();
        }
        pendingGeneratorTasks.clear();
    }

    public void registerPlacedAntiMobTorch(Block block, int radius) {
        if (block == null) {
            return;
        }
        int safeRadius = Math.max(1, radius);
        placedAntiMobTorches.put(BlockPos.fromBlock(block), safeRadius);
        savePlacedBlocks();
    }

    public void registerPlacedAntiMobTorchPos(BlockPos pos, int radius) {
        if (pos != null) {
            placedAntiMobTorches.put(pos, Math.max(1, radius));
        }
    }

    public boolean unregisterPlacedAntiMobTorch(Block block) {
        if (block == null) {
            return false;
        }
        Integer removed = placedAntiMobTorches.remove(BlockPos.fromBlock(block));
        if (removed != null) {
            savePlacedBlocks();
            return true;
        }
        return false;
    }

    public boolean hasPlacedAntiMobTorchAt(Block block) {
        return block != null && placedAntiMobTorches.containsKey(BlockPos.fromBlock(block));
    }

    public boolean isProtectedCustomBlock(Block block) {
        if (block == null) {
            return false;
        }
        BlockPos pos = BlockPos.fromBlock(block);
        return placedGenerators.containsKey(pos) || placedAntiMobTorches.containsKey(pos);
    }

    private void loadPlacedBlocks() {
        placedGenerators.clear();
        placedAntiMobTorches.clear();
        if (plugin == null) {
            return;
        }
        this.blocksDataFile = new File(plugin.getDataFolder(), "custom_blocks_data.yml");
        if (!blocksDataFile.exists()) {
            return;
        }

        FileConfiguration yaml = YamlConfiguration.loadConfiguration(blocksDataFile);
        ConfigurationSection gensSec = yaml.getConfigurationSection("generators");
        if (gensSec != null) {
            for (String key : gensSec.getKeys(false)) {
                ConfigurationSection entry = gensSec.getConfigurationSection(key);
                if (entry == null) {
                    continue;
                }
                String world = entry.getString("world");
                String genId = entry.getString("id", "").toLowerCase(Locale.ROOT);
                if (world != null && !world.isBlank() && generators.containsKey(genId)) {
                    placedGenerators.put(new BlockPos(world, entry.getInt("x"), entry.getInt("y"), entry.getInt("z")), genId);
                }
            }
        }

        ConfigurationSection torchesSec = yaml.getConfigurationSection("anti_mob_torches");
        if (torchesSec != null) {
            for (String key : torchesSec.getKeys(false)) {
                ConfigurationSection entry = torchesSec.getConfigurationSection(key);
                if (entry == null) {
                    continue;
                }
                String world = entry.getString("world");
                int radius = Math.max(1, entry.getInt("radius", antiMobTorch != null ? antiMobTorch.radius() : 32));
                if (world != null && !world.isBlank()) {
                    placedAntiMobTorches.put(new BlockPos(world, entry.getInt("x"), entry.getInt("y"), entry.getInt("z")), radius);
                }
            }
        }
    }

    private void savePlacedBlocks() {
        if (plugin == null) {
            return;
        }
        if (blocksDataFile == null) {
            blocksDataFile = new File(plugin.getDataFolder(), "custom_blocks_data.yml");
        }
        FileConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<BlockPos, String> entry : placedGenerators.entrySet()) {
            BlockPos pos = entry.getKey();
            String path = "generators." + pos.toStorageKey();
            yaml.set(path + ".world", pos.worldName());
            yaml.set(path + ".x", pos.x());
            yaml.set(path + ".y", pos.y());
            yaml.set(path + ".z", pos.z());
            yaml.set(path + ".id", entry.getValue());
        }
        for (Map.Entry<BlockPos, Integer> entry : placedAntiMobTorches.entrySet()) {
            BlockPos pos = entry.getKey();
            String path = "anti_mob_torches." + pos.toStorageKey();
            yaml.set(path + ".world", pos.worldName());
            yaml.set(path + ".x", pos.x());
            yaml.set(path + ".y", pos.y());
            yaml.set(path + ".z", pos.z());
            yaml.set(path + ".radius", entry.getValue());
        }
        try {
            if (blocksDataFile.getParentFile() != null) {
                blocksDataFile.getParentFile().mkdirs();
            }
            yaml.save(blocksDataFile);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Nie udało się zapisać custom_blocks_data.yml", e);
        }
    }

    public String getCustomItemId(ItemStack item) {
        if (item == null || !item.hasItemMeta() || customItemIdKey == null) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null ? meta.getPersistentDataContainer().get(customItemIdKey, PersistentDataType.STRING) : null;
    }

    public String getGeneratorId(ItemStack item) {
        if (item == null || !item.hasItemMeta() || generatorIdKey == null) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null ? meta.getPersistentDataContainer().get(generatorIdKey, PersistentDataType.STRING) : null;
    }

    public Integer getAntiMobTorchRadius(ItemStack item) {
        if (item == null || !item.hasItemMeta() || antiMobRadiusKey == null) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null ? meta.getPersistentDataContainer().get(antiMobRadiusKey, PersistentDataType.INTEGER) : null;
    }

    public String getScrollId(ItemStack item) {
        if (item == null || !item.hasItemMeta() || scrollIdKey == null) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null ? meta.getPersistentDataContainer().get(scrollIdKey, PersistentDataType.STRING) : null;
    }

    public boolean isCustomItem(ItemStack item) {
        return getCustomItemId(item) != null;
    }

    public GeneratorDefinition getGenerator(String id) {
        return id != null ? generators.get(id.trim().toLowerCase(Locale.ROOT)) : null;
    }

    public Map<String, GeneratorDefinition> getGeneratorsSnapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(generators));
    }

    public AntiMobTorchDefinition getAntiMobTorch() {
        return antiMobTorch;
    }

    public UpgradeScrollDefinition getUpgradeScroll(String id) {
        return id != null ? upgradeScrolls.get(id.trim().toLowerCase(Locale.ROOT)) : null;
    }

    public Map<String, UpgradeScrollDefinition> getUpgradeScrollsSnapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(upgradeScrolls));
    }

    public Set<String> getAllCustomItemIds() {
        Set<String> all = new LinkedHashSet<>(generators.keySet());
        if (antiMobTorch != null) {
            all.add(antiMobTorch.id());
        }
        all.addAll(upgradeScrolls.keySet());
        return Collections.unmodifiableSet(all);
    }

    public int getPlacedGeneratorCount() {
        return placedGenerators.size();
    }

    public int getPlacedAntiMobTorchCount() {
        return placedAntiMobTorches.size();
    }

    public static void dropItemAtBlock(Block block, ItemStack stack) {
        if (block == null || stack == null || stack.getType().isAir()) {
            return;
        }
        Location dropLoc = block.getLocation().add(0.5, 0.5, 0.5);
        block.getWorld().dropItemNaturally(dropLoc, stack);
    }

    private static Material parseBlockMaterial(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return null;
        }
        Material mat = Material.matchMaterial(rawName.trim().toUpperCase(Locale.ROOT));
        if (RecipeDefinition.isAirMaterial(mat) || mat.isLegacy()
                || (Bukkit.getServer() != null && !mat.isBlock())) {
            return null;
        }
        return mat;
    }

    private static List<Component> toComponentList(List<String> lines) {
        List<Component> result = new ArrayList<>(lines.size());
        for (String line : lines) {
            result.add(line == null || line.isEmpty() ? Component.empty() : ChatUtils.itemComponent(line));
        }
        return result;
    }
}
