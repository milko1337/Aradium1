package pl.nauka.aradium.modules.skyblock.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import pl.nauka.aradium.modules.skyblock.island.IslandRole;
import pl.nauka.aradium.modules.skyblock.island.IslandWarp;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Dedykowany InventoryHolder dla wszystkich menu SkyBlock GUI (Anti-Dupe).
 */
public class SkyBlockGuiHolder implements InventoryHolder {

    public enum GuiType {
        SCHEMATIC_SELECT,
        ISLAND_PANEL,
        ROLES_MENU,
        WARPS_MENU,
        UPGRADES_MENU,
        TOP_MENU
    }

    private final GuiType guiType;
    private final UUID islandId;
    private final IslandRole selectedRole;
    private final Map<Integer, IslandWarp> warpSlots = new HashMap<>();
    private final Map<Integer, UUID> targetIslandSlots = new HashMap<>();
    private Inventory inventory;

    public SkyBlockGuiHolder(GuiType guiType, UUID islandId, IslandRole selectedRole) {
        this.guiType = Objects.requireNonNull(guiType, "guiType");
        this.islandId = islandId;
        this.selectedRole = selectedRole;
    }

    public GuiType getGuiType() {
        return guiType;
    }

    public UUID getIslandId() {
        return islandId;
    }

    public IslandRole getSelectedRole() {
        return selectedRole;
    }

    public void registerWarpSlot(int slot, IslandWarp warp) {
        if (warp != null) {
            warpSlots.put(slot, warp);
        }
    }

    public IslandWarp getWarpAtSlot(int slot) {
        return warpSlots.get(slot);
    }

    public void registerTargetIslandSlot(int slot, UUID targetIslandId) {
        if (targetIslandId != null) {
            targetIslandSlots.put(slot, targetIslandId);
        }
    }

    public UUID getTargetIslandAtSlot(int slot) {
        return targetIslandSlots.get(slot);
    }

    public Map<Integer, IslandWarp> getWarpSlotsSnapshot() {
        return Collections.unmodifiableMap(warpSlots);
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
