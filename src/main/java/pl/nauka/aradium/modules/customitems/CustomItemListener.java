package pl.nauka.aradium.modules.customitems;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.Vector;
import pl.nauka.aradium.AradiumPlugin;
import pl.nauka.aradium.core.PluginModule;
import pl.nauka.aradium.modules.armor.ArmorModule;
import pl.nauka.aradium.modules.skyblock.SkyBlockModule;
import pl.nauka.aradium.modules.skyblock.island.Island;
import pl.nauka.aradium.modules.skyblock.island.IslandPermission;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Listener obsługujący stawiane generatory, pochodnię anty-mob,
 * GUI oraz walidację Custom Recipes, nakładanie zwojów ulepszeń
 * i mechaniki (kopanie 2x2/3x3, Hutnik, Drwal, Żniwiarz 3x3, Wampiryzm, Piorun, Double Jump).
 */
public class CustomItemListener implements Listener {

    private static final long THUNDER_COOLDOWN_MS = 800L;
    private static final int MAX_AREA_BLOCKS_HARD_LIMIT = 24;

    private final AradiumPlugin plugin;
    private final CustomItemModule module;

    private final Map<UUID, BlockFace> lastHitFaces = new HashMap<>();
    private final Set<UUID> activeMultiBreakPlayers = new HashSet<>();
    private final Map<UUID, Long> thunderCooldowns = new HashMap<>();

    private final Set<UUID> doubleJumpGrantedPlayers = new HashSet<>();
    private final Set<UUID> doubleJumpUsedInAir = new HashSet<>();
    private final Set<UUID> doubleJumpFallProtected = new HashSet<>();

