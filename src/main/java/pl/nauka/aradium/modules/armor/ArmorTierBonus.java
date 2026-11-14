package pl.nauka.aradium.modules.armor;

import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.Map;

/**
 * Bonusy oraz metadane pojedynczego tieru zestawu zbroi.
 */
public record ArmorTierBonus(
        int tier,
        Map<PotionEffectType, Integer> effects,
        int maxHealthLevels,
        List<String> lore,
        int customModelData
) {
    /**
     * Jeden poziom efektu Health Boost / zwiększonego zdrowia odpowiada +4.0 HP (+2 serca).
     */
    public static final double HEALTH_PER_LEVEL = 4.0;

    public ArmorTierBonus {
        effects = effects != null ? Map.copyOf(effects) : Map.of();
        lore = lore != null ? List.copyOf(lore) : List.of();
    }

    public static double toMaxHealthBonus(int maxHealthLevels) {
        return Math.max(0, maxHealthLevels) * HEALTH_PER_LEVEL;
    }
}
