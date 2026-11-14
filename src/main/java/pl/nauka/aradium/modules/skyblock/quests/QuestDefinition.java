package pl.nauka.aradium.modules.skyblock.quests;

import org.bukkit.Material;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Niezmienna definicja pojedynczej misji wyspy wczytana z pliku quests.yml.
 */
public record QuestDefinition(
        String id,
        int stageNumber,
        int slot,
        QuestType type,
        Material icon,
        String name,
        List<String> lore,
        Map<String, Integer> objectives,
        double rewardMoney,
        List<String> rewardItems
) {

    public QuestDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        icon = icon != null ? icon : Material.PAPER;
        name = name != null ? name : id;
        lore = lore != null ? List.copyOf(lore) : List.of();
        objectives = objectives != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(objectives))
                : Map.of();
        rewardMoney = Math.max(0.0, rewardMoney);
        rewardItems = rewardItems != null ? List.copyOf(rewardItems) : List.of();
    }

    public int getRequiredAmount(String objectiveKey) {
        if (objectiveKey == null) {
            return 0;
        }
        return objectives.getOrDefault(objectiveKey, 0);
    }

    public int getTotalRequired() {
        int sum = 0;
        for (int req : objectives.values()) {
            sum += Math.max(0, req);
        }
        return sum;
    }
}
