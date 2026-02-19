package com.statecraft.event;

import com.statecraft.StateCraft;
import com.statecraft.contract.Contract;
import com.statecraft.contract.ContractManager;
import com.statecraft.core.ChunkClaimManager;
import com.statecraft.core.ElectionManager;
import com.statecraft.core.Nation;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.List;

/**
 * Handles player join/leave events
 */
public class PlayerJoinHandler {

    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        // Check if player is in a nation and send welcome message
        Nation nation = ChunkClaimManager.getInstance().getPlayerNation(player.getUUID());
        if (nation != null) {
            player.displayClientMessage(
                Component.literal("§6Welcome back to §e" + nation.getName() + "§6!"),
                false
            );

            // Notify of active elections
            ElectionManager.getInstance().onPlayerLogin(player, player.server);

            // Send contract notifications
            sendContractLoginNotifications(player, nation);
        }

        StateCraft.LOGGER.debug("Player {} logged in", player.getName().getString());
    }

    /**
     * Send contract-related notifications to the player on login
     */
    private void sendContractLoginNotifications(ServerPlayer player, Nation nation) {
        ContractManager contractManager = ContractManager.getInstance();
        List<Contract> nationContracts = contractManager.getNationContracts(nation.getId());

        boolean isLeader = player.getUUID().equals(nation.getLeaderId());
        boolean isOfficer = nation.isOfficer(player.getUUID());

        int pendingApprovals = 0;
        int pendingMilestones = 0;
        int openBidding = 0;
        int overdueContracts = 0;

        for (Contract contract : nationContracts) {
            // Count pending bid approvals
            if (contract.getStatus() == Contract.Status.PENDING_APPROVAL) {
                pendingApprovals++;
            }

            // Count open bidding contracts
            if (contract.getStatus() == Contract.Status.BIDDING) {
                openBidding++;
            }

            // Count pending milestone approvals
            if (contract.getStatus() == Contract.Status.ACTIVE &&
                !contract.getMilestoneApprovalRequests().isEmpty()) {
                pendingMilestones++;
            }

            // Count overdue contracts
            if (contract.isOverdue()) {
                overdueContracts++;
            }

            // If player is the contractor, notify them of important status
            if (player.getUUID().equals(contract.getContractorId()) &&
                contract.getStatus() == Contract.Status.ACTIVE) {

                long timeRemaining = contract.getDeadlineTimeRemaining();
                long oneDayMs = 24 * 60 * 60 * 1000;

                if (timeRemaining <= 0) {
                    player.sendSystemMessage(Component.literal(
                        "§c[Contracts] §f⚠ Your contract " + contract.getContractNumber() +
                        " is OVERDUE! Progress: " + contract.getProgressPercent() + "%"));
                } else if (timeRemaining <= oneDayMs) {
                    player.sendSystemMessage(Component.literal(
                        "§e[Contracts] §fContract " + contract.getContractNumber() +
                        " deadline is within 24 hours! Progress: " + contract.getProgressPercent() + "%"));
                }

                // Notify of pending milestone requests
                if (!contract.getMilestoneApprovalRequests().isEmpty()) {
                    int pendingMilestone = contract.getMilestoneApprovalRequests().keySet().stream()
                        .min(Integer::compareTo).orElse(0);
                    player.sendSystemMessage(Component.literal(
                        "§e[Contracts] §fMilestone " + pendingMilestone + "% approval pending for " +
                        contract.getContractNumber()));
                }
            }
        }

        // Send summary notifications to leaders/officers
        if (isLeader || isOfficer) {
            if (pendingApprovals > 0) {
                player.sendSystemMessage(Component.literal(
                    "§e[Contracts] §f" + pendingApprovals + " contract(s) awaiting bid approval."));
            }
            if (pendingMilestones > 0) {
                player.sendSystemMessage(Component.literal(
                    "§e[Contracts] §f" + pendingMilestones + " milestone approval(s) pending review."));
            }
            if (overdueContracts > 0) {
                player.sendSystemMessage(Component.literal(
                    "§c[Contracts] §f" + overdueContracts + " contract(s) are OVERDUE!"));
            }
        }

        // Notify all nation members of open bidding opportunities
        if (openBidding > 0) {
            player.sendSystemMessage(Component.literal(
                "§a[Contracts] §f" + openBidding + " contract(s) open for bidding. Use /sc gui to view."));
        }
    }

    @SubscribeEvent
    public void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        StateCraft.LOGGER.debug("Player {} logged out", player.getName().getString());
    }
}

