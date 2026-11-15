package pl.nauka.aradium;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.nauka.aradium.modules.armor.ArmorModule;
import pl.nauka.aradium.modules.armor.ArmorPieceType;
import pl.nauka.aradium.modules.armor.ArmorTierBonus;
import pl.nauka.aradium.modules.economy.EconomyManager;
import pl.nauka.aradium.modules.magicitems.MagicItemDefinition;
import pl.nauka.aradium.modules.magicitems.MagicItemModule;
import pl.nauka.aradium.modules.oreregions.CuboidRegion;
import pl.nauka.aradium.modules.oreregions.OreRegionModule;
import pl.nauka.aradium.modules.shop.ShopCategory;
import pl.nauka.aradium.modules.shop.ShopGUI;
import pl.nauka.aradium.modules.shop.ShopItem;
import pl.nauka.aradium.modules.shop.ShopManager;
import pl.nauka.aradium.modules.skyblock.island.BorderColor;
import pl.nauka.aradium.modules.skyblock.island.Island;
import pl.nauka.aradium.modules.skyblock.island.IslandManager;
import pl.nauka.aradium.modules.skyblock.island.IslandPermission;
import pl.nauka.aradium.modules.skyblock.island.IslandRole;
import pl.nauka.aradium.modules.skyblock.level.BlockValueManager;
import pl.nauka.aradium.modules.skyblock.level.IslandLevelCalculator;
import pl.nauka.aradium.modules.skyblock.level.IslandTopCache;
import pl.nauka.aradium.modules.skyblock.quests.QuestDefinition;
import pl.nauka.aradium.modules.skyblock.quests.QuestManager;
import pl.nauka.aradium.modules.skyblock.quests.QuestProgress;
import pl.nauka.aradium.modules.skyblock.quests.QuestStage;
import pl.nauka.aradium.modules.skyblock.quests.QuestType;
import pl.nauka.aradium.modules.skyblock.upgrades.IslandUpgradeManager;
import pl.nauka.aradium.modules.skyblock.upgrades.IslandUpgradeType;
import pl.nauka.aradium.modules.spawners.SpawnerDataManager;
import pl.nauka.aradium.modules.spawners.SpawnerGUI;
import pl.nauka.aradium.modules.spawners.SpawnerModule;
import pl.nauka.aradium.modules.spawners.SpawnerTierDefinition;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AradiumAuditTest {

    @Test
    void cuboidRegionNormalizesMinMaxAndChecksBoundaries() {
        CuboidRegion region = CuboidRegion.ofCoordinates("world", 10, 64, -5, -2, 70, 5);
        assertNotNull(region);

        assertEquals(-2, region.minX());
        assertEquals(64, region.minY());
        assertEquals(-5, region.minZ());
        assertEquals(10, region.maxX());
        assertEquals(70, region.maxY());
        assertEquals(5, region.maxZ());

        assertTrue(region.contains("world", -2, 64, -5));
        assertTrue(region.contains("world", 10, 70, 5));
        assertTrue(region.contains("world", 0, 67, 0));

        assertFalse(region.contains("world", 11, 67, 0));
        assertFalse(region.contains("world", 0, 63, 0));
        assertFalse(region.contains("world_nether", 0, 67, 0));
        assertFalse(region.contains(null, 0, 67, 0));
        assertNull(CuboidRegion.ofCoordinates(" ", 0, 0, 0, 1, 1, 1));
    }

    @Test
    void cooldownCalculationAndIdNormalizationWorkCorrectly() {
        long now = 1_000_000L;

        assertEquals(0L, MagicItemModule.calculateRemainingSeconds(now - 500L, now));
        assertEquals(0L, MagicItemModule.calculateRemainingSeconds(now, now));
        assertEquals(1L, MagicItemModule.calculateRemainingSeconds(now + 1L, now));
        assertEquals(1L, MagicItemModule.calculateRemainingSeconds(now + 999L, now));
        assertEquals(1L, MagicItemModule.calculateRemainingSeconds(now + 1000L, now));
        assertEquals(2L, MagicItemModule.calculateRemainingSeconds(now + 1001L, now));
        assertEquals(120L, MagicItemModule.calculateRemainingSeconds(now + 120_000L, now));

        assertEquals("strength", MagicItemModule.normalizeItemId("STRENGTH"));
        assertEquals("duch", MagicItemModule.normalizeItemId("Duch"));
        assertNull(MagicItemModule.normalizeItemId(null));
    }

    @Test
    void blockKeyAndPendingMaterialResolutionAreSafe() {
        OreRegionModule.BlockKey keyA = new OreRegionModule.BlockKey("world.custom", 10, 64, -20);
        OreRegionModule.BlockKey keyB = new OreRegionModule.BlockKey("world.custom", 10, 64, -20);
        OreRegionModule.BlockKey keyOtherWorld = new OreRegionModule.BlockKey("world_nether", 10, 64, -20);

        assertEquals(keyA, keyB);
        assertEquals(keyA.hashCode(), keyB.hashCode());
        assertNotEquals(keyA, keyOtherWorld);
        assertFalse(keyA.toStorageKey().contains("."));
        assertNotEquals(keyA.toStorageKey(), keyOtherWorld.toStorageKey());

        assertEquals(Material.DIAMOND_ORE, OreRegionModule.resolvePendingMaterial(Material.DIAMOND_ORE, Material.IRON_ORE));
        assertEquals(Material.IRON_ORE, OreRegionModule.resolvePendingMaterial(null, Material.IRON_ORE));
    }

    @Test
    void ghostFlySpeedConversionAndDefinitionFlagWorkCorrectly() {
        assertEquals(0.3f, MagicItemModule.toBukkitFlySpeed(3.0), 0.0001f);
        assertEquals(1.0f, MagicItemModule.toBukkitFlySpeed(10.0), 0.0001f);
        assertEquals(1.0f, MagicItemModule.toBukkitFlySpeed(15.0), 0.0001f);
        assertEquals(0.3f, MagicItemModule.toBukkitFlySpeed(0.3), 0.0001f);
        assertEquals(0.1f, MagicItemModule.toBukkitFlySpeed(0.0), 0.0001f);
        assertEquals(0.1f, MagicItemModule.toBukkitFlySpeed(-2.0), 0.0001f);
        assertEquals(0.1f, MagicItemModule.toBukkitFlySpeed(Double.NaN), 0.0001f);

        MagicItemDefinition ghost = new MagicItemDefinition(
                "duch", "Duch", Material.SOUL_SAND, "", 120, "GHOST", null, 600, 2, 3.0, 3, 2
        );
        MagicItemDefinition strength = new MagicItemDefinition(
                "strength", "Siła", Material.BLAZE_POWDER, "", 120, "", null, 600, 2, 3.0, 3, 2
        );
        assertTrue(ghost.isGhostAbility());
        assertFalse(strength.isGhostAbility());
    }

    @Test
    void armorFullSetMatchingRequiresFourMatchingPiecesAndTiers() {
        Optional<ArmorModule.EquippedSet> matched = ArmorModule.EquippedSet.matchFullSet(
                new String[]{"Wojownik", "wojownik", "WOJOWNIK", "wojownik"},
                new int[]{5, 5, 5, 5}
        );
        assertTrue(matched.isPresent());
        assertEquals("wojownik", matched.get().setId());
        assertEquals(5, matched.get().tier());

        assertTrue(ArmorModule.EquippedSet.matchFullSet(
                new String[]{"wojownik", "wojownik", null, "wojownik"},
                new int[]{3, 3, 3, 3}
        ).isEmpty());

        assertTrue(ArmorModule.EquippedSet.matchFullSet(
                new String[]{"gornik", "gornik", "wojownik", "gornik"},
                new int[]{2, 2, 2, 2}
        ).isEmpty());

        assertTrue(ArmorModule.EquippedSet.matchFullSet(
                new String[]{"wojownik", "wojownik", "wojownik", "wojownik"},
                new int[]{3, 3, 4, 3}
        ).isEmpty());

        assertTrue(ArmorModule.EquippedSet.matchFullSet(
                new String[]{"gornik", "gornik"},
                new int[]{1, 1}
        ).isEmpty());

        assertTrue(ArmorModule.EquippedSet.matchFullSet(
                new String[]{"gornik", "gornik", "gornik", "gornik"},
                new int[]{0, 0, 0, 0}
        ).isEmpty());
    }

    @Test
    void armorMaxHealthBonusAndYamlStructureAreValid() throws Exception {
        assertEquals(0.0, ArmorTierBonus.toMaxHealthBonus(0), 0.0001);
        assertEquals(8.0, ArmorTierBonus.toMaxHealthBonus(2), 0.0001);
        assertEquals(12.0, ArmorTierBonus.toMaxHealthBonus(3), 0.0001);
        assertEquals(20.0, ArmorTierBonus.toMaxHealthBonus(5), 0.0001);

        assertEquals(Material.IRON_HELMET, ArmorPieceType.HELMET.resolveMaterial("IRON"));
        assertEquals(Material.GOLDEN_CHESTPLATE, ArmorPieceType.CHESTPLATE.resolveMaterial("GOLDEN"));
        assertEquals(Material.DIAMOND_BOOTS, ArmorPieceType.BOOTS.resolveMaterial("DIAMOND"));

        try (InputStream stream = getClass().getClassLoader().getResourceAsStream("armor.yml")) {
            assertNotNull(stream);
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
            ConfigurationSection sets = yaml.getConfigurationSection("armor_sets");
            assertNotNull(sets);

            assertEquals(5, sets.getConfigurationSection("gornik.tiers").getKeys(false).size());
            assertEquals(5, sets.getInt("gornik.tiers.5.effects.HASTE"));

            assertEquals(3, sets.getConfigurationSection("handlarz.tiers").getKeys(false).size());
            assertEquals(3, sets.getInt("handlarz.tiers.3.effects.HERO_OF_THE_VILLAGE"));

            assertEquals(5, sets.getConfigurationSection("wojownik.tiers").getKeys(false).size());
            assertEquals(3, sets.getInt("wojownik.tiers.1.effects.RESISTANCE"));
            assertEquals(4, sets.getInt("wojownik.tiers.2.effects.RESISTANCE"));
            assertEquals(3, sets.getInt("wojownik.tiers.3.effects.RESISTANCE"));
            assertEquals(2, sets.getInt("wojownik.tiers.3.max_health_levels"));
            assertEquals(4, sets.getInt("wojownik.tiers.4.effects.RESISTANCE"));
            assertEquals(3, sets.getInt("wojownik.tiers.4.max_health_levels"));
            assertEquals(5, sets.getInt("wojownik.tiers.5.effects.RESISTANCE"));
            assertEquals(4, sets.getInt("wojownik.tiers.5.effects.STRENGTH"));
            assertEquals(5, sets.getInt("wojownik.tiers.5.max_health_levels"));
            assertTrue(sets.getStringList("wojownik.tiers.5.lore").stream()
                    .anyMatch(line -> line.contains("(%equipped%/4)")));
        }
    }

    @Test
    void armorLoreFormattingUpdatesEquippedCountAndClampsBounds() {
        List<String> template = List.of(
                "&8» &7Zestaw: &bWojownik",
                "&8» &7Wymaga założenia pełnego setu (%equipped%/4)",
                "&8» &7Starszy zapis: (4/4)"
        );

        assertEquals(List.of(
                "&8» &7Zestaw: &bWojownik",
                "&8» &7Wymaga założenia pełnego setu (0/4)",
                "&8» &7Starszy zapis: (0/4)"
        ), ArmorModule.formatLoreLines(template, 0));

        assertEquals(List.of(
                "&8» &7Zestaw: &bWojownik",
                "&8» &7Wymaga założenia pełnego setu (2/4)",
                "&8» &7Starszy zapis: (2/4)"
        ), ArmorModule.formatLoreLines(template, 2));

        assertEquals(List.of(
                "&8» &7Zestaw: &bWojownik",
                "&8» &7Wymaga założenia pełnego setu (3/4)",
                "&8» &7Starszy zapis: (3/4)"
        ), ArmorModule.formatLoreLines(template, 3));

        assertEquals(List.of(
                "&8» &7Zestaw: &bWojownik",
                "&8» &7Wymaga założenia pełnego setu (4/4)",
                "&8» &7Starszy zapis: (4/4)"
        ), ArmorModule.formatLoreLines(template, 4));
    }

    @Test
    void aviatorBootsGameModeAndSingleFallProtectionWorkCorrectly() throws Exception {
        assertTrue(ArmorModule.canKeepFlightByGameMode(GameMode.CREATIVE));
        assertTrue(ArmorModule.canKeepFlightByGameMode(GameMode.SPECTATOR));
        assertFalse(ArmorModule.canKeepFlightByGameMode(GameMode.SURVIVAL));
        assertFalse(ArmorModule.canKeepFlightByGameMode(GameMode.ADVENTURE));
        assertFalse(ArmorModule.canKeepFlightByGameMode(null));

        UUID playerId = UUID.randomUUID();
        UUID otherPlayerId = UUID.randomUUID();
        Set<UUID> protectedPlayers = new HashSet<>();
        protectedPlayers.add(playerId);

        assertFalse(ArmorModule.consumeFallProtection(protectedPlayers, otherPlayerId));
        assertTrue(ArmorModule.consumeFallProtection(protectedPlayers, playerId));
        assertFalse(ArmorModule.consumeFallProtection(protectedPlayers, playerId));

        try (InputStream stream = getClass().getClassLoader().getResourceAsStream("armor.yml")) {
            assertNotNull(stream);
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
            ConfigurationSection bootsSection = yaml.getConfigurationSection("special_armor.buty_lotnika");
            assertNotNull(bootsSection);
            assertEquals("LEATHER_BOOTS", bootsSection.getString("material"));
            assertEquals("FEET", bootsSection.getString("slot"));
            assertTrue(bootsSection.getBoolean("unbreakable"));
            assertEquals("FLIGHT", bootsSection.getString("ability"));
        }
    }

    @Test
    void economyManagerOperationsAndYamlPersistenceWorkCorrectly(@TempDir Path tempDir) {
        File economyFile = tempDir.resolve("economy.yml").toFile();
        Logger testLogger = Logger.getLogger("EconomyTest");
        EconomyManager eco = new EconomyManager(economyFile, testLogger, 100.0);

        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();

        assertEquals(100.0, eco.getMoney(alice), 0.001);
        assertTrue(eco.hasMoney(alice, 100.0));
        assertFalse(eco.hasMoney(alice, 100.01));
        assertFalse(eco.hasMoney(alice, -5.0));

        assertTrue(eco.addMoney(alice, 50.129));
        assertEquals(150.13, eco.getMoney(alice), 0.001);

        assertFalse(eco.addMoney(alice, -10.0));
        assertFalse(eco.takeMoney(alice, 200.0));
        assertEquals(150.13, eco.getMoney(alice), 0.001);

        assertTrue(eco.takeMoney(alice, 50.13));
        assertEquals(100.0, eco.getMoney(alice), 0.001);

        assertFalse(eco.transferMoney(alice, alice, 10.0));
        assertTrue(eco.transferMoney(alice, bob, 40.0));
        assertEquals(60.0, eco.getMoney(alice), 0.001);
        assertEquals(140.0, eco.getMoney(bob), 0.001);

        assertTrue(eco.setMoney(bob, 999.95));
        assertEquals(999.95, eco.getMoney(bob), 0.001);
        assertTrue(eco.getDirtyCount() > 0);

        eco.flushDirtyAccounts();
        assertEquals(0, eco.getDirtyCount());

        EconomyManager reloaded = new EconomyManager(economyFile, testLogger, 0.0);
        reloaded.loadAll();
        assertEquals(60.0, reloaded.getMoney(alice), 0.001);
        assertEquals(999.95, reloaded.getMoney(bob), 0.001);
    }

    @Test
    void spawnerQuestsAndShopYamlConfigsAreValid() throws Exception {
        assertTrue(SpawnerDataManager.shouldApplyMaxDelayFirst(200, 250));
        assertFalse(SpawnerDataManager.shouldApplyMaxDelayFirst(600, 140));

        try (InputStream spawnersStream = getClass().getClassLoader().getResourceAsStream("spawners.yml");
             InputStream questsStream = getClass().getClassLoader().getResourceAsStream("quests.yml");
             InputStream blocksShopStream = getClass().getClassLoader().getResourceAsStream("shops/blocks.yml");
             InputStream naturalShopStream = getClass().getClassLoader().getResourceAsStream("shops/natural.yml")) {

            assertNotNull(spawnersStream);
            YamlConfiguration spawners = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(spawnersStream, StandardCharsets.UTF_8)
            );
            ConfigurationSection tiers = spawners.getConfigurationSection("tiers");
            assertNotNull(tiers);
            assertEquals(3, tiers.getKeys(false).size());
            assertEquals(8, tiers.getInt("3.spawn-count"));
            assertEquals(20000.0, tiers.getDouble("3.upgrade-cost"), 0.001);

            assertNotNull(questsStream);
            YamlConfiguration quests = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(questsStream, StandardCharsets.UTF_8)
            );
            ConfigurationSection stage1Quests = quests.getConfigurationSection("stages.stage_1.quests");
            assertNotNull(stage1Quests);
            assertEquals(7, stage1Quests.getKeys(false).size());
            assertEquals(128, stage1Quests.getInt("mining_1.objectives.COBBLESTONE"));
            assertEquals(2000, stage1Quests.getInt("economy_1.objectives.BALANCE"));
            assertEquals(500, stage1Quests.getInt("island_level_1.objectives.ISLAND_LEVEL"));
            assertEquals(16, quests.getInt("stages.stage_5.quests.mining_5.objectives.NETHERITE_INGOT"));

            assertNotNull(blocksShopStream);
            YamlConfiguration blocksShop = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(blocksShopStream, StandardCharsets.UTF_8)
            );
            ConfigurationSection blockItems = blocksShop.getConfigurationSection("pages.page1.items");
            assertNotNull(blockItems);
            assertEquals(4.0, blockItems.getDouble("cobblestone.buy-price"), 0.001);

            assertNotNull(naturalShopStream);
            YamlConfiguration naturalShop = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(naturalShopStream, StandardCharsets.UTF_8)
            );
            ConfigurationSection naturalItems = naturalShop.getConfigurationSection("pages.page1.items");
            assertNotNull(naturalItems);
            assertEquals(-1.0, naturalItems.getDouble("grass_block.sell-price"), 0.001);
        }
    }

    @Test
    void shopManagerAndAutoLoreGenerationWorkCorrectly() throws Exception {
        assertEquals("4.00", EconomyManager.formatAmount(4.0));
        assertEquals("4.00$", EconomyManager.formatMoney(4.0));
        assertEquals(9, ShopCategory.normalizeInventorySize(5));
        assertEquals(27, ShopCategory.normalizeInventorySize(20));
        assertEquals(54, ShopCategory.normalizeInventorySize(90));

        try (InputStream mainStream = getClass().getClassLoader().getResourceAsStream("shops/main.yml");
             InputStream blocksStream = getClass().getClassLoader().getResourceAsStream("shops/blocks.yml");
             InputStream naturalStream = getClass().getClassLoader().getResourceAsStream("shops/natural.yml")) {

            assertNotNull(mainStream);
            assertNotNull(blocksStream);
            assertNotNull(naturalStream);

            Logger testLogger = Logger.getLogger("ShopTest");
            YamlConfiguration mainYaml = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(mainStream, StandardCharsets.UTF_8)
            );
            YamlConfiguration blocksYaml = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(blocksStream, StandardCharsets.UTF_8)
            );
            YamlConfiguration naturalYaml = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(naturalStream, StandardCharsets.UTF_8)
            );

            ShopCategory mainCategory = ShopManager.parseCategory(mainYaml, "main", testLogger);
            ShopCategory blocksCategory = ShopManager.parseCategory(blocksYaml, "blocks", testLogger);
            ShopCategory naturalCategory = ShopManager.parseCategory(naturalYaml, "natural", testLogger);

            assertNotNull(mainCategory);
            assertEquals("main", mainCategory.id());
            assertEquals(54, mainCategory.size());
            ShopItem buildingNav = mainCategory.itemsBySlot().get(11);
            assertNotNull(buildingNav);
            assertTrue(buildingNav.isNavigationItem());
            assertEquals("building", buildingNav.targetShop());
            assertFalse(ShopGUI.buildRawLoreLines(mainCategory, buildingNav).stream()
                    .anyMatch(line -> line.contains("LPM")));

            assertNotNull(blocksCategory);
            assertEquals("blocks", blocksCategory.id());
            assertEquals("building", blocksCategory.parentShopId());
            assertEquals(53, blocksCategory.backButtonSlot());
            assertEquals(3, blocksCategory.pageCount());

            ShopItem cobble = blocksCategory.itemsBySlot().get(10);
            assertNotNull(cobble);
            assertTrue(cobble.canBuy());
            assertTrue(cobble.canSell());
            List<String> cobbleLore = ShopGUI.buildRawLoreLines(blocksCategory, cobble);
            assertTrue(cobbleLore.stream().anyMatch(line -> line.contains("4.00$") && !line.contains("$$")));
            assertTrue(cobbleLore.stream().anyMatch(line -> line.contains("256.00$") && !line.contains("$$")));
            assertTrue(cobbleLore.stream().anyMatch(line -> line.contains("1.50$") && !line.contains("$$")));
            assertTrue(cobbleLore.stream().anyMatch(line -> line.contains("Sprzedaj wszystko")));

            assertNotNull(naturalCategory);
            ShopItem grass = naturalCategory.pageItems(0).values().stream()
                    .filter(item -> item.material() == Material.GRASS_BLOCK)
                    .findFirst()
                    .orElse(null);
            assertNotNull(grass);
            assertTrue(grass.canBuy());
            assertFalse(grass.canSell());
            List<String> grassLore = ShopGUI.buildRawLoreLines(naturalCategory, grass);
            assertTrue(grassLore.stream().anyMatch(line -> line.contains("Sprzedaż tego przedmiotu jest wyłączona")));
            assertFalse(grassLore.stream().anyMatch(line -> line.contains("Sprzedaj wszystko")));
        }
    }

    @Test
    void allShopCategoriesLoadCleanlyWithValidMaterialsAndPagination(@TempDir Path tempDir) throws Exception {
        List<String> shopNames = List.of(
                "main", "building", "blocks", "wood", "natural", "decorative_blocks",
                "ores", "farming", "food", "combat", "redstone", "magic",
                "mobdrops", "decoration", "music", "storage", "misc"
        );

        Path shopsDir = tempDir.resolve("shops");
        Files.createDirectories(shopsDir);

        for (String shopName : shopNames) {
            try (InputStream stream = getClass().getClassLoader().getResourceAsStream("shops/" + shopName + ".yml")) {
                assertNotNull(stream, "Brak zasobu shops/" + shopName + ".yml");
                Files.copy(stream, shopsDir.resolve(shopName + ".yml"));
            }
        }

        List<String> warningMessages = new ArrayList<>();
        Logger strictLogger = Logger.getLogger("ShopAuditStrictLogger");
        strictLogger.setUseParentHandlers(false);
        Handler captureHandler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (record.getLevel().intValue() >= Level.WARNING.intValue()) {
                    warningMessages.add(record.getMessage());
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        strictLogger.addHandler(captureHandler);

        try {
            ShopManager manager = new ShopManager(strictLogger);
            manager.loadFromDirectory(shopsDir.toFile());

            assertTrue(warningMessages.isEmpty(), "Błędy parsowania sklepów: " + warningMessages);
            assertEquals(17, manager.getCategoryIds().size());

            for (ShopCategory category : manager.getCategories()) {
                assertTrue(category.pageCount() >= 1);
                if (category.parentShopId() != null) {
                    assertTrue(
                            manager.getCategory(category.parentShopId()).isPresent(),
                            "Nieznany parent-shop '" + category.parentShopId() + "' w " + category.id()
                    );
                }
                for (Map<Integer, ShopItem> page : category.pages()) {
                    assertFalse(page.isEmpty(), "Pusta strona w kategorii " + category.id());
                    for (ShopItem item : page.values()) {
                        assertFalse(item.material().isLegacy(), "Legacy material w " + category.id());
                        if (item.isNavigationItem()) {
                            assertTrue(
                                    manager.getCategory(item.targetShop()).isPresent(),
                                    "Nieznany target-shop '" + item.targetShop() + "' w " + category.id()
                            );
                        } else if (item.canBuy() && item.canSell()) {
                            assertTrue(
                                    item.sellPrice() <= item.buyPrice(),
                                    "Arbitraż ceny w " + category.id() + "/" + item.id()
                            );
                        }
                    }
                }
            }

            assertEquals(1.5, manager.findBestSellableItem(Material.COBBLESTONE).orElseThrow().sellPrice(), 0.001);
            assertEquals(75.0, manager.findBestSellableItem(Material.DIAMOND).orElseThrow().sellPrice(), 0.001);
            assertTrue(manager.findBestSellableItem(Material.GRASS_BLOCK).isEmpty());
            assertTrue(manager.findBestSellableItem(Material.SPAWNER).isEmpty());
        } finally {
            strictLogger.removeHandler(captureHandler);
        }

        assertEquals(1, ShopGUI.calculateAdjustedAmount(1, -16, 1, 64));
        assertEquals(48, ShopGUI.calculateAdjustedAmount(16, 32, 1, 64));
        assertEquals(64, ShopGUI.calculateAdjustedAmount(40, 32, 1, 64));
        assertEquals(28, ShopManager.computeAutoSlots(54, 53).size());

        YamlConfiguration overflowYaml = new YamlConfiguration();
        overflowYaml.set("shop.id", "overflow_test");
        overflowYaml.set("shop.size", 54);
        for (int i = 0; i < 30; i++) {
            overflowYaml.set("items.item_" + i + ".material", "STONE");
            overflowYaml.set("items.item_" + i + ".buy-price", 5.0);
        }
        ShopCategory overflowCategory = ShopManager.parseCategory(overflowYaml, "overflow_test", Logger.getLogger("Overflow"));
        assertNotNull(overflowCategory);
        assertEquals(2, overflowCategory.pageCount());
        assertEquals(28, overflowCategory.pageItems(0).size());
        assertEquals(2, overflowCategory.pageItems(1).size());
    }

    @Test
    void spawnerModuleParsingAndGuiLoreWorkCorrectly() throws Exception {
        assertEquals(EntityType.ZOMBIE, SpawnerDataManager.parseEntityType(" zombie "));
        assertNull(SpawnerDataManager.parseEntityType("not_a_real_entity"));
        assertEquals("Iron Golem", SpawnerDataManager.formatEntityName(EntityType.IRON_GOLEM));

        try (InputStream stream = getClass().getClassLoader().getResourceAsStream("spawners.yml")) {
            assertNotNull(stream);
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8)
            );

            Map<Integer, SpawnerTierDefinition> tiers = SpawnerModule.parseTiers(yaml, Logger.getLogger("SpawnerTest"));
            assertEquals(3, tiers.size());

            SpawnerTierDefinition tier1 = tiers.get(1);
            SpawnerTierDefinition tier2 = tiers.get(2);
            SpawnerTierDefinition tier3 = tiers.get(3);
            assertNotNull(tier1);
            assertNotNull(tier2);
            assertNotNull(tier3);

            assertEquals(4, tier2.requiredItems().get(Material.IRON_BLOCK));
            assertEquals(8, tier2.requiredItems().get(Material.COAL_BLOCK));

            String summary = SpawnerGUI.formatRequiredItemsSummary(tier2.requiredItems());
            assertTrue(summary.contains("4x Iron Block"));
            assertTrue(summary.contains("8x Coal Block"));

            List<String> upgradeLore = SpawnerGUI.buildUpgradeLore(tier1, tier2);
            assertTrue(upgradeLore.stream().anyMatch(line -> line.contains("Tier 2")));
            assertTrue(upgradeLore.stream().anyMatch(line -> line.contains("5,000.00$")));
            assertTrue(upgradeLore.stream().anyMatch(line -> line.contains("Iron Block")));

            List<String> maxLore = SpawnerGUI.buildUpgradeLore(tier3, null);
            assertTrue(maxLore.stream().anyMatch(line -> line.contains("maksymalny poziom")));
        }
    }

    @Test
    void skyblockIslandsRolesPermissionsAndGridWorkCorrectly() {
        assertEquals(BorderColor.GREEN, BorderColor.BLUE.next());
        assertEquals(BorderColor.RED, BorderColor.GREEN.next());
        assertEquals(BorderColor.BLUE, BorderColor.RED.next());
        assertEquals(BorderColor.GREEN, BorderColor.parse("green"));

        org.junit.jupiter.api.Assertions.assertArrayEquals(new int[]{0, 0}, IslandManager.calculateGridCoordinates(0, 400));
        org.junit.jupiter.api.Assertions.assertArrayEquals(new int[]{400, 0}, IslandManager.calculateGridCoordinates(1, 400));
        org.junit.jupiter.api.Assertions.assertArrayEquals(new int[]{400, 400}, IslandManager.calculateGridCoordinates(2, 400));
        org.junit.jupiter.api.Assertions.assertArrayEquals(new int[]{0, 400}, IslandManager.calculateGridCoordinates(3, 400));
        org.junit.jupiter.api.Assertions.assertArrayEquals(new int[]{-400, 400}, IslandManager.calculateGridCoordinates(4, 400));

        java.util.UUID owner = java.util.UUID.randomUUID();
        java.util.UUID member = java.util.UUID.randomUUID();
        java.util.UUID outsider = java.util.UUID.randomUUID();

        Island island = new Island(
                java.util.UUID.randomUUID(),
                owner,
                "skyblock_world",
                400, 0,
                400.5, 100.0, 0.5, 0.0f, 0.0f,
                false,
                BorderColor.BLUE,
                0.0, 0.0,
                System.currentTimeMillis(),
                50, 4, 5
        );

        assertEquals(IslandRole.OWNER, island.getPlayerRole(owner));
        assertTrue(island.hasPermission(owner, IslandPermission.SPAWNERS));
        assertEquals(IslandRole.VISITOR, island.getPlayerRole(outsider));
        assertFalse(island.canVisit(outsider));

        assertTrue(island.addTrusted(outsider));
        assertTrue(island.isTrusted(outsider));
        assertEquals(IslandRole.TRUSTED, island.getPlayerRole(outsider));
        assertTrue(island.canVisit(outsider));
        assertTrue(island.hasPermission(outsider, IslandPermission.BREAK));
        assertFalse(island.hasPermission(outsider, IslandPermission.CONTAINERS));
        assertTrue(island.removeTrusted(outsider));
        assertFalse(island.canVisit(outsider));

        island.setMember(member, IslandRole.MEMBER);
        assertEquals(IslandRole.MEMBER, island.getPlayerRole(member));
        assertTrue(island.hasPermission(member, IslandPermission.CONTAINERS));
        assertFalse(island.hasPermission(member, IslandPermission.SPAWNERS));
        assertTrue(island.toggleRolePermission(IslandRole.MEMBER, IslandPermission.SPAWNERS));
        assertTrue(island.hasPermission(member, IslandPermission.SPAWNERS));

        assertFalse(island.transferOwnership(outsider));
        assertTrue(island.transferOwnership(member));
        assertEquals(IslandRole.OWNER, island.getPlayerRole(member));
        assertEquals(IslandRole.CO_OWNER, island.getPlayerRole(owner));

        assertTrue(island.containsLocation("skyblock_world", 450, -50));
        assertFalse(island.containsLocation("skyblock_world", 451, 0));
        assertFalse(island.containsLocation("world", 400, 0));
    }

    @Test
    void skyblockLevelsTopAndUpgradesWorkCorrectly() throws Exception {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream("blocks-value.yml");
             InputStream cfgStream = getClass().getClassLoader().getResourceAsStream("config.yml")) {
            assertNotNull(stream);
            assertNotNull(cfgStream);

            YamlConfiguration blocksYaml = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8)
            );
            Map<Material, Double> values = BlockValueManager.parseBlockValues(blocksYaml, Logger.getLogger("BlocksTest"));
            assertEquals(400.0, values.get(Material.BEACON));
            assertEquals(150.0, values.get(Material.SPAWNER));
            assertEquals(35.0, values.get(Material.DIAMOND_BLOCK));
            assertEquals(250.0, values.get(Material.NETHERITE_BLOCK));

            double computed = IslandLevelCalculator.computeLevelFromCounts(
                    Map.of(Material.BEACON, 2, Material.DIAMOND_BLOCK, 4, Material.SPAWNER, 1),
                    values,
                    1.0
            );
            assertEquals(1090.0, computed, 0.001);

            Island i1 = new Island(UUID.randomUUID(), UUID.randomUUID(), "skyblock_world", 0, 0,
                    0.5, 100, 0.5, 0, 0, true, BorderColor.BLUE, 150.0, 0, 100L, 50, 4, 8);
            Island i2 = new Island(UUID.randomUUID(), UUID.randomUUID(), "skyblock_world", 400, 0,
                    400.5, 100, 0.5, 0, 0, true, BorderColor.GREEN, 1090.0, 0, 200L, 50, 4, 8);
            Island i3 = new Island(UUID.randomUUID(), UUID.randomUUID(), "skyblock_world", 400, 400,
                    400.5, 100, 400.5, 0, 0, true, BorderColor.RED, 500.0, 0, 300L, 50, 4, 8);

            IslandTopCache topCache = new IslandTopCache();
            topCache.refresh(List.of(i1, i2, i3));
            List<Island> top2 = topCache.getTopIslands(2);
            assertEquals(2, top2.size());
            assertEquals(i2.getIslandId(), top2.get(0).getIslandId());
            assertEquals(i3.getIslandId(), top2.get(1).getIslandId());
            assertEquals(1, topCache.getIslandRank(i2.getIslandId()));
            assertEquals(2, topCache.getIslandRank(i3.getIslandId()));
            assertEquals(3, topCache.getIslandRank(i1.getIslandId()));

            assertEquals(IslandUpgradeType.WORLD_BORDER, IslandUpgradeType.parse("world_border"));
            assertEquals(IslandUpgradeType.SPAWNER_LIMIT, IslandUpgradeType.parse("SPAWNER_LIMIT"));

            YamlConfiguration cfgYaml = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(cfgStream, StandardCharsets.UTF_8)
            );
            IslandUpgradeManager upgradeManager = new IslandUpgradeManager(Logger.getLogger("UpgradesTest"));
            upgradeManager.reload(cfgYaml);
            assertEquals(5, upgradeManager.getMaxTier(IslandUpgradeType.WORLD_BORDER));
            assertEquals(32, upgradeManager.getTier(IslandUpgradeType.SPAWNER_LIMIT, 3).value());

            i1.setUpgradeTier(IslandUpgradeType.WORLD_BORDER, 4);
            i1.setUpgradeTier(IslandUpgradeType.SPAWNER_LIMIT, 3);
            i1.setUpgradeTier(IslandUpgradeType.CROP_GROWTH, 5);
            i1.setUpgradeTier(IslandUpgradeType.MEMBER_LIMIT, 2);
            upgradeManager.applyUpgradesToIsland(i1);

            assertEquals(150, i1.getBorderRadius());
            assertEquals(32, i1.getSpawnerLimit());
            assertEquals(100, i1.getCropGrowthBonusPercent());
            assertEquals(6, i1.getMaxMembers());
        }
    }

    @Test
    void skyblockQuestsAllFiveStagesProgressAndGuiWorkCorrectly() throws Exception {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream("quests.yml")) {
            assertNotNull(stream);
            YamlConfiguration questsYaml = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8)
            );

            QuestManager manager = new QuestManager(Logger.getLogger("QuestsTest"));
            manager.reload(questsYaml);

            Map<Integer, QuestStage> stages = manager.getStagesSnapshot();
            assertEquals(5, stages.size());
            int totalQuests = 0;
            for (int stageNum = 1; stageNum <= 5; stageNum++) {
                QuestStage stage = stages.get(stageNum);
                assertNotNull(stage, "Brak etapu " + stageNum);
                assertEquals(7, stage.getQuests().size(), "Etap " + stageNum + " powinien mieć 7 zadań");
                totalQuests += stage.getQuests().size();
            }
            assertEquals(35, totalQuests);

            // Weryfikacja JSON roundtrip oraz paska postępu
            QuestProgress rawProgress = new QuestProgress("mining_1", 1, Map.of("COBBLESTONE", 64, "COAL_ORE", 16), false, 0L);
            Map<String, Integer> decoded = QuestProgress.fromJson(rawProgress.toJson());
            assertEquals(64, decoded.get("COBBLESTONE"));
            assertEquals(16, decoded.get("COAL_ORE"));

            assertEquals("&7■■■■■■■■■■ &e0%", manager.buildProgressBar(0, 100));
            assertEquals("&a■■■■■&7■■■■■ &e50%", manager.buildProgressBar(50, 100));
            assertEquals("&a■■■■■■■■■■ &e100%", manager.buildProgressBar(150, 100));

            Island island = new Island(
                    UUID.randomUUID(), UUID.randomUUID(), "skyblock_world", 0, 0,
                    0.5, 100, 0.5, 0, 0, false, BorderColor.BLUE,
                    0.0, 0.0, System.currentTimeMillis(), 50, 4, 8
            );

            assertTrue(manager.isStageUnlocked(island, 1));
            assertFalse(manager.isStageUnlocked(island, 2));
            assertEquals(1, manager.getHighestUnlockedStage(island));

            // Zablokowany etap 2 nie powinien zliczać postępu przed ukończeniem etapu 1
            manager.recordProgress(null, island, QuestType.BLOCK_BREAK, "COBBLESTONE", 100);
            assertNull(island.getQuestProgress("mining_2"));
            assertEquals(100, island.getQuestProgress("mining_1").getProgress("COBBLESTONE"));

            // Dokończenie wszystkich 7 misji Etapu 1
            manager.recordProgress(null, island, QuestType.BLOCK_BREAK, "COBBLESTONE", 50);
            manager.recordProgress(null, island, QuestType.BLOCK_BREAK, "COAL_ORE", 32);
            manager.recordProgress(null, island, QuestType.BLOCK_BREAK, "IRON_ORE", 16);
            assertTrue(island.getQuestProgress("mining_1").isCompleted());

            manager.recordProgress(null, island, QuestType.COLLECT, "WHEAT", 64);
            manager.recordProgress(null, island, QuestType.COLLECT, "POTATO", 32);
            manager.recordProgress(null, island, QuestType.COLLECT, "EGG", 16);
            manager.recordProgress(null, island, QuestType.COLLECT, "PORKCHOP", 16);
            assertTrue(island.getQuestProgress("farming_1").isCompleted());

            manager.recordProgress(null, island, QuestType.BLOCK_BREAK, "ANY_LOG", 64);
            assertTrue(island.getQuestProgress("woodcutting_1").isCompleted());

            manager.updateAbsoluteProgress(null, island, QuestType.BALANCE_REACH, "BALANCE", 2000);
            assertTrue(island.getQuestProgress("economy_1").isCompleted());

            manager.updateAbsoluteProgress(null, island, QuestType.ISLAND_LEVEL, "ISLAND_LEVEL", 500);
            assertTrue(island.getQuestProgress("island_level_1").isCompleted());

            manager.recordProgress(null, island, QuestType.ENTITY_KILL, "ZOMBIE", 16);
            manager.recordProgress(null, island, QuestType.ENTITY_KILL, "SKELETON", 8);
            manager.recordProgress(null, island, QuestType.ENTITY_KILL, "SPIDER", 4);
            assertTrue(island.getQuestProgress("slayer_1").isCompleted());

            manager.recordProgress(null, island, QuestType.FISH_CATCH, "ANY_FISH", 16);
            assertTrue(island.getQuestProgress("fishing_1").isCompleted());

            // Po ukończeniu Etapu 1 odblokowuje się Etap 2
            assertTrue(manager.isStageCompleted(island, 1));
            assertTrue(manager.isStageUnlocked(island, 2));
            assertFalse(manager.isStageUnlocked(island, 3));
            assertEquals(2, manager.getHighestUnlockedStage(island));

            // Odblokowanie etapów 2–4 w modelu postępu odblokowuje Etap 5 i złożone misje BLOCK_AND_CRAFT
            for (int s = 2; s <= 4; s++) {
                for (QuestDefinition q : manager.getStage(s).getQuests()) {
                    island.getOrCreateQuestProgress(q.id(), s).markCompleted(System.currentTimeMillis());
                }
            }
            assertTrue(manager.isStageUnlocked(island, 5));
            assertEquals(5, manager.getHighestUnlockedStage(island));

            manager.recordProgress(null, island, QuestType.BLOCK_BREAK, "COBBLESTONE", 2048);
            manager.recordProgress(null, island, QuestType.CRAFT, "NETHERITE_INGOT", 16);
            assertTrue(island.getQuestProgress("mining_5").isCompleted());
        }
    }
}
