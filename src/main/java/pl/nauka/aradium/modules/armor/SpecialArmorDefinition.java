package pl.nauka.aradium.modules.armor;

import org.bukkit.Material;
import org.bukkit.inventory.EquipmentSlot;

import java.util.List;

/**
 * Definicja specjalnego, pojedynczego elementu zbroi (np. "Buty lotnika").
 */
public record SpecialArmorDefinition(
        String id,
        String name,
        Material material,
        EquipmentSlot slot,
        boolean unbreakable,
        String ability,
        String nbtTag,
        int customModelData,
        List<String> lore
) {
    public SpecialArmorDefinition {
        lore = lore != null ? List.copyOf(lore) : List.of();
    }

    public boolean isFlightAbility() {
        return "FLIGHT".equalsIgnoreCase(ability);
    }
}
