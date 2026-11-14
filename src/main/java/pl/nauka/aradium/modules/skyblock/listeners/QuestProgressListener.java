package pl.nauka.aradium.modules.skyblock.listeners;

import io.papermc.paper.event.player.PlayerPurchaseEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import pl.nauka.aradium.modules.skyblock.SkyBlockModule;
import pl.nauka.aradium.modules.skyblock.island.Island;
import pl.nauka.aradium.modules.skyblock.island.IslandManager;
import pl.nauka.aradium.modules.skyblock.island.IslandRole;
import pl.nauka.aradium.modules.skyblock.quests.QuestManager;
import pl.nauka.aradium.modules.skyblock.quests.QuestType;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Zoptymalizowany nasłuch zdarzeń rozgrywki zliczający postępy wszystkich 5 etapów misji wyspy
 * (wykopki, uprawy i zbiory, przepalanie/craft, handel z osadnikami, alchemia II, walki z mobami oraz wędkarstwo).
 */
public class QuestProgressListener implements Listener {

    private static final int MAX_COLUMN_SCAN = 64;

    private final IslandManager islandManager;
    private final QuestManager questManager;

    public QuestProgressListener(SkyBlockModule module) {
        Objects.requireNonNull(module, "module");
        this.islandManager = Objects.requireNonNull(module.getIslandManager(), "islandManager");
        this.questManager = Objects.requireNonNull(module.getQuestManager(), "questManager");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        Island island = resolveBlockIsland(player, block.getLocation());
        if (island == null) {
            return;
        }

        Material type = block.getType();
        if (type == Material.SUGAR_CANE || type == Material.CACTUS) {
            int columnHeight = countConnectedVerticalBlocks(block, type);
            questManager.recordProgress(player, island, QuestType.COLLECT, type.name(), columnHeight);
            return;
        }

        if (type == Material.MELON || type == Material.PUMPKIN) {
            questManager.recordProgress(player, island, QuestType.COLLECT, type.name(), 1);
            return;
        }

        if (Tag.LOGS.isTagged(type)) {
            questManager.recordProgress(player, island, QuestType.BLOCK_BREAK, "ANY_LOG", 1);
            return;
        }

        Material normalized = normalizeOreMaterial(type);
        questManager.recordProgress(player, island, QuestType.BLOCK_BREAK, normalized.name(), 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockDropItem(BlockDropItemEvent event) {
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        Island island = resolveBlockIsland(player, event.getBlock().getLocation());
        if (island == null) {
            return;
        }

        Material brokenType = event.getBlockState().getType();
        if (!isTrackedCropBlock(brokenType)) {
            return;
        }
        if (!(event.getBlockState().getBlockData() instanceof Ageable ageable)
                || ageable.getAge() < ageable.getMaximumAge()) {
            return;
        }

        for (Item droppedEntity : event.getItems()) {
            ItemStack stack = droppedEntity.getItemStack();
            Material dropMat = stack.getType();
            if (isTrackedCropDrop(dropMat)) {
                questManager.recordProgress(player, island, QuestType.COLLECT, dropMat.name(), stack.getAmount());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        Item itemEntity = event.getItem();
        if (itemEntity.getItemStack().getType() != Material.EGG || itemEntity.getThrower() != null) {
            return;
        }

        Island island = resolveBlockIsland(player, itemEntity.getLocation());
        if (island != null) {
            questManager.recordProgress(player, island, QuestType.COLLECT, "EGG", itemEntity.getItemStack().getAmount());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFurnaceExtract(FurnaceExtractEvent event) {
        Player player = event.getPlayer();
        Island island = resolvePlayerIsland(player);
        if (island == null) {
            return;
        }
        int amount = Math.max(0, event.getItemAmount());
        if (amount > 0) {
            questManager.recordProgress(player, island, QuestType.SMELT, event.getItemType().name(), amount);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraftItem(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getSlotType() != InventoryType.SlotType.RESULT) {
            return;
        }
        ItemStack current = event.getCurrentItem();
        if (current == null || current.getType().isAir()) {
            return;
        }
        ItemStack result = event.getRecipe() != null ? event.getRecipe().getResult() : current;
        if (result.getType().isAir()) {
            return;
        }

        Island island = resolvePlayerIsland(player);
        if (island == null) {
            return;
        }

        int craftedAmount = computeCraftedAmount(player, event, result);
        if (craftedAmount > 0) {
            questManager.recordProgress(player, island, QuestType.CRAFT, result.getType().name(), craftedAmount);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVillagerTrade(PlayerPurchaseEvent event) {
        Player player = event.getPlayer();
        Island island = resolvePlayerIsland(player);
        if (island == null) {
            return;
        }
        ItemStack traded = event.getTrade().getResult();
        if (!traded.getType().isAir() && traded.getAmount() > 0) {
            questManager.recordProgress(player, island, QuestType.VILLAGER_TRADE, traded.getType().name(), traded.getAmount());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBrew(BrewEvent event) {
        Island island = islandManager.getIslandAt(event.getBlock().getLocation()).orElse(null);
        if (island == null) {
            return;
        }

        BrewerInventory beforeInv = event.getContents();
        List<ItemStack> results = event.getResults();
        int newlyTierTwo = 0;

        int slots = Math.min(3, results.size());
        for (int i = 0; i < slots; i++) {
            ItemStack after = results.get(i);
            ItemStack before = beforeInv.getItem(i);
            if (isTierTwoPotion(after) && !isTierTwoPotion(before)) {
                newlyTierTwo++;
            }
        }

        if (newlyTierTwo > 0) {
            Player actor = findOnlineIslandMember(island);
            questManager.recordProgress(actor, island, QuestType.BREW, "POTION_TIER_2", newlyTierTwo);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();
        if (killer == null) {
            return;
        }
        Island island = resolvePlayerIsland(killer);
        if (island == null) {
            return;
        }

        questManager.recordProgress(killer, island, QuestType.ENTITY_KILL, entity.getType().name(), 1);

        int porkAmount = 0;
        for (ItemStack drop : event.getDrops()) {
            Material dropMat = drop.getType();
            if (dropMat == Material.PORKCHOP || dropMat == Material.COOKED_PORKCHOP) {
                porkAmount += drop.getAmount();
            }
        }
        if (porkAmount > 0) {
            questManager.recordProgress(killer, island, QuestType.COLLECT, "PORKCHOP", porkAmount);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
            return;
        }
        Player player = event.getPlayer();
        Island island = resolvePlayerIsland(player);
        if (island == null) {
            return;
        }

        Entity caught = event.getCaught();
        if (!(caught instanceof Item caughtItem)) {
            return;
        }
        ItemStack stack = caughtItem.getItemStack();
        Material mat = stack.getType();
        int amount = Math.max(1, stack.getAmount());

        if (isFishMaterial(mat)) {
            questManager.recordProgress(player, island, QuestType.FISH_CATCH, "ANY_FISH", amount);
            questManager.recordProgress(player, island, QuestType.FISH_CATCH, mat.name(), amount);
        } else if (isFishingTreasure(stack)) {
            questManager.recordProgress(player, island, QuestType.FISH_CATCH, "TREASURE", amount);
        }
    }

    private Island resolveBlockIsland(Player player, Location location) {
        Island island = islandManager.getIslandAt(location).orElse(null);
        if (island == null || island.getPlayerRole(player.getUniqueId()) == IslandRole.VISITOR) {
            return null;
        }
        return island;
    }

    private Island resolvePlayerIsland(Player player) {
        Optional<Island> atLocation = islandManager.getIslandAt(player.getLocation());
        if (atLocation.isPresent()) {
            Island island = atLocation.get();
            return island.getPlayerRole(player.getUniqueId()) != IslandRole.VISITOR ? island : null;
        }
        return islandManager.getPlayerIsland(player.getUniqueId()).orElse(null);
    }

    private static Player findOnlineIslandMember(Island island) {
        Player owner = Bukkit.getPlayer(island.getOwnerUuid());
        if (owner != null && owner.isOnline()) {
            return owner;
        }
        for (UUID memberId : island.getMembersSnapshot().keySet()) {
            Player member = Bukkit.getPlayer(memberId);
            if (member != null && member.isOnline()) {
                return member;
            }
        }
        return null;
    }

    private static int countConnectedVerticalBlocks(Block startBlock, Material expected) {
        int count = 1;
        int maxY = startBlock.getWorld().getMaxHeight();
        for (int dy = 1; dy < MAX_COLUMN_SCAN && startBlock.getY() + dy < maxY; dy++) {
            if (startBlock.getRelative(0, dy, 0).getType() == expected) {
                count++;
            } else {
                break;
            }
        }
        return count;
    }

    private static Material normalizeOreMaterial(Material type) {
        return switch (type) {
            case DEEPSLATE_COAL_ORE -> Material.COAL_ORE;
            case DEEPSLATE_IRON_ORE -> Material.IRON_ORE;
            case DEEPSLATE_GOLD_ORE, NETHER_GOLD_ORE -> Material.GOLD_ORE;
            case DEEPSLATE_DIAMOND_ORE -> Material.DIAMOND_ORE;
            case DEEPSLATE_EMERALD_ORE -> Material.EMERALD_ORE;
            default -> type;
        };
    }

    private static boolean isTrackedCropBlock(Material blockType) {
        return blockType == Material.WHEAT
                || blockType == Material.POTATOES
                || blockType == Material.CARROTS
                || blockType == Material.NETHER_WART;
    }

    private static boolean isTrackedCropDrop(Material dropType) {
        return dropType == Material.WHEAT
                || dropType == Material.POTATO
                || dropType == Material.POISONOUS_POTATO
                || dropType == Material.CARROT
                || dropType == Material.NETHER_WART;
    }

    private static int computeCraftedAmount(Player player, CraftItemEvent event, ItemStack result) {
        int batchAmount = Math.max(1, result.getAmount());
        if (event.isShiftClick()) {
            int maxCrafts = Integer.MAX_VALUE;
            for (ItemStack ingredient : event.getInventory().getMatrix()) {
                if (ingredient != null && !ingredient.getType().isAir()) {
                    maxCrafts = Math.min(maxCrafts, ingredient.getAmount());
                }
            }
            if (maxCrafts == Integer.MAX_VALUE || maxCrafts <= 0) {
                return 0;
            }
            int freeSpace = countInventorySpace(player.getInventory(), result);
            int affordableCrafts = freeSpace / batchAmount;
            return Math.min(maxCrafts, affordableCrafts) * batchAmount;
        }

        ClickType click = event.getClick();
        if (click == ClickType.DROP || click == ClickType.CONTROL_DROP) {
            return batchAmount;
        }
        if (click == ClickType.NUMBER_KEY) {
            int hotbarButton = event.getHotbarButton();
            ItemStack target = (hotbarButton >= 0 && hotbarButton < 9) ? player.getInventory().getItem(hotbarButton) : null;
            return (target == null || target.getType().isAir()) ? batchAmount : 0;
        }

        ItemStack cursor = event.getCursor();
        if (cursor.getType().isAir()) {
            return batchAmount;
        }
        if (cursor.isSimilar(result) && cursor.getAmount() + batchAmount <= result.getMaxStackSize()) {
            return batchAmount;
        }
        return 0;
    }

    private static int countInventorySpace(PlayerInventory inventory, ItemStack sample) {
        int maxStack = Math.max(1, sample.getMaxStackSize());
        int free = 0;
        for (ItemStack slotItem : inventory.getStorageContents()) {
            if (slotItem == null || slotItem.getType().isAir()) {
                free += maxStack;
            } else if (slotItem.isSimilar(sample)) {
                free += Math.max(0, maxStack - slotItem.getAmount());
            }
        }
        return free;
    }

    private static boolean isTierTwoPotion(ItemStack item) {
        if (item == null || item.getType().isAir() || !(item.getItemMeta() instanceof PotionMeta meta)) {
            return false;
        }
        if (meta.hasBasePotionType() && meta.getBasePotionType() != null
                && meta.getBasePotionType().name().startsWith("STRONG_")) {
            return true;
        }
        if (meta.hasCustomEffects()) {
            for (PotionEffect effect : meta.getCustomEffects()) {
                if (effect.getAmplifier() >= 1) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isFishMaterial(Material mat) {
        return mat == Material.COD
                || mat == Material.SALMON
                || mat == Material.TROPICAL_FISH
                || mat == Material.PUFFERFISH;
    }

    private static boolean isFishingTreasure(ItemStack stack) {
        return switch (stack.getType()) {
            case ENCHANTED_BOOK, NAME_TAG, NAUTILUS_SHELL, SADDLE -> true;
            case BOW, FISHING_ROD -> stack.hasItemMeta() && !stack.getEnchantments().isEmpty();
            default -> false;
        };
    }
}