    public CustomItemListener(AradiumPlugin plugin, CustomItemModule module) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.module = Objects.requireNonNull(module, "module");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCustomBlockPlace(BlockPlaceEvent event) {
        if (!module.isEnabled()) {
            return;
        }
        ItemStack handItem = event.getItemInHand();
        Block blockPlaced = event.getBlockPlaced();

        String generatorId = module.getGeneratorId(handItem);
        if (generatorId != null) {
            GeneratorDefinition gen = module.getGenerator(generatorId);
            if (gen != null && blockPlaced.getType() == gen.material()) {
                module.registerPlacedGenerator(blockPlaced, generatorId);
            }
            return;
        }

        Integer torchRadius = module.getAntiMobTorchRadius(handItem);
        AntiMobTorchDefinition torchDef = module.getAntiMobTorch();
        if (torchRadius != null && torchDef != null && torchDef.isPlacedTorchMaterial(blockPlaced.getType())) {
            module.registerPlacedAntiMobTorch(blockPlaced, torchRadius);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCustomBlockBreak(BlockBreakEvent event) {
        if (!module.isEnabled()) {
            return;
        }
        Block block = event.getBlock();
        Player player = event.getPlayer();

        String genId = module.getPlacedGeneratorAt(block);
        if (genId != null) {
            module.unregisterPlacedGenerator(block);
            event.setDropItems(false);
            if (player.getGameMode() != GameMode.CREATIVE) {
                CustomItemModule.dropItemAtBlock(block, module.createGeneratorItem(genId, 1));
            }
            return;
        }

        if (module.hasPlacedAntiMobTorchAt(block)) {
            module.unregisterPlacedAntiMobTorch(block);
            event.setDropItems(false);
            if (player.getGameMode() != GameMode.CREATIVE) {
                CustomItemModule.dropItemAtBlock(block, module.createAntiMobTorchItem(1));
            }
            return;
        }

        CustomItemModule.BlockPos belowPos = CustomItemModule.BlockPos.fromBlock(block).below();
        if (module.getPlacedGeneratorAtPos(belowPos) != null) {
            module.scheduleGeneratorBlockSpawn(belowPos);
        }

        UUID playerId = player.getUniqueId();
        if (activeMultiBreakPlayers.contains(playerId)) {
            return;
        }

        ItemStack tool = player.getInventory().getItemInMainHand();
        Set<UpgradeScrollDefinition.CustomUpgradeAbility> abilities = module.getAppliedUpgrades(tool);
        if (abilities.isEmpty()) {
            return;
        }

        if (abilities.contains(UpgradeScrollDefinition.CustomUpgradeAbility.HARVESTER_3X3)
                && block.getBlockData() instanceof Ageable ageable
                && ageable.getAge() >= ageable.getMaximumAge()) {
            event.setCancelled(true);
            harvestAreaCrops3x3(player, tool, block);
            return;
        }

        if (abilities.contains(UpgradeScrollDefinition.CustomUpgradeAbility.TREE_CAPITATOR)
                && isLogMaterial(block.getType())) {
            int maxLogs = (int) Math.max(1, Math.min(MAX_AREA_BLOCKS_HARD_LIMIT,
                    module.getAbilityValue(UpgradeScrollDefinition.CustomUpgradeAbility.TREE_CAPITATOR, 24.0)));
            breakConnectedTreeLogs(player, block, maxLogs);
            return;
        }

        boolean has3x3 = abilities.contains(UpgradeScrollDefinition.CustomUpgradeAbility.MINING_3X3);
        boolean has2x2 = !has3x3 && abilities.contains(UpgradeScrollDefinition.CustomUpgradeAbility.MINING_2X2);
        if ((has3x3 || has2x2) && player.getGameMode() != GameMode.CREATIVE) {
            performAreaMining(player, block, tool, has3x3);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onAutoSmeltDrop(BlockDropItemEvent event) {
        if (!module.isEnabled()) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (tool.getEnchantmentLevel(Enchantment.SILK_TOUCH) > 0) {
            return;
        }
        if (!module.hasUpgrade(tool, UpgradeScrollDefinition.CustomUpgradeAbility.AUTO_SMELT)) {
            return;
        }
        for (Item itemEntity : event.getItems()) {
            ItemStack stack = itemEntity.getItemStack();
            if (stack.hasItemMeta()) {
                continue;
            }
            Material smelted = CustomItemModule.smeltDropMaterial(stack.getType());
            if (smelted != null) {
                itemEntity.setItemStack(new ItemStack(smelted, stack.getAmount()));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onWeaponCombatAbilities(EntityDamageByEntityEvent event) {
        if (!module.isEnabled() || !(event.getDamager() instanceof Player attacker)) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity target) || target instanceof ArmorStand) {
            return;
        }
        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        Set<UpgradeScrollDefinition.CustomUpgradeAbility> abilities = module.getAppliedUpgrades(weapon);
        if (abilities.isEmpty()) {
            return;
        }

        if (abilities.contains(UpgradeScrollDefinition.CustomUpgradeAbility.THUNDER_STRIKE)) {
            double chance = module.getAbilityValue(UpgradeScrollDefinition.CustomUpgradeAbility.THUNDER_STRIKE, 0.25);
            long now = System.currentTimeMillis();
            long prev = thunderCooldowns.getOrDefault(attacker.getUniqueId(), 0L);
            if (now - prev >= THUNDER_COOLDOWN_MS && ThreadLocalRandom.current().nextDouble() < chance) {
                thunderCooldowns.put(attacker.getUniqueId(), now);
                target.getWorld().strikeLightningEffect(target.getLocation());
                event.setDamage(event.getDamage() + 4.0);
            }
        }

        if (abilities.contains(UpgradeScrollDefinition.CustomUpgradeAbility.VAMPIRISM)) {
            double ratio = module.getAbilityValue(UpgradeScrollDefinition.CustomUpgradeAbility.VAMPIRISM, 1.0 / 3.0);
            AttributeInstance maxHpAttr = attacker.getAttribute(Attribute.MAX_HEALTH);
            double maxHp = maxHpAttr != null ? maxHpAttr.getValue() : 20.0;
            double nextHealth = CustomItemModule.computeVampirismHeal(
                    event.getFinalDamage(), ratio, attacker.getHealth(), maxHp
            );
            if (nextHealth > attacker.getHealth()) {
                attacker.setHealth(nextHealth);
                attacker.getWorld().spawnParticle(Particle.HEART, attacker.getLocation().add(0, 1.8, 0), 3, 0.2, 0.2, 0.2);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (module.isEnabled() && containsProtectedBlock(event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (module.isEnabled() && containsProtectedBlock(event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (module.isEnabled()) {
            removeProtectedBlocksFromExplosion(event.blockList());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (module.isEnabled()) {
            removeProtectedBlocksFromExplosion(event.blockList());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!module.isEnabled() || event.getLocation().getWorld() == null) {
            return;
        }
        var loc = event.getLocation();
        if (module.shouldBlockMobSpawn(
                CustomItemModule.isHostileEntity(event.getEntity()),
                event.getSpawnReason(),
                loc.getWorld().getName(),
                loc.getBlockX(),
                loc.getBlockY(),
                loc.getBlockZ()
        )) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareCustomCraft(PrepareItemCraftEvent event) {
        if (!module.isEnabled() || module.getRecipeManager() == null || event.getRecipe() == null) {
            return;
        }
        CustomRecipeManager manager = module.getRecipeManager();
        CustomRecipeDefinition def = manager.findManagedRecipe(event.getRecipe()).orElse(null);
        if (def == null) {
            return;
        }
        if (!manager.validateCraftMatrix(def, event.getInventory().getMatrix())) {
            event.getInventory().setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof CustomRecipeGuiHolder holder) {
            if (event.getWhoClicked() instanceof Player player && module.getRecipeGUI() != null) {
                module.getRecipeGUI().handleInventoryClick(event, player, holder);
            } else {
                event.setCancelled(true);
            }
            return;
        }

        if (!module.isEnabled() || event.isCancelled() || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        plugin.getServer().getScheduler().runTask(plugin, () -> syncDoubleJumpState(player));

        if (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT) {
            return;
        }
        if (!(event.getClickedInventory() instanceof PlayerInventory)) {
            return;
        }

        ItemStack cursor = event.getView().getCursor();
        ItemStack target = event.getCurrentItem();
        if (cursor == null || cursor.getType().isAir() || target == null || target.getType().isAir()) {
            return;
        }
        if (module.getScrollId(cursor) == null) {
            return;
        }

        event.setCancelled(true);
        CustomItemModule.ScrollApplyOutcome outcome = module.applyUpgradeScroll(cursor, target);
        if (!outcome.applied()) {
            if (outcome.message() != null) {
                player.sendMessage(outcome.message());
            }
            return;
        }

        event.setCurrentItem(outcome.updatedTarget());
        consumeOneCursorItem(event, cursor);
        if (outcome.message() != null) {
            player.sendMessage(outcome.message());
        }
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.7f, 1.25f);
        syncArmorAndDoubleJumpLater(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof CustomRecipeGuiHolder holder && module.getRecipeGUI() != null) {
            module.getRecipeGUI().handleInventoryDrag(event, holder);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof CustomRecipeGuiHolder holder
                && event.getPlayer() instanceof Player player
                && module.getRecipeGUI() != null) {
            module.getRecipeGUI().handleEditorClose(player, holder, top);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!module.isEnabled()) {
            return;
        }
        Player player = event.getPlayer();
        if (event.getAction() == Action.LEFT_CLICK_BLOCK && event.getBlockFace() != BlockFace.SELF) {
            lastHitFaces.put(player.getUniqueId(), event.getBlockFace());
            return;
        }

        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        PlayerInventory inventory = player.getInventory();
        ItemStack mainHand = inventory.getItemInMainHand();
        if (action == Action.RIGHT_CLICK_BLOCK
                && event.getClickedBlock() != null
                && !player.isSneaking()
                && module.hasUpgrade(mainHand, UpgradeScrollDefinition.CustomUpgradeAbility.HARVESTER_3X3)
                && event.getClickedBlock().getBlockData() instanceof Ageable ageable
                && ageable.getAge() >= ageable.getMaximumAge()) {
            event.setCancelled(true);
            harvestAreaCrops3x3(player, mainHand, event.getClickedBlock());
            return;
        }

        if (!player.isSneaking()) {
            return;
        }
        ItemStack offHand = inventory.getItemInOffHand();
        if (offHand.getType().isAir() || module.getScrollId(offHand) == null || mainHand.getType().isAir()) {
            return;
        }

        event.setCancelled(true);
        CustomItemModule.ScrollApplyOutcome outcome = module.applyUpgradeScroll(offHand, mainHand);
        if (!outcome.applied()) {
            if (outcome.message() != null) {
                player.sendMessage(outcome.message());
            }
            return;
        }

        inventory.setItemInMainHand(outcome.updatedTarget());
        if (offHand.getAmount() <= 1) {
            inventory.setItemInOffHand(null);
        } else {
            offHand.setAmount(offHand.getAmount() - 1);
            inventory.setItemInOffHand(offHand);
        }

        if (outcome.message() != null) {
            player.sendMessage(outcome.message());
        }
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.7f, 1.25f);
        syncArmorAndDoubleJumpLater(player);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerToggleFlight(PlayerToggleFlightEvent event) {
        if (!module.isEnabled()) {
            return;
        }
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        boolean hasFlightBoots = hasArmorFlightBoots(uuid);
        boolean hasDoubleJumpBoots = module.hasDoubleJumpBootsEquipped(player);
        boolean usedInAir = doubleJumpUsedInAir.contains(uuid);

        if (!CustomItemModule.shouldAllowDoubleJump(player.getGameMode(), hasFlightBoots, hasDoubleJumpBoots, usedInAir)) {
            return;
        }

        event.setCancelled(true);
        player.setFlying(false);
        player.setAllowFlight(false);
        doubleJumpUsedInAir.add(uuid);
        doubleJumpFallProtected.add(uuid);

        Vector forward = player.getLocation().getDirection().setY(0.0);
        if (forward.lengthSquared() > 1.0e-4) {
            forward.normalize().multiply(0.55);
        } else {
            forward = new Vector(0.0, 0.0, 0.0);
        }
        forward.setY(0.78);
        player.setVelocity(forward);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BAT_TAKEOFF, 0.8f, 1.2f);
        player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 10, 0.25, 0.05, 0.25, 0.05);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!module.isEnabled()) {
            return;
        }
        Player player = event.getPlayer();
        if (((Entity) player).isOnGround()) {
            doubleJumpUsedInAir.remove(player.getUniqueId());
            syncDoubleJumpState(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerFallDamage(EntityDamageEvent event) {
        if (module.isEnabled()
                && event.getEntity() instanceof Player player
                && event.getCause() == EntityDamageEvent.DamageCause.FALL
                && doubleJumpFallProtected.remove(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (module.isEnabled()) {
            plugin.getServer().getScheduler().runTask(plugin, () -> syncDoubleJumpState(event.getPlayer()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        if (module.isEnabled()) {
            plugin.getServer().getScheduler().runTask(plugin, () -> syncDoubleJumpState(event.getPlayer()));
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        lastHitFaces.remove(uuid);
        activeMultiBreakPlayers.remove(uuid);
        thunderCooldowns.remove(uuid);
        doubleJumpUsedInAir.remove(uuid);
        doubleJumpFallProtected.remove(uuid);
        clearDoubleJumpOnDisable(event.getPlayer());
    }

    public void syncDoubleJumpState(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        UUID uuid = player.getUniqueId();
        if (player.getGameMode() == GameMode.CREATIVE
                || player.getGameMode() == GameMode.SPECTATOR
                || hasArmorFlightBoots(uuid)) {
            doubleJumpGrantedPlayers.remove(uuid);
            return;
        }

        boolean equipped = module.hasDoubleJumpBootsEquipped(player);
        if (equipped) {
            doubleJumpGrantedPlayers.add(uuid);
            if (!doubleJumpUsedInAir.contains(uuid) && !player.getAllowFlight()) {
                player.setAllowFlight(true);
            }
        } else if (doubleJumpGrantedPlayers.remove(uuid)) {
            if (player.isFlying()) {
                player.setFlying(false);
            }
            if (player.getAllowFlight()) {
                player.setAllowFlight(false);
            }
        }
    }

    public void clearDoubleJumpOnDisable(Player player) {
        if (player == null) {
            return;
        }
        UUID uuid = player.getUniqueId();
        if (doubleJumpGrantedPlayers.remove(uuid)) {
            if (player.getGameMode() != GameMode.CREATIVE
                    && player.getGameMode() != GameMode.SPECTATOR
                    && !hasArmorFlightBoots(uuid)) {
                if (player.isFlying()) {
                    player.setFlying(false);
                }
                if (player.getAllowFlight()) {
                    player.setAllowFlight(false);
                }
            }
        }
    }

    private void performAreaMining(Player player, Block originBlock, ItemStack tool, boolean is3x3) {
        BlockFace hitFace = lastHitFaces.getOrDefault(player.getUniqueId(), BlockFace.NORTH);
        BlockFace facing = player.getFacing();
        int signU = (facing == BlockFace.WEST || facing == BlockFace.NORTH) ? -1 : 1;
        int signV = player.getLocation().getPitch() < -30.0f ? 1 : -1;
        List<int[]> offsets = CustomItemModule.computeAreaMiningOffsets(hitFace, is3x3, signU, signV);

        UUID uuid = player.getUniqueId();
        activeMultiBreakPlayers.add(uuid);
        try {
            for (int[] off : offsets) {
                if (off[0] == 0 && off[1] == 0 && off[2] == 0) {
                    continue;
                }
                Block neighbor = originBlock.getRelative(off[0], off[1], off[2]);
                if (!canMultiBreakBlock(player, neighbor)) {
                    continue;
                }
                if (neighbor.getDrops(tool, player).isEmpty()) {
                    continue;
                }
                player.breakBlock(neighbor);
            }
        } finally {
            activeMultiBreakPlayers.remove(uuid);
        }
    }

    private void breakConnectedTreeLogs(Player player, Block origin, int maxLogs) {
        UUID uuid = player.getUniqueId();
        activeMultiBreakPlayers.add(uuid);
        try {
            Set<Block> visited = new HashSet<>();
            ArrayDeque<Block> queue = new ArrayDeque<>();
            visited.add(origin);
            queue.add(origin);
            int brokenCount = 0;

            while (!queue.isEmpty() && brokenCount < maxLogs) {
                Block current = queue.poll();
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = 0; dy <= 1; dy++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            if (dx == 0 && dy == 0 && dz == 0) {
                                continue;
                            }
                            Block next = current.getRelative(dx, dy, dz);
                            if (visited.add(next) && isLogMaterial(next.getType()) && canModifyBlock(player, next, true)) {
                                queue.add(next);
                            }
                        }
                    }
                }
                if (!current.equals(origin) && !module.isProtectedCustomBlock(current)) {
                    player.breakBlock(current);
                    brokenCount++;
                }
            }
        } finally {
            activeMultiBreakPlayers.remove(uuid);
        }
    }

    private void harvestAreaCrops3x3(Player player, ItemStack hoe, Block centerBlock) {
        UUID uuid = player.getUniqueId();
        activeMultiBreakPlayers.add(uuid);
        try {
            boolean harvestedAny = false;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    Block candidate = centerBlock.getRelative(dx, 0, dz);
                    if (!(candidate.getBlockData() instanceof Ageable ageable)
                            || ageable.getAge() < ageable.getMaximumAge()) {
                        continue;
                    }
                    if (!canModifyBlock(player, candidate, true) || !canModifyBlock(player, candidate, false)) {
                        continue;
                    }
                    Material seedMaterial = CustomItemModule.resolveCropSeedMaterial(candidate.getType());
                    Collection<ItemStack> drops = candidate.getDrops(hoe, player);
                    boolean subtractedSeed = false;
                    for (ItemStack drop : drops) {
                        if (drop == null || drop.getType().isAir() || drop.getAmount() <= 0) {
                            continue;
                        }
                        if (!subtractedSeed && seedMaterial != null && drop.getType() == seedMaterial) {
                            int remaining = drop.getAmount() - 1;
                            subtractedSeed = true;
                            if (remaining <= 0) {
                                continue;
                            }
                            drop = drop.clone();
                            drop.setAmount(remaining);
                        }
                        candidate.getWorld().dropItemNaturally(candidate.getLocation().add(0.5, 0.3, 0.5), drop);
                    }
                    ageable.setAge(0);
                    candidate.setBlockData(ageable);
                    harvestedAny = true;
                }
            }
            if (harvestedAny) {
                player.playSound(centerBlock.getLocation(), Sound.ITEM_CROP_PLANT, 0.7f, 1.1f);
            }
        } finally {
            activeMultiBreakPlayers.remove(uuid);
        }
    }

    private boolean canMultiBreakBlock(Player player, Block block) {
        Material mat = block.getType();
        if (mat.isAir()
                || block.isLiquid()
                || mat == Material.BEDROCK
                || mat == Material.BARRIER
                || mat == Material.END_PORTAL_FRAME
                || mat == Material.END_PORTAL
                || mat == Material.NETHER_PORTAL
                || mat == Material.SPAWNER
                || module.isProtectedCustomBlock(block)) {
            return false;
        }
        return canModifyBlock(player, block, true);
    }

    private boolean canModifyBlock(Player player, Block block, boolean checkBreak) {
        if (player.hasPermission("aradium.admin")) {
            return true;
        }
        SkyBlockModule skyBlock = plugin.getSkyBlockModule().filter(PluginModule::isEnabled).orElse(null);
        if (skyBlock == null || skyBlock.getIslandManager() == null) {
            return true;
        }
        if (!block.getWorld().getName().equals(skyBlock.getIslandManager().getWorldName())) {
            return true;
        }
        Island island = skyBlock.getIslandManager().getIslandAt(block.getLocation()).orElse(null);
        if (island == null) {
            return false;
        }
        IslandPermission perm = checkBreak ? IslandPermission.BREAK : IslandPermission.PLACE;
        return island.hasPermission(player.getUniqueId(), perm);
    }

    private boolean hasArmorFlightBoots(UUID uuid) {
        return plugin.getArmorModule()
                .filter(PluginModule::isEnabled)
                .map(armor -> armor.hasActiveFlightBoots(uuid))
                .orElse(false);
    }

    private void syncArmorAndDoubleJumpLater(Player player) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            plugin.getArmorModule()
                    .filter(PluginModule::isEnabled)
                    .ifPresent(armorModule -> armorModule.evaluatePlayerArmor(player));
            syncDoubleJumpState(player);
        });
    }

    private boolean containsProtectedBlock(List<Block> blocks) {
        for (Block block : blocks) {
            if (module.isProtectedCustomBlock(block)) {
                return true;
            }
        }
        return false;
    }

    private void removeProtectedBlocksFromExplosion(List<Block> blockList) {
        Iterator<Block> it = blockList.iterator();
        while (it.hasNext()) {
            if (module.isProtectedCustomBlock(it.next())) {
                it.remove();
            }
        }
    }

    private static boolean isLogMaterial(Material mat) {
        if (mat == null) {
            return false;
        }
        String name = mat.name();
        return name.endsWith("_LOG") || name.endsWith("_STEM") || name.endsWith("_WOOD");
    }

    private static void consumeOneCursorItem(InventoryClickEvent event, ItemStack cursor) {
        if (cursor.getAmount() <= 1) {
            event.getView().setCursor(null);
        } else {
            ItemStack updated = cursor.clone();
            updated.setAmount(cursor.getAmount() - 1);
            event.getView().setCursor(updated);
        }
    }
}
