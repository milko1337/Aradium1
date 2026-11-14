package pl.nauka.aradium.modules.armor;

import java.util.Map;

/**
 * Definicja kompletnego zestawu magicznej zbroi wczytywana z armor.yml.
 */
public record ArmorSetDefinition(
        String id,
        String displayName,
        String materialPrefix,
        String nbtTag,
        int customModelDataBase,
        Map<ArmorPieceType, String> pieceNames,
        Map<Integer, ArmorTierBonus> tiers
) {
    public ArmorSetDefinition {
        pieceNames = pieceNames != null ? Map.copyOf(pieceNames) : Map.of();
        tiers = tiers != null ? Map.copyOf(tiers) : Map.of();
    }

    public ArmorTierBonus getTier(int tier) {
        return tiers.get(tier);
    }

    public String getPieceName(ArmorPieceType pieceType, int tier) {
        String template = pieceNames.getOrDefault(pieceType, "&f" + displayName + " &8[&eTier %tier%&8]");
        return template.replace("%tier%", String.valueOf(tier));
    }
}
