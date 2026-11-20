package pl.nauka.aradium.modules.skyblock.island;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import pl.nauka.aradium.modules.skyblock.quests.QuestProgress;
import pl.nauka.aradium.modules.skyblock.upgrades.IslandUpgradeType;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Model pojedynczej wyspy SkyBlock przechowujący granice, członków, role, tymczasowy Trust i warpy.
 */
public class Island {

    private final UUID islandId;
    private UUID ownerUuid;
    private final String worldName;
    private final int centerX;
    private final int centerZ;

    private double homeX;
    private double homeY;
    private double homeZ;
    private float homeYaw;
    private float homePitch;

    private boolean publicAccess;
    private BorderColor borderColor;
    private double islandLevel;
    private double bankBalance;
    private final long createdAt;

    private int borderRadius;
    private int maxMembers;
    private int spawnerLimit;
    private int cropGrowthBonusPercent = 0;
    private int cobbleGeneratorYield = 1;
    private int doubleCropDropChancePercent = 0;
    private int mobDropBonusPercent = 0;
    private int placedSpawnersCount = 0;

    private final Map<UUID, IslandRole> members = new ConcurrentHashMap<>();
    private final Set<UUID> trustedPlayers = ConcurrentHashMap.newKeySet();
    private final EnumMap<IslandRole, EnumMap<IslandPermission, Boolean>> rolePermissions = new EnumMap<>(IslandRole.class);
    private final EnumMap<IslandUpgradeType, Integer> upgradeTiers = new EnumMap<>(IslandUpgradeType.class);
    private final Map<String, IslandWarp> warps = new LinkedHashMap<>();
    private final Map<String, QuestProgress> quests = new ConcurrentHashMap<>();

    public Island(
            UUID islandId,
            UUID ownerUuid,
            String worldName,
            int centerX,
            int centerZ,
            double homeX,
            double homeY,
            double homeZ,
            float homeYaw,
            float homePitch,
            boolean publicAccess,
            BorderColor borderColor,
            double islandLevel,
            double bankBalance,
            long createdAt,
            int borderRadius,
            int maxMembers,
            int spawnerLimit
    ) {
        this.islandId = Objects.requireNonNull(islandId, "islandId");
        this.ownerUuid = Objects.requireNonNull(ownerUuid, "ownerUuid");
        this.worldName = Objects.requireNonNull(worldName, "worldName");
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.homeX = homeX;
        this.homeY = homeY;
        this.homeZ = homeZ;
        this.homeYaw = homeYaw;
        this.homePitch = homePitch;
        this.publicAccess = publicAccess;
        this.borderColor = borderColor != null ? borderColor : BorderColor.BLUE;
        this.islandLevel = Math.max(0.0, islandLevel);
        this.bankBalance = Math.max(0.0, bankBalance);
        this.createdAt = createdAt;
        this.borderRadius = Math.max(10, borderRadius);
        this.maxMembers = Math.max(1, maxMembers);
        this.spawnerLimit = Math.max(1, spawnerLimit);

        for (IslandRole role : IslandRole.configurableRoles()) {
            this.rolePermissions.put(role, IslandPermission.defaultPermissionsFor(role));
        }
        for (IslandUpgradeType upgradeType : IslandUpgradeType.values()) {
            this.upgradeTiers.put(upgradeType, 1);
        }
    }

    public UUID getIslandId() {
        return islandId;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public String getWorldName() {
        return worldName;
    }

    public int getCenterX() {
        return centerX;
    }

    public int getCenterZ() {
        return centerZ;
    }

    public double getHomeX() {
        return homeX;
    }

    public double getHomeY() {
        return homeY;
    }

    public double getHomeZ() {
        return homeZ;
    }

    public float getHomeYaw() {
        return homeYaw;
    }

    public float getHomePitch() {
        return homePitch;
    }

    public Location getHomeLocation() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        return new Location(world, homeX, homeY, homeZ, homeYaw, homePitch);
    }

    public void setHomeLocation(Location location) {
        if (location == null) {
            return;
        }
        this.homeX = location.getX();
        this.homeY = location.getY();
        this.homeZ = location.getZ();
        this.homeYaw = location.getYaw();
        this.homePitch = location.getPitch();
    }

    public boolean isPublicAccess() {
        return publicAccess;
    }

    public void setPublicAccess(boolean publicAccess) {
        this.publicAccess = publicAccess;
    }

    public BorderColor getBorderColor() {
        return borderColor;
    }

