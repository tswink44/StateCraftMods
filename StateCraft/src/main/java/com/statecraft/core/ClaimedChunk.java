package com.statecraft.core;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.*;

/**
 * Represents a claimed chunk in the world
 * This is the lowest level of the hierarchy
 */
public class ClaimedChunk {
    private final UUID id;
    private final ChunkPos chunkPos;
    private final ResourceKey<Level> dimension;
    private UUID cityId;

    // Ownership
    private OwnershipType ownershipType;
    private UUID playerOwner; // Only set if ownershipType is PLAYER

    // Sale information
    private boolean forSale;
    private double salePrice;
    private UUID sellerId; // Who listed it for sale

    // Permissions per role
    private final Map<PermissionLevel, Set<Permission>> rolePermissions;
    // Specific player overrides
    private final Map<UUID, Set<Permission>> playerPermissions;

    public ClaimedChunk(ChunkPos chunkPos, ResourceKey<Level> dimension, UUID cityId) {
        this.id = UUID.randomUUID();
        this.chunkPos = chunkPos;
        this.dimension = dimension;
        this.cityId = cityId;
        this.ownershipType = OwnershipType.HIERARCHY;
        this.rolePermissions = new EnumMap<>(PermissionLevel.class);
        this.playerPermissions = new HashMap<>();
        initializeDefaultPermissions();
    }

    private ClaimedChunk(UUID id, ChunkPos chunkPos, ResourceKey<Level> dimension, UUID cityId) {
        this.id = id;
        this.chunkPos = chunkPos;
        this.dimension = dimension;
        this.cityId = cityId;
        this.ownershipType = OwnershipType.HIERARCHY;
        this.rolePermissions = new EnumMap<>(PermissionLevel.class);
        this.playerPermissions = new HashMap<>();
        initializeDefaultPermissions();
    }

    private void initializeDefaultPermissions() {
        // Owners and Admins get all permissions
        rolePermissions.put(PermissionLevel.OWNER, EnumSet.allOf(Permission.class));
        rolePermissions.put(PermissionLevel.ADMIN, EnumSet.allOf(Permission.class));

        // Members can build, break, interact, and access containers
        rolePermissions.put(PermissionLevel.MEMBER, EnumSet.of(
            Permission.BUILD, Permission.BREAK, Permission.INTERACT, Permission.CONTAINER
        ));

        // Allies can interact and access containers
        rolePermissions.put(PermissionLevel.ALLY, EnumSet.of(
            Permission.INTERACT, Permission.CONTAINER
        ));

        // Outsiders have no permissions by default
        rolePermissions.put(PermissionLevel.OUTSIDER, EnumSet.noneOf(Permission.class));
    }

    public ChunkPos getChunkPos() {
        return chunkPos;
    }

    public UUID getId() {
        return id;
    }

    public ResourceKey<Level> getDimension() {
        return dimension;
    }

    public UUID getCityId() {
        return cityId;
    }

    public void setCityId(UUID cityId) {
        this.cityId = cityId;
    }

    public OwnershipType getOwnershipType() {
        return ownershipType;
    }

    public void setOwnershipType(OwnershipType ownershipType) {
        this.ownershipType = ownershipType;
    }

    @Nullable
    public UUID getPlayerOwner() {
        return playerOwner;
    }

    public void setPlayerOwner(@Nullable UUID playerOwner) {
        this.playerOwner = playerOwner;
        this.ownershipType = playerOwner != null ? OwnershipType.PLAYER : OwnershipType.HIERARCHY;
    }

    /**
     * Alias for setPlayerOwner
     */
    public void setOwnerId(@Nullable UUID ownerId) {
        setPlayerOwner(ownerId);
    }

    // ==================== Sale Methods ====================

    public boolean isForSale() {
        return forSale;
    }

    public void setForSale(boolean forSale) {
        this.forSale = forSale;
        if (!forSale) {
            this.salePrice = 0;
            this.sellerId = null;
        }
    }

    public double getSalePrice() {
        return salePrice;
    }

