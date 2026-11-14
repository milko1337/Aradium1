package pl.nauka.aradium.modules.skyblock.quests;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Definicja jednego z 5 etapów misji wyspy zawierającego 7 zadań.
 */
public record QuestStage(
        int stageNumber,
        String id,
        String name,
        String description,
        double unlockRewardMoney,
        Map<String, QuestDefinition> questsById
) {

    public QuestStage {
        Objects.requireNonNull(id, "id");
        name = name != null ? name : ("Etap " + stageNumber);
        description = description != null ? description : "";
        unlockRewardMoney = Math.max(0.0, unlockRewardMoney);
        questsById = questsById != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(questsById))
                : Map.of();
    }

    public Collection<QuestDefinition> getQuests() {
        return questsById.values();
    }
}
