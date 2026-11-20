package pl.nauka.aradium.modules.customitems;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
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
import org.bukkit.entity.Player;
import org.bukkit.entity.Shulker;
import org.bukkit.entity.Slime;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
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
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Moduł dodatkowych funkcji (Etap 6): stawiane generatory cobla i rud,
 * pochodnia anty-mob, nietypowe ulepszenia mechaniczne (2x2/3x3, Hutnik, Drwal,
 * Żniwiarz 3x3, Wampiryzm, Piorun, Double Jump, Awans Zbroi) oraz system Custom Recipes (recipes.yml).
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
    private final NamespacedKey appliedUpgradesKey;

    private final EnumMap<Material, String> materialNames = new EnumMap<>(Material.class);
    private final Map<String, GeneratorDefinition> generators = new LinkedHashMap<>();
    private AntiMobTorchDefinition antiMobTorch;
    private final Map<String, UpgradeScrollDefinition> upgradeScrolls = new LinkedHashMap<>();

    private final Map<BlockPos, String> placedGenerators = new HashMap<>();
    private final Map<BlockPos, Integer> placedAntiMobTorches = new HashMap<>();
    private final Map<BlockPos, BukkitTask> pendingGeneratorTasks = new HashMap<>();

    private CustomRecipeManager recipeManager;
    private CustomRecipeGUI recipeGUI;
    private CustomItemListener listener;

    private File configFile;
    private File blocksDataFile;

    public CustomItemModule(AradiumPlugin plugin) {
        super(plugin, "custom_items");
        this.logger = plugin.getLogger();
        this.customItemIdKey = new NamespacedKey(plugin, "custom_item_id");
        this.generatorIdKey = new NamespacedKey(plugin, "generator_id");
        this.antiMobRadiusKey = new NamespacedKey(plugin, "anti_mob_radius");
        this.scrollIdKey = new NamespacedKey(plugin, "scroll_id");
        this.appliedUpgradesKey = new NamespacedKey(plugin, "custom_upgrades");
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
        this.appliedUpgradesKey = null;
    }

    @Override
    protected void onEnable() {
        saveDefaultCustomItemsConfig();
        reload();
        loadPlacedBlocks();

        this.recipeManager = new CustomRecipeManager(plugin, this);
        this.recipeManager.saveDefaultRecipesFile();
        this.recipeManager.reload();
        this.recipeGUI = new CustomRecipeGUI(plugin, this, recipeManager);

        resumePlacedGenerators();
        this.listener = new CustomItemListener(plugin, this);
        registerCommand("customitem", new CustomItemCommand(plugin, this));
        registerCommand("recipes", new CustomRecipeCommand(plugin, this));
        registerListener(listener);

        for (Player online : Bukkit.getOnlinePlayers()) {
            listener.syncDoubleJumpState(online);
        }
    }

    @Override
    protected void onDisable() {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getOpenInventory().getTopInventory().getHolder() instanceof CustomRecipeGuiHolder) {
                online.closeInventory();
            }
            if (listener != null) {
                listener.clearDoubleJumpOnDisable(online);
            }
        }
        cancelAllPendingGeneratorTasks();
        if (recipeManager != null) {
            recipeManager.unregisterAllRecipes();
        }
        savePlacedBlocks();
        placedGenerators.clear();
        placedAntiMobTorches.clear();
        generators.clear();
        upgradeScrolls.clear();
        materialNames.clear();
        antiMobTorch = null;
        recipeGUI = null;
        listener = null;
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

                generators.put(id, new GeneratorDefinition(id, name, material, delayTicks, lore, drops, totalWeight));
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
            this.antiMobTorch = new AntiMobTorchDefinition(id, name, mat, radius, lore, blockedReasons);
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
                String displayTag = sec.getString("display-tag");
                String matName = sec.getString("material", "PAPER");
                Material material = Material.matchMaterial(matName.trim().toUpperCase(Locale.ROOT));
                if (RecipeDefinition.isAirMaterial(material) || material.isLegacy()) {
                    logger.warning("Nieprawidłowy materiał zwoju '" + matName + "' dla upgrade_scrolls." + rawId);
                    continue;
                }
                UpgradeScrollDefinition.TargetCategory targetCategory = UpgradeScrollDefinition.TargetCategory.parse(
                        sec.getString("target-category"), UpgradeScrollDefinition.TargetCategory.ANY_TOOL
                );
                UpgradeScrollDefinition.CustomUpgradeAbility ability =
                        UpgradeScrollDefinition.CustomUpgradeAbility.parse(sec.getString("ability"));
                if (ability == null) {
                    logger.warning("Nieznana zdolność ulepszenia '" + sec.getString("ability") + "' w upgrade_scrolls." + rawId);
                    continue;
                }
                double value = sec.getDouble("value", 1.0);
                int maxLevel = Math.max(1, sec.getInt("max-level", 1));
                Set<UpgradeScrollDefinition.CustomUpgradeAbility> incompatible =
                        EnumSet.noneOf(UpgradeScrollDefinition.CustomUpgradeAbility.class);
                for (String incRaw : sec.getStringList("incompatible-abilities")) {
                    UpgradeScrollDefinition.CustomUpgradeAbility parsed =
                            UpgradeScrollDefinition.CustomUpgradeAbility.parse(incRaw);
                    if (parsed != null) {
                        incompatible.add(parsed);
                    }
                }
                List<String> lore = sec.getStringList("lore");
                upgradeScrolls.put(id, new UpgradeScrollDefinition(
                        id, name, displayTag, material, targetCategory, ability, value, maxLevel, incompatible, lore
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

    public Set<UpgradeScrollDefinition.CustomUpgradeAbility> getAppliedUpgrades(ItemStack item) {
        if (item == null || RecipeDefinition.isAirMaterial(item.getType()) || !item.hasItemMeta() || appliedUpgradesKey == null) {
            return Set.of();
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return Set.of();
        }
        String serialized = meta.getPersistentDataContainer().get(appliedUpgradesKey, PersistentDataType.STRING);
        return parseAppliedUpgrades(serialized);
    }

    public boolean hasUpgrade(ItemStack item, UpgradeScrollDefinition.CustomUpgradeAbility ability) {
        return ability != null && getAppliedUpgrades(item).contains(ability);
    }

    public static Set<UpgradeScrollDefinition.CustomUpgradeAbility> parseAppliedUpgrades(String serialized) {
        if (serialized == null || serialized.isBlank()) {
            return Set.of();
        }
        EnumSet<UpgradeScrollDefinition.CustomUpgradeAbility> result =
                EnumSet.noneOf(UpgradeScrollDefinition.CustomUpgradeAbility.class);
        for (String token : serialized.split(",")) {
            UpgradeScrollDefinition.CustomUpgradeAbility ability =
                    UpgradeScrollDefinition.CustomUpgradeAbility.parse(token);
            if (ability != null) {
                result.add(ability);
            }
        }
        return result;
    }

    public static String serializeAppliedUpgrades(Set<UpgradeScrollDefinition.CustomUpgradeAbility> abilities) {
        if (abilities == null || abilities.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (UpgradeScrollDefinition.CustomUpgradeAbility ability : abilities) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(ability.name());
        }
        return sb.toString();
    }

    public double getAbilityValue(UpgradeScrollDefinition.CustomUpgradeAbility ability, double fallback) {
        if (ability == null) {
            return fallback;
        }
        for (UpgradeScrollDefinition scroll : upgradeScrolls.values()) {
            if (scroll.ability() == ability) {
                return scroll.value();
            }
        }
        return fallback;
    }

    public ScrollApplyOutcome applyUpgradeScroll(ItemStack scrollItem, ItemStack targetItem) {
        String scrollId = getScrollId(scrollItem);
        if (scrollId == null) {
            return new ScrollApplyOutcome(false, null, null);
        }
        UpgradeScrollDefinition scroll = getUpgradeScroll(scrollId);
        if (scroll == null) {
            return new ScrollApplyOutcome(false, null, ChatUtils.color("&cTen zwój/runa nie jest już skonfigurowany!"));
        }
        if (targetItem == null || RecipeDefinition.isAirMaterial(targetItem.getType())) {
            return new ScrollApplyOutcome(false, null, ChatUtils.color("&cMusisz wskazać przedmiot do ulepszenia!"));
        }
        if (isCustomItem(targetItem) || getScrollId(targetItem) != null) {
            return new ScrollApplyOutcome(false, null, ChatUtils.color("&cNie można ulepszać zwojów, run ani specjalnych bloków!"));
        }

        Set<UpgradeScrollDefinition.CustomUpgradeAbility> existingUpgrades = getAppliedUpgrades(targetItem);
        UpgradeScrollDefinition.ScrollEvalResult eval = scroll.evaluateApplication(targetItem.getType(), existingUpgrades);
        if (!eval.success()) {
            return new ScrollApplyOutcome(false, null, ChatUtils.color(eval.errorMessage()));
        }

        if (scroll.ability() == UpgradeScrollDefinition.CustomUpgradeAbility.ARMOR_TIER) {
            return applyArmorTierRune(scroll, targetItem);
        }

        if (targetItem.getAmount() != 1) {
            return new ScrollApplyOutcome(false, null, ChatUtils.color("&cUlepszaj pojedynczy przedmiot!"));
        }

        ItemStack copy = targetItem.clone();
        ItemMeta meta = copy.getItemMeta();
        if (meta == null) {
            return new ScrollApplyOutcome(false, null, ChatUtils.color("&cTen przedmiot nie obsługuje ulepszeń!"));
        }

        EnumSet<UpgradeScrollDefinition.CustomUpgradeAbility> updatedSet =
                EnumSet.noneOf(UpgradeScrollDefinition.CustomUpgradeAbility.class);
        updatedSet.addAll(existingUpgrades);
        updatedSet.add(scroll.ability());

        if (appliedUpgradesKey != null) {
            meta.getPersistentDataContainer().set(
                    appliedUpgradesKey,
                    PersistentDataType.STRING,
                    serializeAppliedUpgrades(updatedSet)
            );
        }

        List<Component> lore = meta.hasLore() && meta.lore() != null
                ? new ArrayList<>(meta.lore())
                : new ArrayList<>();
        lore.add(ChatUtils.itemComponent(scroll.displayTag()));
        meta.lore(lore);
        copy.setItemMeta(meta);

        String msg = ChatUtils.color("&aNałożono ulepszenie " + scroll.name() + " &ana wybrany przedmiot!");
        return new ScrollApplyOutcome(true, copy, msg);
    }

    private ScrollApplyOutcome applyArmorTierRune(UpgradeScrollDefinition scroll, ItemStack targetItem) {
        if (targetItem.getAmount() != 1) {
            return new ScrollApplyOutcome(false, null, ChatUtils.color("&cUlepszaj pojedynczy element zbroi!"));
        }
        ArmorModule armorModule = plugin != null
                ? plugin.getArmorModule().filter(PluginModule::isEnabled).orElse(null)
                : null;
        if (armorModule == null) {
            return new ScrollApplyOutcome(false, null, ChatUtils.color("&cModuł magicznych zbroi jest wyłączony!"));
        }

        ItemMeta targetMeta = targetItem.getItemMeta();
        if (targetMeta == null) {
            return new ScrollApplyOutcome(false, null, ChatUtils.color("&cTa runa działa wyłącznie na elementach magicznych zbroi!"));
        }
        String setId = armorModule.getArmorSetId(targetMeta);
        Integer currentTier = armorModule.getArmorTier(targetMeta);
        if (setId == null || currentTier == null) {
            return new ScrollApplyOutcome(false, null,
                    ChatUtils.color("&cTa runa działa wyłącznie na elementach magicznych zbroi (Górnik/Handlarz/Wojownik)!"));
        }

        ArmorSetDefinition def = armorModule.getDefinition(setId);
        int nextTier = computeNextArmorTier(def, currentTier, scroll.maxLevel());
        if (nextTier < 0) {
            return new ScrollApplyOutcome(false, null,
                    ChatUtils.color("&eTen element zbroi osiągnął już maksymalny Tier (&6" + currentTier + "&e)!"));
        }

        ArmorPieceType pieceType = resolveArmorPieceType(targetItem.getType());
        if (pieceType == null) {
            return new ScrollApplyOutcome(false, null, ChatUtils.color("&cNie rozpoznano typu elementu zbroi!"));
        }

        ItemStack upgraded = armorModule.createArmorPiece(setId, nextTier, pieceType);
        if (upgraded == null) {
            return new ScrollApplyOutcome(false, null, ChatUtils.color("&cNie udało się utworzyć kolejnego tieru tej zbroi!"));
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

            // Zachowanie nietypowych ulepszeń mechanicznych (np. Double Jump na butach)
            Set<UpgradeScrollDefinition.CustomUpgradeAbility> existingCustom = getAppliedUpgrades(targetItem);
            if (!existingCustom.isEmpty() && appliedUpgradesKey != null) {
                upgradedMeta.getPersistentDataContainer().set(
                        appliedUpgradesKey,
                        PersistentDataType.STRING,
                        serializeAppliedUpgrades(existingCustom)
                );
                List<Component> lore = upgradedMeta.hasLore() && upgradedMeta.lore() != null
                        ? new ArrayList<>(upgradedMeta.lore())
                        : new ArrayList<>();
                for (UpgradeScrollDefinition.CustomUpgradeAbility ability : existingCustom) {
                    lore.add(ChatUtils.itemComponent(findAbilityDisplayTag(ability)));
                }
                upgradedMeta.lore(lore);
            }
            upgraded.setItemMeta(upgradedMeta);
        }

        String msg = ChatUtils.color("&aAwansowano &e" + def.displayName() + " &az &7Tier " + currentTier + " &ana &6Tier " + nextTier + "&a!");
        return new ScrollApplyOutcome(true, upgraded, msg);
    }

    private String findAbilityDisplayTag(UpgradeScrollDefinition.CustomUpgradeAbility ability) {
        for (UpgradeScrollDefinition scroll : upgradeScrolls.values()) {
            if (scroll.ability() == ability) {
                return scroll.displayTag();
            }
        }
        return "&d✦ Ulepszenie: &f" + ability.getLabel();
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

    /**
     * Wyznacza względne przesunięcia bloków [dx, dy, dz] dla kopania obszarowego 2x2 lub 3x3.
     */
    public static List<int[]> computeAreaMiningOffsets(BlockFace hitFace, boolean is3x3) {
        return computeAreaMiningOffsets(hitFace, is3x3, 1, 1);
    }

    public static List<int[]> computeAreaMiningOffsets(BlockFace hitFace, boolean is3x3, int signU, int signV) {
        int stepU = signU >= 0 ? 1 : -1;
        int stepV = signV >= 0 ? 1 : -1;
        List<int[]> offsets = new ArrayList<>();

        if (hitFace == BlockFace.UP || hitFace == BlockFace.DOWN) {
            if (is3x3) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        offsets.add(new int[]{dx, 0, dz});
                    }
                }
            } else {
                for (int ix = 0; ix <= 1; ix++) {
                    for (int iz = 0; iz <= 1; iz++) {
                        offsets.add(new int[]{ix * stepU, 0, iz * stepV});
                    }
                }
            }
            return offsets;
        }

        if (hitFace == BlockFace.EAST || hitFace == BlockFace.WEST) {
            if (is3x3) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        offsets.add(new int[]{0, dy, dz});
                    }
                }
            } else {
                for (int iy = 0; iy <= 1; iy++) {
                    for (int iz = 0; iz <= 1; iz++) {
                        offsets.add(new int[]{0, iy * stepV, iz * stepU});
                    }
                }
            }
            return offsets;
        }

        if (is3x3) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    offsets.add(new int[]{dx, dy, 0});
                }
            }
        } else {
            for (int ix = 0; ix <= 1; ix++) {
                for (int iy = 0; iy <= 1; iy++) {
                    offsets.add(new int[]{ix * stepU, iy * stepV, 0});
                }
            }
        }
        return offsets;
    }

    /**
     * Mapuje wykopany blok lub surową rudę na przetopiony wynik dla ulepszenia Hutnik (Auto-Smelt).
     */
    public static Material smeltDropMaterial(Material input) {
        if (input == null) {
            return null;
        }
        return switch (input) {
            case RAW_IRON, IRON_ORE, DEEPSLATE_IRON_ORE -> Material.IRON_INGOT;
            case RAW_GOLD, GOLD_ORE, DEEPSLATE_GOLD_ORE, NETHER_GOLD_ORE -> Material.GOLD_INGOT;
            case RAW_COPPER, COPPER_ORE, DEEPSLATE_COPPER_ORE -> Material.COPPER_INGOT;
            case ANCIENT_DEBRIS -> Material.NETHERITE_SCRAP;
            case COBBLESTONE -> Material.STONE;
            case SAND -> Material.GLASS;
            case RED_SAND -> Material.RED_STAINED_GLASS;
            case CLAY_BALL -> Material.BRICK;
            default -> null;
        };
    }

    /**
     * Wylicza końcowe zdrowie po leczeniu z Wampiryzmu (domyślnie 1/3 zadanych obrażeń).
     */
    public static double computeVampirismHeal(double finalDamage, double ratio, double currentHealth, double maxHealth) {
        if (finalDamage <= 0.0 || ratio <= 0.0 || currentHealth <= 0.0 || maxHealth <= 0.0) {
            return Math.max(0.0, Math.min(maxHealth, currentHealth));
        }
        return Math.min(maxHealth, currentHealth + (finalDamage * ratio));
    }

    /**
     * Zwraca materiał nasion do automatycznego sadzenia dla Żniwiarza 3x3.
     */
    public static Material resolveCropSeedMaterial(Material cropBlockMaterial) {
        if (cropBlockMaterial == null) {
            return null;
        }
        return switch (cropBlockMaterial) {
            case WHEAT -> Material.WHEAT_SEEDS;
            case CARROTS -> Material.CARROT;
            case POTATOES -> Material.POTATO;
            case BEETROOTS -> Material.BEETROOT_SEEDS;
            case NETHER_WART -> Material.NETHER_WART;
            default -> null;
        };
    }

    public static boolean shouldAllowDoubleJump(
            GameMode mode,
            boolean hasFlightBoots,
            boolean hasDoubleJumpBoots,
            boolean usedInAir
    ) {
        if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR || hasFlightBoots) {
            return false;
        }
        return hasDoubleJumpBoots && !usedInAir;
    }

    public boolean hasDoubleJumpBootsEquipped(Player player) {
        if (player == null) {
            return false;
        }
        ItemStack boots = player.getInventory().getBoots();
        return hasUpgrade(boots, UpgradeScrollDefinition.CustomUpgradeAbility.DOUBLE_JUMP);
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

    public CustomRecipeManager getRecipeManager() {
        return recipeManager;
    }

    public CustomRecipeGUI getRecipeGUI() {
        return recipeGUI;
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
