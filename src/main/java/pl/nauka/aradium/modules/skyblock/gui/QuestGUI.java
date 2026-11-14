package pl.nauka.aradium.modules.skyblock.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import pl.nauka.aradium.AradiumPlugin;
import pl.nauka.aradium.modules.economy.EconomyManager;
import pl.nauka.aradium.modules.skyblock.SkyBlockModule;
import pl.nauka.aradium.modules.skyblock.island.Island;
import pl.nauka.aradium.modules.skyblock.quests.QuestDefinition;
import pl.nauka.aradium.modules.skyblock.quests.QuestManager;
import pl.nauka.aradium.modules.skyblock.quests.QuestProgress;
import pl.nauka.aradium.modules.skyblock.quests.QuestStage;
import pl.nauka.aradium.utils.ChatUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Menu GUI współdzielonych misji wyspy (5 etapów po 7 zadań, paski postępu, status zablokowany/w trakcie/ukończony).
 */
public class QuestGUI {

    private static final int SIZE = 27;
    private static final int STAGE_TAB_OFFSET = 1; // Etap 1..5 -> sloty 2..6
    private static final int SLOT_STAGE_FIRST = 2;
    private static final int SLOT_STAGE_LAST = 6;
    private static final int SLOT_BACK = 22;

    private final AradiumPlugin plugin;
    private final SkyBlockModule module;

