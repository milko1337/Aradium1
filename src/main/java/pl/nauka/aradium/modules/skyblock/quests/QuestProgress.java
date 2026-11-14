package pl.nauka.aradium.modules.skyblock.quests;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Współdzielony stan postępu pojedynczej misji na wyspie wraz z serializacją JSON do bazy danych.
 */
public class QuestProgress {

    private final String questId;
    private final int stageNumber;
    private final Map<String, Integer> currentAmounts = new ConcurrentHashMap<>();
    private volatile boolean completed;
    private volatile long completedAt;

    public QuestProgress(String questId, int stageNumber) {
        this(questId, stageNumber, Map.of(), false, 0L);
    }

    public QuestProgress(
            String questId,
            int stageNumber,
            Map<String, Integer> initialAmounts,
            boolean completed,
            long completedAt
    ) {
        this.questId = Objects.requireNonNull(questId, "questId");
        this.stageNumber = Math.max(1, stageNumber);
        if (initialAmounts != null) {
            for (Map.Entry<String, Integer> entry : initialAmounts.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    this.currentAmounts.put(entry.getKey(), Math.max(0, entry.getValue()));
                }
            }
        }
        this.completed = completed;
        this.completedAt = Math.max(0L, completedAt);
    }

    public String getQuestId() {
        return questId;
    }

    public int getStageNumber() {
        return stageNumber;
    }

    public int getProgress(String objectiveKey) {
        if (objectiveKey == null) {
            return 0;
        }
        return currentAmounts.getOrDefault(objectiveKey, 0);
    }

    public boolean addProgress(String objectiveKey, int delta, int cap) {
        if (completed || objectiveKey == null || delta <= 0 || cap <= 0) {
            return false;
        }
        int current = currentAmounts.getOrDefault(objectiveKey, 0);
        if (current >= cap) {
            return false;
        }
        int updated = (int) Math.min((long) cap, (long) current + delta);
        currentAmounts.put(objectiveKey, updated);
        return updated != current;
    }

    public boolean setProgress(String objectiveKey, int value, int cap) {
        if (completed || objectiveKey == null || cap <= 0) {
            return false;
        }
        int normalized = Math.min(Math.max(0, value), cap);
        Integer prev = currentAmounts.get(objectiveKey);
        if (prev != null && prev == normalized) {
            return false;
        }
        currentAmounts.put(objectiveKey, normalized);
        return true;
    }

    public boolean isAllObjectivesMet(QuestDefinition definition) {
        if (definition == null || definition.objectives().isEmpty()) {
            return false;
        }
        for (Map.Entry<String, Integer> entry : definition.objectives().entrySet()) {
            int required = Math.max(0, entry.getValue());
            if (getProgress(entry.getKey()) < required) {
                return false;
            }
        }
        return true;
    }

    public int getCompletedTotal(QuestDefinition definition) {
        if (definition == null) {
            return 0;
        }
        int total = 0;
        for (Map.Entry<String, Integer> entry : definition.objectives().entrySet()) {
            int required = Math.max(0, entry.getValue());
            total += Math.min(getProgress(entry.getKey()), required);
        }
        return total;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void markCompleted(long timestampMillis) {
        this.completed = true;
        this.completedAt = Math.max(0L, timestampMillis);
    }

    public long getCompletedAt() {
        return completedAt;
    }

    public Map<String, Integer> getProgressSnapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(currentAmounts));
    }

    public String toJson() {
        TreeMap<String, Integer> sorted = new TreeMap<>(currentAmounts);
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Integer> entry : sorted.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(escapeJsonKey(entry.getKey())).append("\":").append(entry.getValue());
        }
        sb.append('}');
        return sb.toString();
    }

    public static Map<String, Integer> fromJson(String json) {
        Map<String, Integer> parsed = new LinkedHashMap<>();
        if (json == null) {
            return parsed;
        }
        String trimmed = json.trim();
        if (trimmed.length() < 2 || trimmed.charAt(0) != '{' || trimmed.charAt(trimmed.length() - 1) != '}') {
            return parsed;
        }
        String body = trimmed.substring(1, trimmed.length() - 1).trim();
        if (body.isEmpty()) {
            return parsed;
        }

        int i = 0;
        int len = body.length();
        while (i < len) {
            while (i < len && (Character.isWhitespace(body.charAt(i)) || body.charAt(i) == ',')) {
                i++;
            }
            if (i >= len || body.charAt(i) != '"') {
                break;
            }
            i++;
            StringBuilder key = new StringBuilder();
            while (i < len) {
                char c = body.charAt(i++);
                if (c == '\\' && i < len) {
                    key.append(body.charAt(i++));
                } else if (c == '"') {
                    break;
                } else {
                    key.append(c);
                }
            }
            while (i < len && body.charAt(i) != ':') {
                i++;
            }
            if (i >= len) {
                break;
            }
            i++; // skip ':'
            while (i < len && Character.isWhitespace(body.charAt(i))) {
                i++;
            }
            int numStart = i;
            while (i < len && (Character.isDigit(body.charAt(i)) || body.charAt(i) == '-')) {
                i++;
            }
            if (i > numStart && !key.isEmpty()) {
                try {
                    int val = Math.max(0, Integer.parseInt(body.substring(numStart, i)));
                    parsed.put(key.toString(), val);
                } catch (NumberFormatException ignored) {
                    // Pomijamy niepoprawną wartość liczbową.
                }
            }
        }
        return parsed;
    }

    private static String escapeJsonKey(String key) {
        return key.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