    public void setSalePrice(double salePrice) {
        this.salePrice = salePrice;
    }

    @Nullable
    public UUID getSellerId() {
        return sellerId;
    }

    public void setSellerId(@Nullable UUID sellerId) {
        this.sellerId = sellerId;
    }

    /**
     * List this chunk for sale
     */
    public void listForSale(double price, UUID seller) {
        this.forSale = true;
        this.salePrice = price;
        this.sellerId = seller;
    }

    /**
     * Remove this chunk from sale
     */
    public void removeFromSale() {
        this.forSale = false;
        this.salePrice = 0;
        this.sellerId = null;
    }

    public boolean hasPermission(UUID playerId, Permission permission, PermissionLevel roleLevel) {
        // Check if player is the chunk owner — owner always has all permissions
        if (ownershipType == OwnershipType.PLAYER && playerId.equals(playerOwner)) {
            return true;
        }

        // Check player-specific overrides (building permits)
        Set<Permission> playerPerms = playerPermissions.get(playerId);
        if (playerPerms != null && playerPerms.contains(permission)) {
            return true;
        }

        // For privately owned chunks, non-owners without permits are treated as OUTSIDER
        // Only the owner and explicit permit holders get access
        if (ownershipType == OwnershipType.PLAYER) {
            Set<Permission> outsiderPerms = rolePermissions.get(PermissionLevel.OUTSIDER);
            return outsiderPerms != null && outsiderPerms.contains(permission);
        }

        // Government-owned chunks: check role-based permissions as normal
        Set<Permission> rolePerms = rolePermissions.get(roleLevel);
        return rolePerms != null && rolePerms.contains(permission);
    }

    public void setRolePermission(PermissionLevel level, Permission permission, boolean granted) {
        Set<Permission> perms = rolePermissions.computeIfAbsent(level, k -> EnumSet.noneOf(Permission.class));
        if (granted) {
            perms.add(permission);
        } else {
            perms.remove(permission);
        }
    }

    public void setPlayerPermission(UUID playerId, Permission permission, boolean granted) {
        Set<Permission> perms = playerPermissions.computeIfAbsent(playerId, k -> EnumSet.noneOf(Permission.class));
        if (granted) {
            perms.add(permission);
        } else {
            perms.remove(permission);
        }
    }

    public Set<Permission> getRolePermissions(PermissionLevel level) {
        return Collections.unmodifiableSet(rolePermissions.getOrDefault(level, EnumSet.noneOf(Permission.class)));
    }

    /**
     * Get all players with explicit permissions on this chunk (permits)
     */
    public Map<UUID, Set<Permission>> getPlayerPermissions() {
        return Collections.unmodifiableMap(playerPermissions);
    }

    /**
     * Get the set of players who have been granted permits (any permissions) on this chunk
     */
    public Set<UUID> getPermitHolders() {
        return Collections.unmodifiableSet(playerPermissions.keySet());
    }

    /**
     * Grant a building permit to a player (gives BUILD and BREAK permissions)
     */
    public void grantBuildingPermit(UUID playerId) {
        Set<Permission> perms = playerPermissions.computeIfAbsent(playerId, k -> EnumSet.noneOf(Permission.class));
        perms.add(Permission.BUILD);
        perms.add(Permission.BREAK);
    }

    /**
     * Revoke a building permit from a player
     */
    public void revokeBuildingPermit(UUID playerId) {
        Set<Permission> perms = playerPermissions.get(playerId);
        if (perms != null) {
            perms.remove(Permission.BUILD);
            perms.remove(Permission.BREAK);
            // Remove the player entry if they have no permissions left
            if (perms.isEmpty()) {
                playerPermissions.remove(playerId);
            }
        }
    }

    /**
     * Check if a player has a building permit
     */
    public boolean hasBuildingPermit(UUID playerId) {
        Set<Permission> perms = playerPermissions.get(playerId);
        return perms != null && perms.contains(Permission.BUILD) && perms.contains(Permission.BREAK);
    }

