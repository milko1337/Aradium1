package pl.nauka.aradium.modules.skyblock.upgrades;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import pl.nauka.aradium.AradiumPlugin;
import pl.nauka.aradium.modules.economy.EconomyManager;
import pl.nauka.aradium.modules.economy.EconomyModule;
import pl.nauka.aradium.modules.skyblock.island.Island;
import pl.nauka.aradium.modules.skyblock.island.IslandManager;
import pl.nauka.aradium.modules.skyblock.island.IslandRole;
import pl.nauka.aradium.utils.ChatUtils;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;
import java.util.logging.Logger;

/**
 * Menedżer ulepszeń wyspy (rozmiar WorldBorder, limit spawnerów, bonus wzrostu roślin, limit członków, wydajność brukarki).
 */
public class IslandUpgradeManager {

    public record UpgradeTier(int tier, int value, double cost) {
    }

    private final AradiumPlugin plugin;
    private final IslandManager islandManager;
    private final Logger logger;
    private final EnumMap<IslandUpgradeType, TreeMap<Integer, UpgradeTier>> tiersByType = new EnumMap<>(IslandUpgradeType.class);

    public IslandUpgradeManager(AradiumPlugin plugin, IslandManager islandManager) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.islandManager = Objects.requireNonNull(islandManager, "islandManager");
        this.logger = plugin.getLogger();
        loadDefaults();
    }

    public IslandUpgradeManager(Logger logger) {
        this.plugin = null;
        this.islandManager = null;
        this.logger = Objects.requireNonNull(logger, "logger");
        loadDefaults();
    }

    public synchronized void reload() {
        if (plugin != null) {
            reload(plugin.getConfig());
        } else {
            loadDefaults();
        }
    }

    public synchronized void reload(FileConfiguration config) {
        loadDefaults();
        if (config == null) {
            return;
        }
        ConfigurationSection upgradesRoot = config.getConfigurationSection("skyblock.upgrades");
        if (upgradesRoot == null) {
            return;
        }

        for (IslandUpgradeType type : IslandUpgradeType.values()) {
            ConfigurationSection typeSec = upgradesRoot.getConfigurationSection(type.getKey());
            if (typeSec == null) {
                continue;
            }
            TreeMap<Integer, UpgradeTier> parsed = new TreeMap<>();
            int minValue = type == IslandUpgradeType.CROP_GROWTH ? 0 : 1;
            for (String tierKey : typeSec.getKeys(false)) {
                ConfigurationSection tierSec = typeSec.getConfigurationSection(tierKey);
                if (tierSec == null) {
                    continue;
                }
                try {
                    int tierNumber = Integer.parseInt(tierKey.trim());
                    if (tierNumber < 1) {
                        continue;
                    }
                    int value = Math.max(minValue, tierSec.getInt("value", minValue));
                    double cost = Math.max(0.0, tierSec.getDouble("cost", 0.0));
                    parsed.put(tierNumber, new UpgradeTier(tierNumber, value, cost));
                } catch (NumberFormatException ignored) {
                    logger.warning("Niepoprawny numer tieru ulepszenia '" + type.getKey() + "': " + tierKey);
                }
            }
            if (!parsed.isEmpty()) {
                tiersByType.put(type, parsed);
            }
        }
    }

    private void loadDefaults() {
        tiersByType.clear();
        registerDefault(IslandUpgradeType.WORLD_BORDER, new int[][]{
                {1, 50, 0}, {2, 75, 5000}, {3, 100, 15000}, {4, 150, 35000}, {5, 190, 75000}
        });
        registerDefault(IslandUpgradeType.SPAWNER_LIMIT, new int[][]{
                {1, 8, 0}, {2, 16, 7500}, {3, 32, 20000}, {4, 64, 50000}, {5, 128, 100000}
        });
        registerDefault(IslandUpgradeType.CROP_GROWTH, new int[][]{
                {1, 0, 0}, {2, 25, 4000}, {3, 50, 12000}, {4, 75, 25000}, {5, 100, 60000}
        });
        registerDefault(IslandUpgradeType.MEMBER_LIMIT, new int[][]{
                {1, 4, 0}, {2, 6, 3000}, {3, 8, 8000}, {4, 12, 20000}, {5, 16, 45000}
        });
        registerDefault(IslandUpgradeType.COBBLE_GENERATOR_YIELD, new int[][]{
                {1, 1, 0}, {2, 2, 1500}, {3, 3, 5000}, {4, 4, 15000}, {5, 5, 35000}
        });
    }

    private void registerDefault(IslandUpgradeType type, int[][] rows) {
        TreeMap<Integer, UpgradeTier> map = new TreeMap<>();
        for (int[] row : rows) {
            map.put(row[0], new UpgradeTier(row[0], row[1], row[2]));
        }
        tiersByType.put(type, map);
    }

    public synchronized UpgradeTier getTier(IslandUpgradeType type, int tier) {
        if (type == null) {
            return null;
        }
        NavigableMap<Integer, UpgradeTier> map = tiersByType.get(type);
        if (map == null || map.isEmpty()) {
            return null;
        }
        UpgradeTier exact = map.get(tier);
        if (exact != null) {
            return exact;
        }
        Map.Entry<Integer, UpgradeTier> floor = map.floorEntry(tier);
        return floor != null ? floor.getValue() : map.firstEntry().getValue();
    }

    public synchronized UpgradeTier getExactTier(IslandUpgradeType type, int tier) {
        if (type == null) {
            return null;
        }
        TreeMap<Integer, UpgradeTier> map = tiersByType.get(type);
        return map != null ? map.get(tier) : null;
    }

    public synchronized int getMaxTier(IslandUpgradeType type) {
        TreeMap<Integer, UpgradeTier> map = tiersByType.get(type);
        return (map != null && !map.isEmpty()) ? map.lastKey() : 1;
    }

    public synchronized Map<Integer, UpgradeTier> getTiersSnapshot(IslandUpgradeType type) {
        TreeMap<Integer, UpgradeTier> map = tiersByType.get(type);
        return map != null ? Collections.unmodifiableMap(new TreeMap<>(map)) : Collections.emptyMap();
    }

    public synchronized void applyUpgradesToIsland(Island island) {
        if (island == null) {
            return;
        }
        UpgradeTier border = getTier(IslandUpgradeType.WORLD_BORDER, island.getUpgradeTier(IslandUpgradeType.WORLD_BORDER));
        if (border != null) {
            int maxAllowedRadius = islandManager != null
                    ? Math.max(16, (islandManager.getIslandSpacing() / 2) - 8)
                    : border.value();
            island.setBorderRadius(Math.min(border.value(), maxAllowedRadius));
        }

        UpgradeTier spawners = getTier(IslandUpgradeType.SPAWNER_LIMIT, island.getUpgradeTier(IslandUpgradeType.SPAWNER_LIMIT));
        if (spawners != null) {
            island.setSpawnerLimit(spawners.value());
        }

        UpgradeTier crops = getTier(IslandUpgradeType.CROP_GROWTH, island.getUpgradeTier(IslandUpgradeType.CROP_GROWTH));
        if (crops != null) {
            island.setCropGrowthBonusPercent(crops.value());
        }

        UpgradeTier members = getTier(IslandUpgradeType.MEMBER_LIMIT, island.getUpgradeTier(IslandUpgradeType.MEMBER_LIMIT));
        if (members != null) {
            island.setMaxMembers(members.value());
        }

        UpgradeTier cobbleGen = getTier(
                IslandUpgradeType.COBBLE_GENERATOR_YIELD,
                island.getUpgradeTier(IslandUpgradeType.COBBLE_GENERATOR_YIELD)
        );
        if (cobbleGen != null) {
            island.setCobbleGeneratorYield(cobbleGen.value());
        }
    }

    public boolean purchaseUpgrade(Player player, Island island, IslandUpgradeType type) {
        if (player == null || island == null || type == null || plugin == null || islandManager == null) {
            return false;
        }
        if (!island.getPlayerRole(player.getUniqueId()).isAtLeast(IslandRole.CO_OWNER)) {
            player.sendMessage(ChatUtils.getMessage(
                    plugin.getConfig(),
                    "messages.island_no_permission",
                    "&cNie masz uprawnień do tej akcji na tej wyspie!"
            ));
            return false;
        }

        int currentTier = island.getUpgradeTier(type);
        UpgradeTier nextTier = getExactTier(type, currentTier + 1);
        if (nextTier == null) {
            player.sendMessage(ChatUtils.getMessage(
                            plugin.getConfig(),
                            "messages.island_upgrade_max",
                            "&eTo ulepszenie osiągnęło już maksymalny poziom (&6Tier %tier%&e)!"
                    )
                    .replace("%tier%", String.valueOf(currentTier)));
            return false;
        }

        EconomyManager economy = plugin.getEconomyModule()
                .map(EconomyModule::getEconomyManager)
                .orElse(null);
        if (economy == null) {
            player.sendMessage(ChatUtils.color("&cModuł ekonomii jest wyłączony — nie można kupić ulepszenia!"));
            return false;
        }

        double cost = nextTier.cost();
        if (cost > 0.0) {
            if (!economy.hasMoney(player.getUniqueId(), cost) || !economy.takeMoney(player.getUniqueId(), cost)) {
                player.sendMessage(ChatUtils.getMessage(
                                plugin.getConfig(),
                                "messages.island_upgrade_no_money",
                                "&cNie stać Cię na to ulepszenie! Wymagany koszt: &e%cost%&c."
                        )
                        .replace("%cost%", EconomyManager.formatMoney(cost)));
                return false;
            }
        }

        island.setUpgradeTier(type, nextTier.tier());
        applyUpgradesToIsland(island);
        if (type == IslandUpgradeType.WORLD_BORDER) {
            islandManager.refreshBordersOnIsland(island);
        }
        islandManager.saveIsland(island);

        player.sendMessage(ChatUtils.getMessage(
                        plugin.getConfig(),
                        "messages.island_upgrade_bought",
                        "&aUlepszono &e%upgrade% &ana poziom &6Tier %tier% &8(&f%value%&8) &aza &e%cost%&a!"
                )
                .replace("%upgrade%", ChatUtils.color(type.getDisplayName()))
                .replace("%tier%", String.valueOf(nextTier.tier()))
                .replace("%value%", type.formatValue(nextTier.value()))
                .replace("%cost%", EconomyManager.formatMoney(cost)));
        return true;
    }
}