    public QuestGUI(AradiumPlugin plugin, SkyBlockModule module) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.module = Objects.requireNonNull(module, "module");
    }

    public void open(Player player, Island island) {
        if (player == null || island == null || module.getQuestManager() == null) {
            return;
        }
        QuestManager questManager = module.getQuestManager();
        questManager.syncDynamicQuests(player, island);
        open(player, island, questManager.getHighestUnlockedStage(island));
    }

    public void open(Player player, Island island, int requestedStage) {
        QuestManager questManager = module.getQuestManager();
        if (player == null || island == null || questManager == null) {
            return;
        }
        questManager.syncDynamicQuests(player, island);

        int stageNum = Math.clamp(requestedStage, 1, 5);
        SkyBlockGuiHolder holder = new SkyBlockGuiHolder(
                SkyBlockGuiHolder.GuiType.QUESTS_MENU,
                island.getIslandId(),
                null,
                stageNum
        );
        String rawTitle = questManager.getGuiTitleTemplate().replace("%stage%", String.valueOf(stageNum));
        Inventory inventory = Bukkit.createInventory(holder, SIZE, ChatUtils.component(rawTitle));
        holder.setInventory(inventory);

        ItemStack filler = createItem(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < SIZE; i++) {
            inventory.setItem(i, filler);
        }

        for (int s = 1; s <= 5; s++) {
            int slot = STAGE_TAB_OFFSET + s;
            inventory.setItem(slot, buildStageTab(questManager, island, s, s == stageNum));
        }

        QuestStage currentStage = questManager.getStage(stageNum);
        boolean stageUnlocked = questManager.isStageUnlocked(island, stageNum);
        if (currentStage != null) {
            for (QuestDefinition quest : currentStage.getQuests()) {
                int slot = quest.slot();
                if (slot >= 0 && slot < SIZE) {
                    inventory.setItem(slot, buildQuestItem(questManager, island, stageNum, stageUnlocked, quest));
                }
            }
        }

        inventory.setItem(SLOT_BACK, createItem(
                Material.ARROW,
                "&c&lPowrót do Panelu",
                List.of("&7Kliknij, aby wrócić do głównego menu wyspy.")
        ));

        player.openInventory(inventory);
    }

    public void handleClick(Player player, SkyBlockGuiHolder holder, int slot) {
        Island island = module.getIslandManager().getIslandById(holder.getIslandId()).orElse(null);
        if (island == null) {
            player.closeInventory();
            return;
        }

        if (slot == SLOT_BACK) {
            plugin.getServer().getScheduler().runTask(plugin, () -> module.getPanelGUI().open(player, island));
            return;
        }

        if (slot >= SLOT_STAGE_FIRST && slot <= SLOT_STAGE_LAST) {
            int clickedStage = slot - STAGE_TAB_OFFSET;
            plugin.getServer().getScheduler().runTask(plugin, () -> open(player, island, clickedStage));
            return;
        }

        if (slot >= 10 && slot <= 16) {
            int currentStage = holder.getSelectedStage();
            plugin.getServer().getScheduler().runTask(plugin, () -> open(player, island, currentStage));
        }
    }

    private static ItemStack buildStageTab(QuestManager questManager, Island island, int stageNum, boolean selected) {
        QuestStage stage = questManager.getStage(stageNum);
        boolean completed = questManager.isStageCompleted(island, stageNum);
        boolean unlocked = questManager.isStageUnlocked(island, stageNum);

        Material pane = completed
                ? Material.LIME_STAINED_GLASS_PANE
                : (unlocked ? Material.YELLOW_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE);

        String displayName = stage != null ? stage.name() : ("&eEtap " + stageNum);
        List<String> lore = new ArrayList<>();
        if (stage != null && !stage.description().isBlank()) {
            lore.add(stage.description());
        }

        int completedCount = 0;
        int totalCount = stage != null ? stage.getQuests().size() : 0;
        if (stage != null) {
            for (QuestDefinition q : stage.getQuests()) {
                QuestProgress qp = island.getQuestProgress(q.id());
                if (qp != null && qp.isCompleted()) {
                    completedCount++;
                }
            }
        }

        lore.add("");
        lore.add("&8» &7Ukończone zadania: &e" + completedCount + "&8/&a" + totalCount);
        if (stage != null && stage.unlockRewardMoney() > 0.0) {
            lore.add("&8» &7Bonus za ukończenie etapu: &a+" + EconomyManager.formatMoney(stage.unlockRewardMoney()));
        }
        lore.add("");
        if (completed) {
            lore.add("&a&l✔ ETAP UKOŃCZONY");
        } else if (unlocked) {
            lore.add("&e&l⌛ ETAP AKTYWNY");
        } else {
            lore.add("&c&l✖ ZABLOKOWANY &8(&7Ukończ Etap " + (stageNum - 1) + "&8)");
        }
        lore.add(selected ? "&b▸ Aktualnie przeglądany etap" : "&eKliknij, aby otworzyć Etap " + stageNum + "!");

        return createItem(pane, displayName, lore);
    }

    private static ItemStack buildQuestItem(
            QuestManager questManager,
            Island island,
            int stageNum,
            boolean stageUnlocked,
            QuestDefinition quest
    ) {
        QuestProgress progress = island.getQuestProgress(quest.id());
        boolean completed = progress != null && progress.isCompleted();

        Material paneMaterial;
        if (!stageUnlocked) {
            paneMaterial = Material.RED_STAINED_GLASS_PANE;
        } else if (completed) {
            paneMaterial = Material.LIME_STAINED_GLASS_PANE;
        } else {
            paneMaterial = Material.YELLOW_STAINED_GLASS_PANE;
        }

        List<String> lore = new ArrayList<>();
        for (String line : quest.lore()) {
            String resolved = line;
            for (Map.Entry<String, Integer> obj : quest.objectives().entrySet()) {
                int required = Math.max(0, obj.getValue());
                int currentVal = !stageUnlocked
                        ? 0
                        : (completed ? required : (progress != null ? Math.min(progress.getProgress(obj.getKey()), required) : 0));
                resolved = resolved.replace("%progress_" + obj.getKey() + "%", String.valueOf(currentVal));
            }
            lore.add(resolved);
        }

        lore.add("");
        lore.add("&8» &7Nagrody za ukończenie:");
        if (quest.rewardMoney() > 0.0) {
            lore.add("  &6• &a+" + EconomyManager.formatMoney(quest.rewardMoney()));
        }
        for (String itemSpec : quest.rewardItems()) {
            lore.add("  &6• &f" + formatRewardSpec(itemSpec));
        }

        lore.add("");
        int totalRequired = quest.getTotalRequired();
        if (!stageUnlocked) {
            lore.add("&c&l✖ ZABLOKOWANE");
            lore.add("&7Ukończ wszystkie zadania w &eEtapie " + (stageNum - 1) + "&7,");
            lore.add("&7aby odblokować tę misję!");
        } else if (completed) {
            lore.add("&8» &7Postęp: " + questManager.buildProgressBar(totalRequired, totalRequired));
            lore.add("&a&l✔ MISJA UKOŃCZONA!");
        } else {
            int doneTotal = progress != null ? progress.getCompletedTotal(quest) : 0;
            lore.add("&8» &7Postęp: " + questManager.buildProgressBar(doneTotal, totalRequired)
                    + " &8(&f" + doneTotal + "&7/&f" + totalRequired + "&8)");
            lore.add("&e&l⌛ W TRAKCIE REALIZACJI");
        }

        return createItem(paneMaterial, quest.name(), lore);
    }

    private static String formatRewardSpec(String spec) {
        if (spec == null || spec.isBlank()) {
            return "";
        }
        String[] parts = spec.trim().split(":");
        String itemName = parts[0].trim().replace('_', ' ').toLowerCase(Locale.ROOT);
        String amount = parts.length > 1 ? parts[1].trim() : "1";
        return amount + "x " + capitalizeWords(itemName);
    }

    private static String capitalizeWords(String input) {
        if (input.isEmpty()) {
            return input;
        }
        StringBuilder sb = new StringBuilder(input.length());
        boolean nextUpper = true;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (Character.isWhitespace(c)) {
                nextUpper = true;
                sb.append(c);
            } else if (nextUpper) {
                sb.append(Character.toUpperCase(c));
                nextUpper = false;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static ItemStack createItem(Material material, String name, List<String> loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(ChatUtils.itemComponent(name));
            if (loreLines != null && !loreLines.isEmpty()) {
                meta.lore(loreLines.stream()
                        .map(line -> line.isEmpty() ? Component.empty() : ChatUtils.itemComponent(line))
                        .toList());
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }
}
