package pl.nauka.aradium.modules.skyblock.quests;

import org.bukkit.Material;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Definicja jednego z 7 etapów misji wyspy zawierającego 7 zadań.
 */
public record QuestStage(
        int stageNumber,
        String id,
        String name,
        String description,
        Material icon,
        double unlockRewardMoney,
        Map<String, QuestDefinition> questsById
) {

    public QuestStage(
            int stageNumber,
            String id,
            String name,
            String description,
            double unlockRewardMoney,
            Map<String, QuestDefinition> questsById
    ) {
        this(stageNumber, id, name, description, defaultIconForStage(stageNumber), unlockRewardMoney, questsById);
    }

    public QuestStage {
        Objects.requireNonNull(id, "id");
        name = name != null ? name : ("Etap " + stageNumber);
        description = description != null ? description : "";
        icon = icon != null ? icon : defaultIconForStage(stageNumber);
        unlockRewardMoney = Math.max(0.0, unlockRewardMoney);
        questsById = questsById != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(questsById))
                : Map.of();
    }

    public static Material defaultIconForStage(int stageNumber) {
        return switch (stageNumber) {
            case 1 -> Material.COAL;
            case 2 -> Material.COPPER_INGOT;
            case 3 -> Material.IRON_INGOT;
            case 4 -> Material.GOLD_INGOT;
            case 5 -> Material.DIAMOND;
            case 6 -> Material.EMERALD;
            case 7 -> Material.NETHERITE_INGOT;
            default -> Material.BOOK;
        };
    }

    public Collection<QuestDefinition> getQuests() {
        return questsById.values();
    }
}