    /**
     * Revoke all permits from a player
     */
    public void revokeAllPermits(UUID playerId) {
        playerPermissions.remove(playerId);
    }

    // NBT Serialization
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.putInt("chunkX", chunkPos.x);
        tag.putInt("chunkZ", chunkPos.z);
        tag.putString("dimension", dimension.location().toString());
        tag.putUUID("cityId", cityId);
        tag.putString("ownershipType", ownershipType.name());

        if (playerOwner != null) {
            tag.putUUID("playerOwner", playerOwner);
        }

        // Save sale information
        tag.putBoolean("forSale", forSale);
        if (forSale) {
            tag.putDouble("salePrice", salePrice);
            if (sellerId != null) {
                tag.putUUID("sellerId", sellerId);
            }
        }

        // Save role permissions
        CompoundTag rolePermsTag = new CompoundTag();
        for (Map.Entry<PermissionLevel, Set<Permission>> entry : rolePermissions.entrySet()) {
            ListTag permList = new ListTag();
            for (Permission perm : entry.getValue()) {
                permList.add(StringTag.valueOf(perm.name()));
            }
            rolePermsTag.put(entry.getKey().name(), permList);
        }
        tag.put("rolePermissions", rolePermsTag);

        // Save player permissions
        CompoundTag playerPermsTag = new CompoundTag();
        for (Map.Entry<UUID, Set<Permission>> entry : playerPermissions.entrySet()) {
            ListTag permList = new ListTag();
            for (Permission perm : entry.getValue()) {
                permList.add(StringTag.valueOf(perm.name()));
            }
            playerPermsTag.put(entry.getKey().toString(), permList);
        }
        tag.put("playerPermissions", playerPermsTag);

        return tag;
    }

    public static ClaimedChunk load(CompoundTag tag, ResourceKey<Level> dimension) {
        UUID id = tag.hasUUID("id") ? tag.getUUID("id") : UUID.randomUUID();
        ChunkPos pos = new ChunkPos(tag.getInt("chunkX"), tag.getInt("chunkZ"));
        UUID cityId = tag.getUUID("cityId");

        ClaimedChunk chunk = new ClaimedChunk(id, pos, dimension, cityId);
        chunk.ownershipType = OwnershipType.valueOf(tag.getString("ownershipType"));

        if (tag.hasUUID("playerOwner")) {
            chunk.playerOwner = tag.getUUID("playerOwner");
        }

        // Load sale information
        chunk.forSale = tag.getBoolean("forSale");
        if (chunk.forSale) {
            chunk.salePrice = tag.getDouble("salePrice");
            if (tag.hasUUID("sellerId")) {
                chunk.sellerId = tag.getUUID("sellerId");
            }
        }

        // Load role permissions
        if (tag.contains("rolePermissions")) {
            CompoundTag rolePermsTag = tag.getCompound("rolePermissions");
            for (PermissionLevel level : PermissionLevel.values()) {
                if (rolePermsTag.contains(level.name())) {
                    ListTag permList = rolePermsTag.getList(level.name(), Tag.TAG_STRING);
                    Set<Permission> perms = EnumSet.noneOf(Permission.class);
                    for (int i = 0; i < permList.size(); i++) {
                        perms.add(Permission.valueOf(permList.getString(i)));
                    }
                    chunk.rolePermissions.put(level, perms);
                }
            }
        }

        // Load player permissions
        if (tag.contains("playerPermissions")) {
            CompoundTag playerPermsTag = tag.getCompound("playerPermissions");
            for (String key : playerPermsTag.getAllKeys()) {
                UUID playerId = UUID.fromString(key);
                ListTag permList = playerPermsTag.getList(key, Tag.TAG_STRING);
                Set<Permission> perms = EnumSet.noneOf(Permission.class);
                for (int i = 0; i < permList.size(); i++) {
                    perms.add(Permission.valueOf(permList.getString(i)));
                }
                chunk.playerPermissions.put(playerId, perms);
            }
        }

        return chunk;
    }
}