    public void setBorderColor(BorderColor borderColor) {
        this.borderColor = borderColor != null ? borderColor : BorderColor.BLUE;
    }

    public double getIslandLevel() {
        return islandLevel;
    }

    public void setIslandLevel(double islandLevel) {
        this.islandLevel = Math.max(0.0, islandLevel);
    }

    public double getBankBalance() {
        return bankBalance;
    }

    public void setBankBalance(double bankBalance) {
        this.bankBalance = Math.max(0.0, bankBalance);
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public int getBorderRadius() {
        return borderRadius;
    }

    public void setBorderRadius(int borderRadius) {
        this.borderRadius = Math.max(10, borderRadius);
    }

    public int getMaxMembers() {
        return maxMembers;
    }

    public void setMaxMembers(int maxMembers) {
        this.maxMembers = Math.max(1, maxMembers);
    }

    public int getSpawnerLimit() {
        return spawnerLimit;
    }

    public void setSpawnerLimit(int spawnerLimit) {
        this.spawnerLimit = Math.max(1, spawnerLimit);
    }

    public int getCropGrowthBonusPercent() {
        return cropGrowthBonusPercent;
    }

    public void setCropGrowthBonusPercent(int cropGrowthBonusPercent) {
        this.cropGrowthBonusPercent = Math.max(0, cropGrowthBonusPercent);
    }

    public int getCobbleGeneratorYield() {
        return cobbleGeneratorYield;
    }

    public void setCobbleGeneratorYield(int cobbleGeneratorYield) {
        this.cobbleGeneratorYield = Math.max(1, cobbleGeneratorYield);
    }

    public int getDoubleCropDropChancePercent() {
        return doubleCropDropChancePercent;
    }

    public void setDoubleCropDropChancePercent(int doubleCropDropChancePercent) {
        this.doubleCropDropChancePercent = Math.max(0, doubleCropDropChancePercent);
    }

    public int getMobDropBonusPercent() {
        return mobDropBonusPercent;
    }

    public void setMobDropBonusPercent(int mobDropBonusPercent) {
        this.mobDropBonusPercent = Math.max(0, mobDropBonusPercent);
    }

    public int getPlacedSpawnersCount() {
        return placedSpawnersCount;
    }

    public void setPlacedSpawnersCount(int placedSpawnersCount) {
        this.placedSpawnersCount = Math.max(0, placedSpawnersCount);
    }

    public int incrementPlacedSpawners(int delta) {
        this.placedSpawnersCount = Math.max(0, this.placedSpawnersCount + delta);
        return this.placedSpawnersCount;
    }

    public int getUpgradeTier(IslandUpgradeType type) {
        if (type == null) {
            return 1;
        }
        return upgradeTiers.getOrDefault(type, 1);
    }

    public void setUpgradeTier(IslandUpgradeType type, int tier) {
        if (type != null) {
            upgradeTiers.put(type, Math.max(1, tier));
        }
    }

    public Map<IslandUpgradeType, Integer> getUpgradeTiersSnapshot() {
        return Collections.unmodifiableMap(new EnumMap<>(upgradeTiers));
    }

    public IslandRole getPlayerRole(UUID playerId) {
        if (playerId == null) {
            return IslandRole.VISITOR;
        }
        if (ownerUuid.equals(playerId)) {
            return IslandRole.OWNER;
        }
        IslandRole memberRole = members.get(playerId);
        if (memberRole != null) {
            return memberRole;
        }
        if (trustedPlayers.contains(playerId)) {
            return IslandRole.TRUSTED;
        }
        return IslandRole.VISITOR;
    }

    public boolean isPermanentMember(UUID playerId) {
        return playerId != null && (ownerUuid.equals(playerId) || members.containsKey(playerId));
    }

    public int getPermanentMemberCount() {
        return 1 + members.size();
    }

    public Map<UUID, IslandRole> getMembersSnapshot() {
        return Collections.unmodifiableMap(members);
    }

    public void setMember(UUID playerId, IslandRole role) {
        if (playerId == null || ownerUuid.equals(playerId)) {
            return;
        }
        if (role != IslandRole.MEMBER && role != IslandRole.CO_OWNER) {
            role = IslandRole.MEMBER;
        }
        members.put(playerId, role);
        trustedPlayers.remove(playerId);
    }

    public boolean removeMember(UUID playerId) {
        if (playerId == null || ownerUuid.equals(playerId)) {
            return false;
        }
        return members.remove(playerId) != null;
    }

    public boolean transferOwnership(UUID newOwnerId) {
        if (newOwnerId == null || ownerUuid.equals(newOwnerId) || !members.containsKey(newOwnerId)) {
            return false;
        }
        UUID previousOwner = this.ownerUuid;
        this.members.remove(newOwnerId);
        this.trustedPlayers.remove(newOwnerId);
        this.members.put(previousOwner, IslandRole.CO_OWNER);
        this.ownerUuid = newOwnerId;
        return true;
    }

    public boolean addTrusted(UUID playerId) {
        if (playerId == null || isPermanentMember(playerId)) {
            return false;
        }
        return trustedPlayers.add(playerId);
    }

    public boolean removeTrusted(UUID playerId) {
        return playerId != null && trustedPlayers.remove(playerId);
    }

    public boolean isTrusted(UUID playerId) {
        return playerId != null && trustedPlayers.contains(playerId);
    }

    public Set<UUID> getTrustedPlayersSnapshot() {
        return Collections.unmodifiableSet(trustedPlayers);
    }

    public boolean hasPermission(UUID playerId, IslandPermission permission) {
        if (permission == null) {
            return false;
        }
        IslandRole role = getPlayerRole(playerId);
        if (role == IslandRole.OWNER) {
            return true;
        }
        if (role == IslandRole.VISITOR) {
            return false;
        }
        return getRolePermission(role, permission);
    }

    public boolean getRolePermission(IslandRole role, IslandPermission permission) {
        if (role == IslandRole.OWNER) {
            return true;
        }
        if (role == IslandRole.VISITOR || permission == null) {
            return false;
        }
        EnumMap<IslandPermission, Boolean> map = rolePermissions.get(role);
        return map != null && Boolean.TRUE.equals(map.get(permission));
    }

    public void setRolePermission(IslandRole role, IslandPermission permission, boolean allowed) {
        if (role == null || permission == null || role == IslandRole.OWNER || role == IslandRole.VISITOR) {
            return;
        }
        rolePermissions.computeIfAbsent(role, IslandPermission::defaultPermissionsFor).put(permission, allowed);
    }

    public boolean toggleRolePermission(IslandRole role, IslandPermission permission) {
        boolean next = !getRolePermission(role, permission);
        setRolePermission(role, permission, next);
        return next;
    }

    public Map<IslandRole, Map<IslandPermission, Boolean>> getRolePermissionsSnapshot() {
        EnumMap<IslandRole, Map<IslandPermission, Boolean>> copy = new EnumMap<>(IslandRole.class);
        for (Map.Entry<IslandRole, EnumMap<IslandPermission, Boolean>> entry : rolePermissions.entrySet()) {
            copy.put(entry.getKey(), Collections.unmodifiableMap(new EnumMap<>(entry.getValue())));
        }
        return Collections.unmodifiableMap(copy);
    }

    public boolean canVisit(UUID playerId) {
        return publicAccess || getPlayerRole(playerId) != IslandRole.VISITOR;
    }

    public boolean containsLocation(String world, int blockX, int blockZ) {
        return worldName.equals(world)
                && Math.abs(blockX - centerX) <= borderRadius
                && Math.abs(blockZ - centerZ) <= borderRadius;
    }

    public boolean containsLocation(Location location) {
        return location != null
                && location.getWorld() != null
                && containsLocation(location.getWorld().getName(), location.getBlockX(), location.getBlockZ());
    }

    public synchronized void putWarp(IslandWarp warp) {
        if (warp != null) {
            warps.put(warp.name().toLowerCase(Locale.ROOT), warp);
        }
    }

    public synchronized IslandWarp removeWarp(String warpName) {
        if (warpName == null) {
            return null;
        }
        return warps.remove(warpName.toLowerCase(Locale.ROOT));
    }

    public synchronized IslandWarp getWarp(String warpName) {
        if (warpName == null) {
            return null;
        }
        return warps.get(warpName.toLowerCase(Locale.ROOT));
    }

    public synchronized Map<String, IslandWarp> getWarpsSnapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(warps));
    }

    public QuestProgress getQuestProgress(String questId) {
        if (questId == null) {
            return null;
        }
        return quests.get(questId);
    }

    public QuestProgress getOrCreateQuestProgress(String questId, int stageNumber) {
        Objects.requireNonNull(questId, "questId");
        return quests.computeIfAbsent(questId, id -> new QuestProgress(id, stageNumber));
    }

    public void putQuestProgress(QuestProgress progress) {
        if (progress != null) {
            quests.put(progress.getQuestId(), progress);
        }
    }

    public Map<String, QuestProgress> getQuestsSnapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(quests));
    }
}
