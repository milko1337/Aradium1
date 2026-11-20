package pl.nauka.aradium.modules.customitems;

import org.bukkit.Material;
import org.bukkit.event.entity.CreatureSpawnEvent;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Definicja pochodni anty-mob blokującej naturalny spawn potworów w zadanym promieniu.
 */
public record AntiMobTorchDefinition(
        String id,
        String name,
        Material material,
        int radius,
        List<String> lore,
        Set<CreatureSpawnEvent.SpawnReason> blockedSpawnReasons
) {

    public static final Set<CreatureSpawnEvent.SpawnReason> DEFAULT_BLOCKED_REASONS = Collections.unmodifiableSet(
            EnumSet.of(
                    CreatureSpawnEvent.SpawnReason.NATURAL,
                    CreatureSpawnEvent.SpawnReason.JOCKEY,
                    CreatureSpawnEvent.SpawnReason.MOUNT,
                    CreatureSpawnEvent.SpawnReason.PATROL,
                    CreatureSpawnEvent.SpawnReason.REINFORCEMENTS
            )
    );

    public AntiMobTorchDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(material, "material");
        id = id.toLowerCase(Locale.ROOT);
        name = name != null ? name : "&b&lPochodnia Anty-Mob";
        radius = Math.max(1, Math.min(128, radius));
        lore = lore != null ? List.copyOf(lore) : List.of();
        blockedSpawnReasons = (blockedSpawnReasons != null && !blockedSpawnReasons.isEmpty())
                ? Collections.unmodifiableSet(EnumSet.copyOf(blockedSpawnReasons))
                : DEFAULT_BLOCKED_REASONS;
    }

    public boolean blocksReason(CreatureSpawnEvent.SpawnReason reason) {
        return reason != null && blockedSpawnReasons.contains(reason);
    }

    public boolean isPlacedTorchMaterial(Material placedType) {
        if (placedType == null) {
            return false;
        }
        if (placedType == material) {
            return true;
        }
        if (material == Material.SOUL_TORCH && placedType == Material.SOUL_WALL_TORCH) {
            return true;
        }
        return material == Material.TORCH && placedType == Material.WALL_TORCH;
    }
}
