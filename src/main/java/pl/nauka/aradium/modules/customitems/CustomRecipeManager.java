package pl.nauka.aradium.modules.customitems;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import pl.nauka.aradium.AradiumPlugin;
import pl.nauka.aradium.core.PluginModule;
import pl.nauka.aradium.modules.armor.ArmorModule;
import pl.nauka.aradium.modules.magicitems.MagicItemModule;
import pl.nauka.aradium.utils.ChatUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Menedżer własnych receptur craftingu (recipes.yml), odpowiadający za odczyt,
 * zapis z GUI kreatora, rejestrację w Bukkicie oraz weryfikację składników PDC.
 */
public class CustomRecipeManager {

    private final AradiumPlugin plugin;
    private final CustomItemModule customItemModule;
    private final Logger logger;
    private File recipesFile;

    private final Map<String, CustomRecipeDefinition> recipes = new LinkedHashMap<>();
    private final Map<NamespacedKey, String> registeredRecipeKeys = new LinkedHashMap<>();

    public CustomRecipeManager(AradiumPlugin plugin, CustomItemModule customItemModule) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.customItemModule = Objects.requireNonNull(customItemModule, "customItemModule");
        this.logger = plugin.getLogger();
        this.recipesFile = new File(plugin.getDataFolder(), "recipes.yml");
    }

    /**
     * Lekki konstruktor testowy do walidacji parsowania i logiki bez serwera Bukkit.
     */
    public CustomRecipeManager(CustomItemModule customItemModule, Logger logger) {
        this.plugin = null;
        this.customItemModule = customItemModule;
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public void saveDefaultRecipesFile() {
        if (plugin == null) {
            return;
        }
        if (recipesFile == null) {
            recipesFile = new File(plugin.getDataFolder(), "recipes.yml");
        }
        if (!recipesFile.exists() && plugin.getResource("recipes.yml") != null) {
            plugin.saveResource("recipes.yml", false);
        }
    }

    public synchronized void reload() {
        if (plugin != null && recipesFile != null && recipesFile.exists()) {
            reload(YamlConfiguration.loadConfiguration(recipesFile));
        }
    }

    public synchronized void reload(FileConfiguration yaml) {
        unregisterAllRecipes();
        recipes.clear();
        if (yaml == null) {
            return;
        }

        ConfigurationSection root = yaml.getConfigurationSection("recipes");
        if (root == null) {
            return;
        }

        for (String rawId : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(rawId);
            if (sec == null) {
                continue;
            }
            CustomRecipeDefinition def = parseRecipeSection(rawId, sec);
            if (def != null) {
                recipes.put(def.id(), def);
            }
        }

        registerAllRecipes();
    }

    private CustomRecipeDefinition parseRecipeSection(String rawId, ConfigurationSection sec) {
        String id = rawId.trim().toLowerCase(Locale.ROOT);
        String name = sec.getString("name", "&f" + rawId);
        CustomRecipeDefinition.RecipeCategory category = CustomRecipeDefinition.RecipeCategory.parse(
                sec.getString("category"), CustomRecipeDefinition.RecipeCategory.MISC
        );
        boolean enabled = sec.getBoolean("enabled", true);

        Map<Integer, CustomRecipeDefinition.RecipeSlotIngredient> matrixSlots = parseMatrixSlots(sec, rawId);
        CustomRecipeDefinition.RecipeResultSpec resultSpec = parseResultSpec(sec, rawId);
        if (matrixSlots.isEmpty() || resultSpec == null) {
            logger.warning("Pominięto nieprawidłową recepturę '" + rawId + "' w recipes.yml.");
            return null;
        }

        CustomRecipeDefinition definition = new CustomRecipeDefinition(
                id, name, category, enabled, matrixSlots, resultSpec
        );
        if (!definition.isValidMatrix()) {
            logger.warning("Receptura '" + rawId + "' nie posiada poprawnej siatki lub wyniku.");
            return null;
        }
        return definition;
    }

    private Map<Integer, CustomRecipeDefinition.RecipeSlotIngredient> parseMatrixSlots(
            ConfigurationSection sec,
            String contextId
    ) {
        Map<Integer, CustomRecipeDefinition.RecipeSlotIngredient> slotsMap = new LinkedHashMap<>();

        ConfigurationSection slotsSec = sec.getConfigurationSection("slots");
        if (slotsSec != null) {
            for (String slotKey : slotsSec.getKeys(false)) {
                int slotIdx;
                try {
                    slotIdx = Integer.parseInt(slotKey.trim());
                } catch (NumberFormatException ignored) {
                    continue;
                }
                if (slotIdx < 0 || slotIdx > 8) {
                    continue;
                }
                ConfigurationSection entry = slotsSec.getConfigurationSection(slotKey);
                if (entry != null) {
                    ItemStack serialized = entry.getItemStack("item");
                    String customId = entry.getString("custom-item");
                    String matName = entry.getString("material", serialized != null ? serialized.getType().name() : "");
                    Material mat = parseItemMaterial(matName);
                    if (mat == null && serialized != null && !RecipeDefinition.isAirMaterial(serialized.getType())) {
                        mat = serialized.getType();
                    }
                    if (mat != null) {
                        slotsMap.put(slotIdx, new CustomRecipeDefinition.RecipeSlotIngredient(mat, serialized, customId));
                    }
                } else {
                    String matName = slotsSec.getString(slotKey, "");
                    Material mat = parseItemMaterial(matName);
                    if (mat != null) {
                        slotsMap.put(slotIdx, new CustomRecipeDefinition.RecipeSlotIngredient(mat, null, null));
                    }
                }
            }
            if (!slotsMap.isEmpty()) {
                return slotsMap;
            }
        }

        List<String> shape = sec.getStringList("shape");
        ConfigurationSection ingSec = sec.getConfigurationSection("ingredients");
        if (shape.isEmpty() || ingSec == null) {
            return Map.of();
        }

        Map<Character, CustomRecipeDefinition.RecipeSlotIngredient> legend = new LinkedHashMap<>();
        for (String key : ingSec.getKeys(false)) {
            if (key == null || key.length() != 1) {
                continue;
            }
            ConfigurationSection ingObj = ingSec.getConfigurationSection(key);
            if (ingObj != null) {
                Material mat = parseItemMaterial(ingObj.getString("material", ""));
                String customId = ingObj.getString("custom-item");
                ItemStack exact = ingObj.getItemStack("item");
                if (mat == null && exact != null && !RecipeDefinition.isAirMaterial(exact.getType())) {
                    mat = exact.getType();
                }
                if (mat != null) {
                    legend.put(key.charAt(0), new CustomRecipeDefinition.RecipeSlotIngredient(mat, exact, customId));
                }
            } else {
                String matName = ingSec.getString(key, "");
                Material mat = parseItemMaterial(matName);
                if (mat == null) {
                    logger.warning("Nieprawidłowy składnik '" + matName + "' w recepturze '" + contextId + "'.");
                    return Map.of();
                }
                legend.put(key.charAt(0), new CustomRecipeDefinition.RecipeSlotIngredient(mat, null, null));
            }
        }

        for (int row = 0; row < Math.min(3, shape.size()); row++) {
            String rowText = shape.get(row);
            if (rowText == null) {
                continue;
            }
            for (int col = 0; col < Math.min(3, rowText.length()); col++) {
                char symbol = rowText.charAt(col);
                if (symbol == ' ') {
                    continue;
                }
                CustomRecipeDefinition.RecipeSlotIngredient ing = legend.get(symbol);
                if (ing == null) {
                    logger.warning("Brak definicji symbolu '" + symbol + "' w recepturze '" + contextId + "'.");
                    return Map.of();
                }
                slotsMap.put(row * 3 + col, ing);
            }
        }
        return slotsMap;
    }

    private CustomRecipeDefinition.RecipeResultSpec parseResultSpec(ConfigurationSection sec, String contextId) {
        String customItem = sec.getString("result-custom-item");
        String specialArmor = sec.getString("result-special-armor");
        String magicItem = sec.getString("result-magic-item");
        int magicLevel = sec.getInt("result-magic-level", 1);
        int amount = Math.max(1, Math.min(64, sec.getInt("result-amount", 1)));
        String displayName = sec.getString("result-name");
        ItemStack exactResult = sec.getItemStack("result-item");
        Material mat = parseItemMaterial(sec.getString("result-material", ""));
        if (mat == null && exactResult != null && !RecipeDefinition.isAirMaterial(exactResult.getType())) {
            mat = exactResult.getType();
        }
        if (customItem == null && specialArmor == null && magicItem == null && exactResult == null && mat == null) {
            logger.warning("Brak poprawnego wyniku w recepturze '" + contextId + "'.");
            return null;
        }
        return new CustomRecipeDefinition.RecipeResultSpec(
                customItem, specialArmor, magicItem, magicLevel, mat, amount, displayName, exactResult
        );
    }

    public ItemStack resolveResultItem(CustomRecipeDefinition def) {
        if (def == null) {
            return null;
        }
        CustomRecipeDefinition.RecipeResultSpec spec = def.resultSpec();
        ItemStack resolved = null;
        if (spec.customItemId() != null && customItemModule != null) {
            resolved = customItemModule.createCustomItem(spec.customItemId(), spec.amount());
        } else if (spec.specialArmorId() != null && plugin != null) {
            ArmorModule armor = plugin.getArmorModule().filter(PluginModule::isEnabled).orElse(null);
            if (armor != null) {
                resolved = armor.createSpecialArmor(spec.specialArmorId());
            }
        } else if (spec.magicItemId() != null && plugin != null) {
            MagicItemModule magic = plugin.getMagicItemModule().filter(PluginModule::isEnabled).orElse(null);
            if (magic != null) {
                resolved = magic.createMagicItem(spec.magicItemId(), spec.magicItemLevel());
            }
        } else if (spec.exactResult() != null) {
            resolved = spec.exactResult().clone();
        } else if (spec.material() != null) {
            resolved = new ItemStack(spec.material(), spec.amount());
            if (spec.displayName() != null && !spec.displayName().isBlank()) {
                ItemMeta meta = resolved.getItemMeta();
                if (meta != null) {
                    meta.displayName(ChatUtils.itemComponent(spec.displayName()));
                    resolved.setItemMeta(meta);
                }
            }
        }
        if (resolved != null && spec.exactResult() == null) {
            resolved.setAmount(spec.amount());
        }
        return resolved;
    }

    public ItemStack resolveIngredientPreviewItem(CustomRecipeDefinition.RecipeSlotIngredient ing) {
        if (ing == null) {
            return null;
        }
        if (ing.exactStack() != null) {
            return ing.exactStack().clone();
        }
        if (ing.customItemId() != null && customItemModule != null) {
            ItemStack custom = customItemModule.createCustomItem(ing.customItemId(), 1);
            if (custom != null) {
                return custom;
            }
        }
        return new ItemStack(ing.material(), 1);
    }

    public Material resolveResultIconMaterial(CustomRecipeDefinition def) {
        if (def == null) {
            return Material.BARRIER;
        }
        CustomRecipeDefinition.RecipeResultSpec spec = def.resultSpec();
        if (spec.material() != null) {
            return spec.material();
        }
        if (spec.exactResult() != null) {
            return spec.exactResult().getType();
        }
        if (spec.customItemId() != null && customItemModule != null) {
            var gen = customItemModule.getGenerator(spec.customItemId());
            if (gen != null) {
                return gen.material();
            }
            var scroll = customItemModule.getUpgradeScroll(spec.customItemId());
            if (scroll != null) {
                return scroll.material();
            }
            var torch = customItemModule.getAntiMobTorch();
            if (torch != null && torch.id().equals(spec.customItemId())) {
                return torch.material();
            }
        }
        if ("buty_lotnika".equals(spec.specialArmorId())) {
            return Material.LEATHER_BOOTS;
        }
        if (spec.magicItemId() != null) {
            return Material.NETHER_STAR;
        }
        return Material.CRAFTING_TABLE;
    }

    public synchronized void registerAllRecipes() {
        unregisterAllRecipes();
        if (plugin == null || Bukkit.getServer() == null) {
            return;
        }
        for (CustomRecipeDefinition def : recipes.values()) {
            if (!def.enabled()) {
                continue;
            }
            registerSingleRecipe(def);
        }
    }

    private void registerSingleRecipe(CustomRecipeDefinition def) {
        if (plugin == null || def == null || !def.enabled()) {
            return;
        }
        ItemStack result = resolveResultItem(def);
        if (result == null || RecipeDefinition.isAirMaterial(result.getType())) {
            logger.warning("Nie można zarejestrować receptury '" + def.id() + "' — brak prawidłowego wyniku.");
            return;
        }

        List<String> trimmedShape = computeTrimmedShape(def.matrixSlots());
        if (trimmedShape.isEmpty()) {
            return;
        }

        NamespacedKey key = new NamespacedKey(plugin, "recipe_" + def.id());
        Bukkit.removeRecipe(key);

        ShapedRecipe shaped = new ShapedRecipe(key, result);
        shaped.shape(trimmedShape.toArray(String[]::new));

        int minRow = findMinRow(def.matrixSlots());
        int minCol = findMinCol(def.matrixSlots());
        for (Map.Entry<Integer, CustomRecipeDefinition.RecipeSlotIngredient> entry : def.matrixSlots().entrySet()) {
            int slot = entry.getKey();
            int relRow = (slot / 3) - minRow;
            int relCol = (slot % 3) - minCol;
            char symbol = (char) ('A' + (relRow * 3 + relCol));
            shaped.setIngredient(symbol, entry.getValue().material());
        }

        if (Bukkit.addRecipe(shaped)) {
            registeredRecipeKeys.put(key, def.id());
        }
    }

    public synchronized void unregisterAllRecipes() {
        if (plugin != null && Bukkit.getServer() != null) {
            for (NamespacedKey key : registeredRecipeKeys.keySet()) {
                Bukkit.removeRecipe(key);
            }
        }
        registeredRecipeKeys.clear();
    }

    public Optional<CustomRecipeDefinition> findManagedRecipe(Recipe recipe) {
        if (!(recipe instanceof ShapedRecipe shaped)) {
            return Optional.empty();
        }
        String recipeId = registeredRecipeKeys.get(shaped.getKey());
        return recipeId != null ? Optional.ofNullable(recipes.get(recipeId)) : Optional.empty();
    }

    public boolean validateCraftMatrix(CustomRecipeDefinition def, ItemStack[] craftMatrix) {
        if (def == null || craftMatrix == null) {
            return false;
        }
        boolean hasExactConstraint = def.matrixSlots().values().stream()
                .anyMatch(CustomRecipeDefinition.RecipeSlotIngredient::hasExactItemConstraint);
        if (!hasExactConstraint) {
            return true;
        }

        int gridSize = craftMatrix.length <= 4 ? 2 : 3;
        int minRow = findMinRow(def.matrixSlots());
        int maxRow = findMaxRow(def.matrixSlots());
        int minCol = findMinCol(def.matrixSlots());
        int maxCol = findMaxCol(def.matrixSlots());
        int recipeHeight = maxRow - minRow + 1;
        int recipeWidth = maxCol - minCol + 1;
        if (recipeHeight > gridSize || recipeWidth > gridSize) {
            return false;
        }

        for (int rowOffset = 0; rowOffset <= gridSize - recipeHeight; rowOffset++) {
            for (int colOffset = 0; colOffset <= gridSize - recipeWidth; colOffset++) {
                if (matchesAtOffset(def, craftMatrix, gridSize, minRow, minCol, rowOffset, colOffset)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean matchesAtOffset(
            CustomRecipeDefinition def,
            ItemStack[] craftMatrix,
            int gridSize,
            int minRow,
            int minCol,
            int rowOffset,
            int colOffset
    ) {
        for (int r = 0; r < gridSize; r++) {
            for (int c = 0; c < gridSize; c++) {
                int craftIdx = r * gridSize + c;
                ItemStack placed = craftIdx < craftMatrix.length ? craftMatrix[craftIdx] : null;

                int origRow = (r - rowOffset) + minRow;
                int origCol = (c - colOffset) + minCol;
                CustomRecipeDefinition.RecipeSlotIngredient expected = null;
                if (origRow >= 0 && origRow < 3 && origCol >= 0 && origCol < 3) {
                    expected = def.matrixSlots().get(origRow * 3 + origCol);
                }

                if (expected == null) {
                    if (placed != null && !RecipeDefinition.isAirMaterial(placed.getType())) {
                        return false;
                    }
                    continue;
                }
                if (placed == null || RecipeDefinition.isAirMaterial(placed.getType())
                        || placed.getType() != expected.material()) {
                    return false;
                }
                if (expected.customItemId() != null) {
                    String placedId = customItemModule != null ? customItemModule.getCustomItemId(placed) : null;
                    if (!expected.customItemId().equals(placedId)) {
                        return false;
                    }
                } else if (expected.exactStack() != null) {
                    if (expected.exactStack().hasItemMeta() && !expected.exactStack().isSimilar(placed)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    public synchronized void saveRecipe(CustomRecipeDefinition def, boolean persistToDisk) {
        Objects.requireNonNull(def, "def");
        recipes.put(def.id(), def);
        registerAllRecipes();
        if (persistToDisk) {
            saveToDisk();
        }
    }

    public synchronized boolean deleteRecipe(String rawId) {
        if (rawId == null || rawId.isBlank()) {
            return false;
        }
        String normalized = rawId.trim().toLowerCase(Locale.ROOT);
        CustomRecipeDefinition removed = recipes.remove(normalized);
        if (removed == null) {
            return false;
        }
        registerAllRecipes();
        saveToDisk();
        return true;
    }

    public synchronized boolean toggleEnabled(String rawId) {
        CustomRecipeDefinition existing = getRecipe(rawId).orElse(null);
        if (existing == null) {
            return false;
        }
        CustomRecipeDefinition updated = new CustomRecipeDefinition(
                existing.id(),
                existing.name(),
                existing.category(),
                !existing.enabled(),
                existing.matrixSlots(),
                existing.resultSpec()
        );
        saveRecipe(updated, true);
        return true;
    }

    public synchronized void saveToDisk() {
        if (plugin == null) {
            return;
        }
        if (recipesFile == null) {
            recipesFile = new File(plugin.getDataFolder(), "recipes.yml");
        }
        FileConfiguration yaml = new YamlConfiguration();
        yaml.options().setHeader(List.of(
                "Konfiguracja wszystkich własnych receptur craftingu (Custom Recipes)",
                "Edytuj ręcznie lub w kreatorze w grze pod komendą: /receptury admin"
        ));

        for (CustomRecipeDefinition def : recipes.values()) {
            String base = "recipes." + def.id();
            yaml.set(base + ".name", def.name());
            yaml.set(base + ".category", def.category().getKey().toUpperCase(Locale.ROOT));
            yaml.set(base + ".enabled", def.enabled());

            boolean canUseSimpleShape = def.matrixSlots().values().stream()
                    .noneMatch(CustomRecipeDefinition.RecipeSlotIngredient::hasExactItemConstraint);

            if (canUseSimpleShape) {
                List<String> shapeRows = new ArrayList<>(3);
                Map<Character, String> charToMat = new LinkedHashMap<>();
                char nextSym = 'A';
                Map<Material, Character> matToChar = new LinkedHashMap<>();
                for (int r = 0; r < 3; r++) {
                    StringBuilder row = new StringBuilder();
                    for (int c = 0; c < 3; c++) {
                        CustomRecipeDefinition.RecipeSlotIngredient ing = def.matrixSlots().get(r * 3 + c);
                        if (ing == null) {
                            row.append(' ');
                        } else {
                            char sym = matToChar.computeIfAbsent(ing.material(), m -> (char) (nextSym + charToMat.size()));
                            charToMat.putIfAbsent(sym, ing.material().name());
                            row.append(sym);
                        }
                    }
                    shapeRows.add(row.toString());
                }
                yaml.set(base + ".shape", shapeRows);
                for (Map.Entry<Character, String> e : charToMat.entrySet()) {
                    yaml.set(base + ".ingredients." + e.getKey(), e.getValue());
                }
            } else {
                for (Map.Entry<Integer, CustomRecipeDefinition.RecipeSlotIngredient> entry : def.matrixSlots().entrySet()) {
                    String slotPath = base + ".slots." + entry.getKey();
                    CustomRecipeDefinition.RecipeSlotIngredient ing = entry.getValue();
                    yaml.set(slotPath + ".material", ing.material().name());
                    if (ing.customItemId() != null) {
                        yaml.set(slotPath + ".custom-item", ing.customItemId());
                    }
                    if (ing.exactStack() != null) {
                        yaml.set(slotPath + ".item", ing.exactStack());
                    }
                }
            }

            CustomRecipeDefinition.RecipeResultSpec spec = def.resultSpec();
            if (spec.customItemId() != null) {
                yaml.set(base + ".result-custom-item", spec.customItemId());
            } else if (spec.specialArmorId() != null) {
                yaml.set(base + ".result-special-armor", spec.specialArmorId());
            } else if (spec.magicItemId() != null) {
                yaml.set(base + ".result-magic-item", spec.magicItemId());
                yaml.set(base + ".result-magic-level", spec.magicItemLevel());
            } else if (spec.exactResult() != null && spec.exactResult().hasItemMeta()) {
                yaml.set(base + ".result-item", spec.exactResult());
                yaml.set(base + ".result-material", spec.exactResult().getType().name());
            } else if (spec.material() != null) {
                yaml.set(base + ".result-material", spec.material().name());
            }
            yaml.set(base + ".result-amount", spec.amount());
        }

        try {
            if (recipesFile.getParentFile() != null) {
                recipesFile.getParentFile().mkdirs();
            }
            yaml.save(recipesFile);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Nie udało się zapisać pliku recipes.yml", e);
        }
    }

    public synchronized String nextAutoRecipeId(Material resultMaterial) {
        String base = resultMaterial != null
                ? "custom_" + resultMaterial.name().toLowerCase(Locale.ROOT)
                : "custom_recipe";
        int index = 1;
        while (recipes.containsKey(base + "_" + index)) {
            index++;
        }
        return base + "_" + index;
    }

    public synchronized Optional<CustomRecipeDefinition> getRecipe(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(recipes.get(id.trim().toLowerCase(Locale.ROOT)));
    }

    public synchronized Collection<CustomRecipeDefinition> getAllRecipes() {
        return Collections.unmodifiableList(new ArrayList<>(recipes.values()));
    }

    public synchronized List<CustomRecipeDefinition> getRecipesByCategory(
            CustomRecipeDefinition.RecipeCategory category,
            boolean enabledOnly
    ) {
        List<CustomRecipeDefinition> result = new ArrayList<>();
        for (CustomRecipeDefinition def : recipes.values()) {
            if (def.category() == category && (!enabledOnly || def.enabled())) {
                result.add(def);
            }
        }
        return result;
    }

    public static List<String> computeTrimmedShape(Map<Integer, CustomRecipeDefinition.RecipeSlotIngredient> matrixSlots) {
        if (matrixSlots == null || matrixSlots.isEmpty()) {
            return List.of();
        }
        int minRow = findMinRow(matrixSlots);
        int maxRow = findMaxRow(matrixSlots);
        int minCol = findMinCol(matrixSlots);
        int maxCol = findMaxCol(matrixSlots);

        List<String> rows = new ArrayList<>(maxRow - minRow + 1);
        for (int r = minRow; r <= maxRow; r++) {
            StringBuilder line = new StringBuilder();
            for (int c = minCol; c <= maxCol; c++) {
                int slot = r * 3 + c;
                if (matrixSlots.containsKey(slot)) {
                    int relIdx = (r - minRow) * 3 + (c - minCol);
                    line.append((char) ('A' + relIdx));
                } else {
                    line.append(' ');
                }
            }
            rows.add(line.toString());
        }
        return rows;
    }

    private static int findMinRow(Map<Integer, CustomRecipeDefinition.RecipeSlotIngredient> slots) {
        int min = 2;
        for (int slot : slots.keySet()) {
            min = Math.min(min, slot / 3);
        }
        return min;
    }

    private static int findMaxRow(Map<Integer, CustomRecipeDefinition.RecipeSlotIngredient> slots) {
        int max = 0;
        for (int slot : slots.keySet()) {
            max = Math.max(max, slot / 3);
        }
        return max;
    }

    private static int findMinCol(Map<Integer, CustomRecipeDefinition.RecipeSlotIngredient> slots) {
        int min = 2;
        for (int slot : slots.keySet()) {
            min = Math.min(min, slot % 3);
        }
        return min;
    }

    private static int findMaxCol(Map<Integer, CustomRecipeDefinition.RecipeSlotIngredient> slots) {
        int max = 0;
        for (int slot : slots.keySet()) {
            max = Math.max(max, slot % 3);
        }
        return max;
    }

    private static Material parseItemMaterial(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return null;
        }
        Material mat = Material.matchMaterial(rawName.trim().toUpperCase(Locale.ROOT));
        if (RecipeDefinition.isAirMaterial(mat) || mat.isLegacy()) {
            return null;
        }
        if (Bukkit.getServer() != null && !mat.isItem()) {
            return null;
        }
        return mat;
    }
}
