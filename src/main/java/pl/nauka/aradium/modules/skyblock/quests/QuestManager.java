package pl.nauka.aradium.modules.skyblock.quests;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import pl.nauka.aradium.AradiumPlugin;
import pl.nauka.aradium.modules.economy.EconomyManager;
import pl.nauka.aradium.modules.economy.EconomyModule;
import pl.nauka.aradium.modules.skyblock.island.Island;
import pl.nauka.aradium.modules.skyblock.island.IslandManager;
import pl.nauka.aradium.utils.ChatUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Menedżer współdzielonych misji wyspy (5 etapów po 7 zadań):
 * ładowanie quests.yml, aktualizacje postępu, odblokowania etapów oraz wypłata nagród.
 */
public class QuestManager {

    private final AradiumPlugin plugin;
    private final IslandManager islandManager;
    private final File questsFile;
    private final Logger logger;

    private String guiTitleTemplate = "&8» &6Misje Wyspy &8(&eEtap %stage%/5&8)";
    private int barLength = 10;
    private String barSymbol = "■";
    private String barCompletedColor = "&a";
    private String barRemainingColor = "&7";
    private Map<Integer, QuestStage> stagesByNumber = Collections.emptyMap();

    public QuestManager(AradiumPlugin plugin, IslandManager islandManager) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.islandManager = Objects.requireNonNull(islandManager, "islandManager");
        this.questsFile = new File(plugin.getDataFolder(), "quests.yml");
        this.logger = plugin.getLogger();
        reload();
    }

    public QuestManager(Logger logger) {
        this.plugin = null;
        this.islandManager = null;
        this.questsFile = null;
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public synchronized void reload() {
        if (plugin != null && questsFile != null && !questsFile.exists()) {
            plugin.saveResource("quests.yml", false);
        }
        if (questsFile != null && questsFile.exists()) {
            FileConfiguration config = YamlConfiguration.loadConfiguration(questsFile);
            reload(config);
        }
    }

    public synchronized void reload(FileConfiguration config) {
        if (config == null) {
            this.stagesByNumber = Collections.emptyMap();
            return;
        }
        this.guiTitleTemplate = config.getString("settings.gui-title", "&8» &6Misje Wyspy &8(&eEtap %stage%/5&8)");
        this.barLength = Math.clamp(config.getInt("settings.progress-bar.length", 10), 4, 30);
        this.barSymbol = config.getString("settings.progress-bar.symbol", "■");
        this.barCompletedColor = config.getString("settings.progress-bar.completed-color", "&a");
        this.barRemainingColor = config.getString("settings.progress-bar.remaining-color", "&7");
        this.stagesByNumber = Collections.unmodifiableMap(parseStages(config, logger));
    }

    public static Map<Integer, QuestStage> parseStages(FileConfiguration config, Logger logger) {
        TreeMap<Integer, QuestStage> stages = new TreeMap<>();
        if (config == null) {
            return stages;
        }
        ConfigurationSection stagesSec = config.getConfigurationSection("stages");
        if (stagesSec == null) {
            return stages;
        }

        for (String stageKey : stagesSec.getKeys(false)) {
            ConfigurationSection stageSec = stagesSec.getConfigurationSection(stageKey);
            if (stageSec == null) {
                continue;
            }
            int stageNum = stageSec.getInt("stage", 0);
            if (stageNum < 1) {
                continue;
            }
            String stageName = stageSec.getString("name", "&eEtap " + stageNum);
            String stageDesc = stageSec.getString("description", "");
            double unlockReward = Math.max(0.0, stageSec.getDouble("unlock-reward-money", 0.0));

            Map<String, QuestDefinition> questsMap = new LinkedHashMap<>();
            ConfigurationSection questsSec = stageSec.getConfigurationSection("quests");
            if (questsSec != null) {
                for (String questId : questsSec.getKeys(false)) {
                    ConfigurationSection qSec = questsSec.getConfigurationSection(questId);
                    if (qSec == null) {
                        continue;
                    }
                    QuestType type = QuestType.parse(qSec.getString("type"));
                    if (type == null) {
                        if (logger != null) {
                            logger.warning("Nieznany typ misji '" + qSec.getString("type") + "' w " + stageKey + "." + questId);
                        }
                        continue;
                    }

                    Material icon = Material.PAPER;
                    String iconRaw = qSec.getString("icon", "PAPER");
                    try {
                        icon = Material.valueOf(iconRaw.trim().toUpperCase(Locale.ROOT));
                    } catch (IllegalArgumentException ignored) {
                        if (logger != null) {
                            logger.warning("Nieznana ikona '" + iconRaw + "' w misji " + questId + " — użyto PAPER.");
                        }
                    }

                    int slot = qSec.getInt("slot", 10);
                    String name = qSec.getString("name", questId);
                    List<String> lore = qSec.getStringList("lore");

                    Map<String, Integer> objectives = new LinkedHashMap<>();
                    ConfigurationSection objSec = qSec.getConfigurationSection("objectives");
                    if (objSec != null) {
                        for (String objKey : objSec.getKeys(false)) {
                            int req = Math.max(1, objSec.getInt(objKey, 1));
                            objectives.put(objKey.trim().toUpperCase(Locale.ROOT), req);
                        }
                    }

                    double rewardMoney = Math.max(0.0, qSec.getDouble("rewards.money", 0.0));
                    List<String> rewardItems = qSec.getStringList("rewards.items");

                    questsMap.put(questId, new QuestDefinition(
                            questId, stageNum, slot, type, icon, name, lore, objectives, rewardMoney, rewardItems
                    ));
                }
            }

            stages.put(stageNum, new QuestStage(stageNum, stageKey, stageName, stageDesc, unlockReward, questsMap));
        }
        return stages;
    }

    public static String buildProgressBar(
            int current,
            int required,
            int length,
            String symbol,
            String completedColor,
            String remainingColor
    ) {
        int safeLength = Math.max(1, length);
        int safeReq = Math.max(1, required);
        int clamped = Math.min(Math.max(0, current), safeReq);
        double ratio = (double) clamped / safeReq;
        int filled = (int) Math.round(ratio * safeLength);
        int percent = (int) Math.round(ratio * 100.0);

        String sym = (symbol != null && !symbol.isEmpty()) ? symbol : "■";
        String doneCol = completedColor != null ? completedColor : "&a";
        String remCol = remainingColor != null ? remainingColor : "&7";

        StringBuilder sb = new StringBuilder();
        if (filled > 0) {
            sb.append(doneCol);
            for (int i = 0; i < filled; i++) {
                sb.append(sym);
            }
        }
        if (filled < safeLength) {
            sb.append(remCol);
            for (int i = filled; i < safeLength; i++) {
                sb.append(sym);
            }
        }
        sb.append(" &e").append(percent).append("%");
        return sb.toString();
    }

    public String buildProgressBar(int current, int required) {
        return buildProgressBar(current, required, barLength, barSymbol, barCompletedColor, barRemainingColor);
    }

    public synchronized String getGuiTitleTemplate() {
        return guiTitleTemplate;
    }

    public synchronized Map<Integer, QuestStage> getStagesSnapshot() {
        return stagesByNumber;
    }

    public synchronized QuestStage getStage(int stageNumber) {
        return stagesByNumber.get(stageNumber);
    }

    public synchronized boolean isStageCompleted(Island island, int stageNumber) {
        if (island == null) {
            return false;
        }
        QuestStage stage = stagesByNumber.get(stageNumber);
        if (stage == null || stage.questsById().isEmpty()) {
            return false;
        }
        for (QuestDefinition quest : stage.getQuests()) {
            QuestProgress progress = island.getQuestProgress(quest.id());
            if (progress == null || !progress.isCompleted()) {
                return false;
            }
        }
        return true;
    }

    public synchronized boolean isStageUnlocked(Island island, int stageNumber) {
        if (stageNumber <= 1) {
            return true;
        }
        return isStageCompleted(island, stageNumber - 1);
    }

    public synchronized int getHighestUnlockedStage(Island island) {
        int highest = 1;
        for (Integer stageNum : stagesByNumber.keySet()) {
            if (stageNum > 1 && isStageUnlocked(island, stageNum)) {
                highest = Math.max(highest, stageNum);
            }
        }
        return highest;
    }

    public void recordProgress(Player actor, Island island, QuestType actionType, String objectiveKey, int amount) {
        if (island == null || actionType == null || objectiveKey == null || amount <= 0) {
            return;
        }
        String normalizedKey = objectiveKey.trim().toUpperCase(Locale.ROOT);

        for (QuestStage stage : getStagesSnapshot().values()) {
            if (!isStageUnlocked(island, stage.stageNumber())) {
                continue;
            }
            for (QuestDefinition quest : stage.getQuests()) {
                if (!quest.type().matchesAction(actionType)) {
                    continue;
                }
                int required = quest.getRequiredAmount(normalizedKey);
                if (required <= 0) {
                    continue;
                }
                QuestProgress progress = island.getOrCreateQuestProgress(quest.id(), stage.stageNumber());
                if (progress.isCompleted()) {
                    continue;
                }
                if (progress.addProgress(normalizedKey, amount, required) && progress.isAllObjectivesMet(quest)) {
                    completeQuest(actor, island, stage, quest, progress);
                }
            }
        }
    }

    public void updateAbsoluteProgress(Player actor, Island island, QuestType actionType, String objectiveKey, int currentValue) {
        if (island == null || actionType == null || objectiveKey == null || currentValue < 0) {
            return;
        }
        String normalizedKey = objectiveKey.trim().toUpperCase(Locale.ROOT);

        for (QuestStage stage : getStagesSnapshot().values()) {
            if (!isStageUnlocked(island, stage.stageNumber())) {
                continue;
            }
            for (QuestDefinition quest : stage.getQuests()) {
                if (!quest.type().matchesAction(actionType)) {
                    continue;
                }
                int required = quest.getRequiredAmount(normalizedKey);
                if (required <= 0) {
                    continue;
                }
                QuestProgress progress = island.getOrCreateQuestProgress(quest.id(), stage.stageNumber());
                if (progress.isCompleted()) {
                    continue;
                }
                if (progress.setProgress(normalizedKey, currentValue, required) && progress.isAllObjectivesMet(quest)) {
                    completeQuest(actor, island, stage, quest, progress);
                }
            }
        }
    }

    public void syncDynamicQuests(Player player, Island island) {
        if (island == null) {
            return;
        }
        int currentLevel = (int) Math.floor(Math.max(0.0, island.getIslandLevel()));
        updateAbsoluteProgress(player, island, QuestType.ISLAND_LEVEL, "ISLAND_LEVEL", currentLevel);

        if (plugin != null && player != null) {
            EconomyManager economy = plugin.getEconomyModule()
                    .map(EconomyModule::getEconomyManager)
                    .orElse(null);
            if (economy != null) {
                int balance = (int) Math.floor(Math.max(0.0, economy.getMoney(player.getUniqueId())));
                updateAbsoluteProgress(player, island, QuestType.BALANCE_REACH, "BALANCE", balance);
            }
        }
    }

    private void completeQuest(Player actor, Island island, QuestStage stage, QuestDefinition quest, QuestProgress progress) {
        progress.markCompleted(System.currentTimeMillis());

        Player rewardRecipient = actor;
        if (plugin != null) {
            if (rewardRecipient == null || !rewardRecipient.isOnline()) {
                rewardRecipient = Bukkit.getPlayer(island.getOwnerUuid());
            }
            EconomyManager economy = plugin.getEconomyModule()
                    .map(EconomyModule::getEconomyManager)
                    .orElse(null);
            if (economy != null && quest.rewardMoney() > 0.0) {
                UUID payTarget = rewardRecipient != null ? rewardRecipient.getUniqueId() : island.getOwnerUuid();
                economy.addMoney(payTarget, quest.rewardMoney());
            }

            if (rewardRecipient != null && !quest.rewardItems().isEmpty()) {
                giveRewardItems(rewardRecipient, quest.rewardItems());
            }

            String msg = ChatUtils.getMessage(
                            plugin.getConfig(),
                            "messages.island_quest_completed",
                            "&a&lMISJA UKOŃCZONA! &7Wyspa ukończyła zadanie &e%quest% &8(&6Etap %stage%&8)&7! Nagroda: &a%money%"
                    )
                    .replace("%quest%", ChatUtils.color(quest.name()))
                    .replace("%stage%", String.valueOf(stage.stageNumber()))
                    .replace("%money%", EconomyManager.formatMoney(quest.rewardMoney()));
            notifyIslandMembers(island, msg);

            if (isStageCompleted(island, stage.stageNumber())) {
                if (economy != null && stage.unlockRewardMoney() > 0.0) {
                    UUID bonusTarget = rewardRecipient != null ? rewardRecipient.getUniqueId() : island.getOwnerUuid();
                    economy.addMoney(bonusTarget, stage.unlockRewardMoney());
                }
                String stageMsg = ChatUtils.getMessage(
                                plugin.getConfig(),
                                "messages.island_stage_completed",
                                "&6&lETAP UKOŃCZONY! &7Twoja wyspa ukończyła cały &eEtap %stage% &7i otrzymała bonus &a%reward%&7!"
                        )
                        .replace("%stage%", String.valueOf(stage.stageNumber()))
                        .replace("%reward%", EconomyManager.formatMoney(stage.unlockRewardMoney()));
                notifyIslandMembers(island, stageMsg);
                syncDynamicQuests(rewardRecipient, island);
            }
        }

        if (islandManager != null) {
            islandManager.saveIsland(island);
        }
    }

    private static void giveRewardItems(Player player, List<String> itemSpecs) {
        List<ItemStack> itemsToGive = new ArrayList<>();
        for (String spec : itemSpecs) {
            if (spec == null || spec.isBlank()) {
                continue;
            }
            String[] parts = spec.trim().split(":");
            try {
                Material mat = Material.valueOf(parts[0].trim().toUpperCase(Locale.ROOT));
                int amount = parts.length > 1 ? Math.clamp(Integer.parseInt(parts[1].trim()), 1, 64) : 1;
                itemsToGive.add(new ItemStack(mat, amount));
            } catch (IllegalArgumentException ignored) {
                // Pomijamy niepoprawny wpis nagrody.
            }
        }
        if (itemsToGive.isEmpty()) {
            return;
        }
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(itemsToGive.toArray(new ItemStack[0]));
        for (ItemStack extra : overflow.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), extra);
        }
    }

    private static void notifyIslandMembers(Island island, String message) {
        Player owner = Bukkit.getPlayer(island.getOwnerUuid());
        if (owner != null && owner.isOnline()) {
            owner.sendMessage(message);
        }
        for (UUID memberId : island.getMembersSnapshot().keySet()) {
            Player member = Bukkit.getPlayer(memberId);
            if (member != null && member.isOnline()) {
                member.sendMessage(message);
            }
        }
    }
}
