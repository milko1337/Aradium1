package pl.nauka.aradium.modules.magicitems;

import org.bukkit.Material;
import org.bukkit.potion.PotionEffectType;

public record MagicItemDefinition(
        String id,
        String name,
        Material material,
        String lore,
        int cooldownSeconds,
        String ability,
        PotionEffectType effectType,
        int durationTicks,
        int spectatorSeconds,
        double flySpeed,
        int blindnessSeconds,
        int speedSeconds
) {

    public boolean isGhostAbility() {
        return "GHOST".equalsIgnoreCase(ability) || "duch".equalsIgnoreCase(id);
    }
}
