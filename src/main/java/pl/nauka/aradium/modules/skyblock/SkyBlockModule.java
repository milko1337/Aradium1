package pl.nauka.aradium.modules.skyblock;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import pl.nauka.aradium.AradiumPlugin;
import pl.nauka.aradium.core.AbstractModule;
import pl.nauka.aradium.modules.skyblock.commands.IslandCommand;
import pl.nauka.aradium.modules.skyblock.gui.IslandPanelGUI;
import pl.nauka.aradium.modules.skyblock.gui.IslandRolesGUI;
import pl.nauka.aradium.modules.skyblock.gui.IslandSchematicGUI;
import pl.nauka.aradium.modules.skyblock.gui.IslandTopGUI;
import pl.nauka.aradium.modules.skyblock.gui.IslandUpgradesGUI;
import pl.nauka.aradium.modules.skyblock.gui.IslandWarpsGUI;
import pl.nauka.aradium.modules.skyblock.gui.QuestGUI;
import pl.nauka.aradium.modules.skyblock.gui.SkyBlockGuiHolder;
import pl.nauka.aradium.modules.skyblock.island.IslandManager;
import pl.nauka.aradium.modules.skyblock.level.BlockValueManager;
import pl.nauka.aradium.modules.skyblock.level.IslandLevelCalculator;
import pl.nauka.aradium.modules.skyblock.level.IslandTopCache;
import pl.nauka.aradium.modules.skyblock.listeners.IslandProtectionListener;
import pl.nauka.aradium.modules.skyblock.listeners.QuestProgressListener;
import pl.nauka.aradium.modules.skyblock.listeners.SpawnerLimitListener;
import pl.nauka.aradium.modules.skyblock.quests.QuestManager;
import pl.nauka.aradium.modules.skyblock.upgrades.CobbleGeneratorManager;
import pl.nauka.aradium.modules.skyblock.upgrades.IslandUpgradeManager;

import java.io.File;

/**
 * Główny koordynator Modułu 3–5 (SkyBlock Core: wyspy, role, warpy, WorldBorder, poziomy i ulepszenia).
 */
public class SkyBlockModule extends AbstractModule {

    private IslandManager islandManager;
    private IslandUpgradeManager upgradeManager;
    private CobbleGeneratorManager cobbleGeneratorManager;
    private BlockValueManager blockValueManager;
    private IslandTopCache topCache;
    private IslandLevelCalculator levelCalculator;
    private QuestManager questManager;
    private IslandSchematicGUI schematicGUI;
    private IslandPanelGUI panelGUI;
    private IslandRolesGUI rolesGUI;
    private IslandWarpsGUI warpsGUI;
    private IslandUpgradesGUI upgradesGUI;
    private IslandTopGUI topGUI;
    private QuestGUI questGUI;

    public SkyBlockModule(AradiumPlugin plugin) {
        super(plugin, "skyblock");
    }

    @Override
    protected void onEnable() {
        File yamlFallback = new File(plugin.getDataFolder(), "skyblock_data.yml");
        this.islandManager = new IslandManager(plugin, plugin.getDatabaseManager(), yamlFallback, plugin.getLogger());
        this.upgradeManager = new IslandUpgradeManager(plugin, islandManager);
        this.upgradeManager.reload();
        this.cobbleGeneratorManager = new CobbleGeneratorManager(plugin);
        this.upgradeManager.syncCobbleGeneratorTiers(cobbleGeneratorManager);
        this.islandManager.setUpgradeManager(upgradeManager);

        this.islandManager.reloadSettings();
        this.islandManager.ensureSkyBlockWorld();
        this.islandManager.loadAll();

        this.blockValueManager = new BlockValueManager(plugin);
        this.topCache = new IslandTopCache();
        this.topCache.refresh(this.islandManager.getAllIslands());
        this.levelCalculator = new IslandLevelCalculator(plugin, islandManager, blockValueManager, topCache);
        this.questManager = new QuestManager(plugin, islandManager);
        this.levelCalculator.setQuestManager(this.questManager);

        this.schematicGUI = new IslandSchematicGUI(plugin, islandManager);
        this.panelGUI = new IslandPanelGUI(plugin, this);
        this.rolesGUI = new IslandRolesGUI(plugin, this);
        this.warpsGUI = new IslandWarpsGUI(plugin, this);
        this.upgradesGUI = new IslandUpgradesGUI(plugin, this);
        this.topGUI = new IslandTopGUI(plugin, this);
        this.questGUI = new QuestGUI(plugin, this);

        registerCommand("island", new IslandCommand(plugin, this));
        registerListener(new IslandProtectionListener(plugin, this));
        registerListener(new SpawnerLimitListener(plugin, this));
        registerListener(new QuestProgressListener(this));

        for (Player online : Bukkit.getOnlinePlayers()) {
            this.islandManager.refreshWorldBorder(online, true);
        }
    }

    @Override
    protected void onDisable() {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getOpenInventory().getTopInventory().getHolder() instanceof SkyBlockGuiHolder) {
                online.closeInventory();
            }
        }
        if (islandManager != null) {
            islandManager.saveAll();
        }
    }

    public IslandManager getIslandManager() {
        return islandManager;
    }

    public IslandSchematicGUI getSchematicGUI() {
        return schematicGUI;
    }

    public IslandPanelGUI getPanelGUI() {
        return panelGUI;
    }

    public IslandRolesGUI getRolesGUI() {
        return rolesGUI;
    }

    public IslandWarpsGUI getWarpsGUI() {
        return warpsGUI;
    }

    public IslandUpgradeManager getUpgradeManager() {
        return upgradeManager;
    }

    public CobbleGeneratorManager getCobbleGeneratorManager() {
        return cobbleGeneratorManager;
    }

    public BlockValueManager getBlockValueManager() {
        return blockValueManager;
    }

    public IslandTopCache getTopCache() {
        return topCache;
    }

    public IslandLevelCalculator getLevelCalculator() {
        return levelCalculator;
    }

    public IslandUpgradesGUI getUpgradesGUI() {
        return upgradesGUI;
    }

    public IslandTopGUI getTopGUI() {
        return topGUI;
    }

    public QuestManager getQuestManager() {
        return questManager;
    }

    public QuestGUI getQuestGUI() {
        return questGUI;
    }
}
