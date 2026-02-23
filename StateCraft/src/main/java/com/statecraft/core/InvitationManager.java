package com.statecraft.core;

import com.statecraft.StateCraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Manages all pending invitations
 * Handles invite creation, acceptance, denial, and expiration
 */
public class InvitationManager {
    private static InvitationManager instance;

    // All pending invitations indexed by ID
    private final Map<UUID, Invitation> invitations;

    // Index by target player for quick lookup
    private final Map<UUID, Set<UUID>> playerInvitations;

    private boolean dirty;

    private InvitationManager() {
        this.invitations = new ConcurrentHashMap<>();
        this.playerInvitations = new ConcurrentHashMap<>();
        this.dirty = false;
    }

    public static InvitationManager getInstance() {
        if (instance == null) {
            instance = new InvitationManager();
        }
        return instance;
    }

    public static void resetInstance() {
        instance = null;
    }

    /**
     * Create a new invitation
     */
    public Invitation createInvitation(UUID targetPlayerId, UUID senderId, UUID entityId, Invitation.InvitationType type) {
        // Check if player already has a pending invite to this entity
        for (Invitation existing : getInvitationsForPlayer(targetPlayerId)) {
            if (existing.getEntityId().equals(entityId) && existing.getType() == type) {
                return null; // Already has pending invite
            }
        }

        Invitation invitation = new Invitation(targetPlayerId, senderId, entityId, type);
        invitations.put(invitation.getId(), invitation);
        playerInvitations.computeIfAbsent(targetPlayerId, k -> ConcurrentHashMap.newKeySet())
                        .add(invitation.getId());

        markDirty();
        StateCraft.LOGGER.debug("Created invitation {} for player {}", invitation.getId(), targetPlayerId);
        return invitation;
    }

    /**
     * Get an invitation by ID
     */
    @Nullable
    public Invitation getInvitation(UUID invitationId) {
        Invitation inv = invitations.get(invitationId);
        if (inv != null && inv.isExpired()) {
            removeInvitation(invitationId);
            return null;
        }
        return inv;
    }

    /**
     * Get all pending invitations for a player
     */
    public List<Invitation> getInvitationsForPlayer(UUID playerId) {
        Set<UUID> inviteIds = playerInvitations.get(playerId);
        if (inviteIds == null) {
            return Collections.emptyList();
        }

        // Clean up expired and return valid ones
        List<Invitation> result = new ArrayList<>();
        List<UUID> expired = new ArrayList<>();

        for (UUID id : inviteIds) {
            Invitation inv = invitations.get(id);
            if (inv == null || inv.isExpired()) {
                expired.add(id);
            } else {
                result.add(inv);
            }
        }

        // Remove expired
        for (UUID id : expired) {
            removeInvitation(id);
        }

        return result;
    }

    /**
     * Accept an invitation
     * @return true if successful
     */
    public boolean acceptInvitation(UUID invitationId, UUID playerId) {
        Invitation invitation = getInvitation(invitationId);
        if (invitation == null) {
            return false;
        }

        if (!invitation.getTargetPlayerId().equals(playerId)) {
            return false; // Not their invitation
        }

        ChunkClaimManager claimManager = ChunkClaimManager.getInstance();
        boolean success = false;

        switch (invitation.getType()) {
            case NATION:
                Nation nation = claimManager.getNation(invitation.getEntityId());
                if (nation != null) {
                    // Check if player is already in a nation
                    if (claimManager.getPlayerNation(playerId) != null) {
                        return false;
                    }
                    success = claimManager.addPlayerToNation(playerId, nation.getId());
                }
                break;

            case CITY:
                City city = claimManager.getCity(invitation.getEntityId());
                if (city != null) {
                    city.addResident(playerId);
                    success = true;
                    claimManager.markDirty();
                }
                break;
        }

        if (success) {
            removeInvitation(invitationId);
            StateCraft.LOGGER.info("Player {} accepted invitation {}", playerId, invitationId);
        }

        return success;
    }

    /**
     * Deny/decline an invitation
     */
    public boolean denyInvitation(UUID invitationId, UUID playerId) {
        Invitation invitation = getInvitation(invitationId);
        if (invitation == null) {
            return false;
        }

        if (!invitation.getTargetPlayerId().equals(playerId)) {
            return false;
        }

        removeInvitation(invitationId);
        StateCraft.LOGGER.debug("Player {} denied invitation {}", playerId, invitationId);
        return true;
    }

    /**
     * Cancel an invitation (by sender or admin)
     */
    public boolean cancelInvitation(UUID invitationId, UUID cancelerId) {
        Invitation invitation = getInvitation(invitationId);
        if (invitation == null) {
            return false;
        }

        // Check permission - must be sender or nation leader/officer
        if (!invitation.getSenderId().equals(cancelerId)) {
            ChunkClaimManager claimManager = ChunkClaimManager.getInstance();
            if (invitation.getType() == Invitation.InvitationType.NATION) {
                Nation nation = claimManager.getNation(invitation.getEntityId());
                if (nation == null || !nation.isLeaderOrOfficer(cancelerId)) {
                    return false;
                }
            } else {
                return false;
            }
        }

        removeInvitation(invitationId);
        return true;
    }

    public void removeInvitation(UUID invitationId) {
        Invitation invitation = invitations.remove(invitationId);
        if (invitation != null) {
            Set<UUID> playerInvs = playerInvitations.get(invitation.getTargetPlayerId());
            if (playerInvs != null) {
                playerInvs.remove(invitationId);
                if (playerInvs.isEmpty()) {
                    playerInvitations.remove(invitation.getTargetPlayerId());
                }
            }
            markDirty();
        }
    }

    /**
     * Clean up all expired invitations
     */
    public void cleanupExpired() {
        List<UUID> expired = invitations.values().stream()
            .filter(Invitation::isExpired)
            .map(Invitation::getId)
            .collect(Collectors.toList());

        for (UUID id : expired) {
            removeInvitation(id);
        }

        if (!expired.isEmpty()) {
            StateCraft.LOGGER.debug("Cleaned up {} expired invitations", expired.size());
        }
    }

    // Data Management
    public void clear() {
        invitations.clear();
        playerInvitations.clear();
        dirty = false;
    }

    public void markDirty() {
        this.dirty = true;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void clearDirty() {
        this.dirty = false;
    }

    // NBT Serialization
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag invitesList = new ListTag();

        for (Invitation invitation : invitations.values()) {
            if (!invitation.isExpired()) {
                invitesList.add(invitation.save());
            }
        }

        tag.put("invitations", invitesList);
        return tag;
    }

    public void load(CompoundTag tag) {
        clear();

        if (tag.contains("invitations")) {
            ListTag invitesList = tag.getList("invitations", Tag.TAG_COMPOUND);
            for (int i = 0; i < invitesList.size(); i++) {
                Invitation invitation = Invitation.load(invitesList.getCompound(i));
                if (!invitation.isExpired()) {
                    invitations.put(invitation.getId(), invitation);
                    playerInvitations.computeIfAbsent(invitation.getTargetPlayerId(), k -> ConcurrentHashMap.newKeySet())
                                    .add(invitation.getId());
                }
            }
        }

        StateCraft.LOGGER.debug("Loaded {} pending invitations", invitations.size());
    }
}

