package com.statecraft.event;

import com.statecraft.contract.Contract;
import com.statecraft.contract.ContractBid;
import com.statecraft.contract.ContractManager;
import com.statecraft.core.ChunkClaimManager;
import com.statecraft.core.ElectionManager;
import com.statecraft.core.Nation;
import com.statecraft.data.NationSavedData;
import com.statecraft.legislature.LegislatureManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.UUID;

/**
 * Handles server tick events for election, legislature, and contract timing
 */
@Mod.EventBusSubscriber(modid = "statecraft")
public class ElectionTickHandler {

    private static int tickCounter = 0;
    private static final int CHECK_INTERVAL = 20 * 60; // Check every minute (20 ticks * 60 seconds)

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        tickCounter++;
        if (tickCounter < CHECK_INTERVAL) return;
        tickCounter = 0;

        // Run election tick check
        ElectionManager electionManager = ElectionManager.getInstance();
        electionManager.tick(event.getServer());

        // Run legislature tick check (handles bill transitions)
        LegislatureManager legislatureManager = LegislatureManager.getInstance();
        legislatureManager.setServer(event.getServer()); // Ensure server reference is set
        legislatureManager.tick(event.getServer());

        // Run contract tick check (handles bidding expiration)
        ContractManager contractManager = ContractManager.getInstance();

        // Process bidding expirations
        List<Contract> closedBiddingContracts = contractManager.processBiddingExpirations();
        for (Contract contract : closedBiddingContracts) {
            notifyBiddingClosed(event.getServer(), contract);
        }

        // Process bidding ending warnings (1 day, 1 hour before close)
        List<ContractManager.BiddingEndingWarning> biddingWarnings = contractManager.processBiddingEndingWarnings();
        for (ContractManager.BiddingEndingWarning warning : biddingWarnings) {
            notifyBiddingEndingSoon(event.getServer(), warning);
        }

        // Process deadline warnings (1 day, 1 hour before deadline)
        List<ContractManager.DeadlineWarning> deadlineWarnings = contractManager.processDeadlineWarnings();
        for (ContractManager.DeadlineWarning warning : deadlineWarnings) {
            notifyDeadlineApproaching(event.getServer(), warning);
        }

        // Check for newly overdue contracts and notify
        List<Contract> newlyOverdueContracts = contractManager.processOverdueContracts();
        for (Contract contract : newlyOverdueContracts) {
            notifyContractOverdue(event.getServer(), contract);
        }

        // Save if any manager is dirty
        boolean needsSave = electionManager.isDirty() || legislatureManager.isDirty() || contractManager.isDirty();
        if (needsSave) {
            ServerLevel overworld = event.getServer().getLevel(Level.OVERWORLD);
            if (overworld != null) {
                NationSavedData.get(overworld).setDirty();
            }
        }
    }

    /**
     * Notify relevant players when bidding is ending soon
     */
    private static void notifyBiddingEndingSoon(net.minecraft.server.MinecraftServer server, ContractManager.BiddingEndingWarning warning) {
        Contract contract = warning.contract;
        Nation nation = ChunkClaimManager.getInstance().getNation(contract.getNationId());
        if (nation == null) return;

        String timeStr = warning.warningType.equals("1_HOUR") ? "1 hour" : "1 day";
        String urgency = warning.warningType.equals("1_HOUR") ? "§c" : "§e";

        // Notify nation leader
        UUID leaderId = nation.getLeaderId();
        if (leaderId != null) {
            ServerPlayer leader = server.getPlayerList().getPlayer(leaderId);
            if (leader != null) {
                leader.sendSystemMessage(Component.literal(
                    urgency + "[Contracts] §fBidding for contract " + contract.getContractNumber() +
                    " (" + contract.getTitle() + ") ends in " + timeStr + "! " +
                    contract.getBids().size() + " bid(s) received so far."));
            }
        }

        // Notify all nation members who haven't bid yet (opportunity to bid)
        for (UUID memberId : nation.getAllMembers()) {
            if (memberId.equals(leaderId)) continue; // Already notified

            // Check if they've already bid
            boolean hasBid = false;
            for (ContractBid bid : contract.getBids()) {
                if (bid.getBidderId().equals(memberId)) {
                    hasBid = true;
                    break;
                }
            }

            ServerPlayer member = server.getPlayerList().getPlayer(memberId);
            if (member != null) {
                if (hasBid) {
                    member.sendSystemMessage(Component.literal(
                        urgency + "[Contracts] §fBidding for contract " + contract.getContractNumber() +
                        " ends in " + timeStr + ". Your bid is submitted."));
                } else {
                    member.sendSystemMessage(Component.literal(
                        urgency + "[Contracts] §fBidding for contract " + contract.getContractNumber() +
                        " (" + contract.getTitle() + ") ends in " + timeStr + "! Submit your bid now!"));
                }
            }
        }
    }

    /**
     * Notify relevant players when contract deadline is approaching
     */
    private static void notifyDeadlineApproaching(net.minecraft.server.MinecraftServer server, ContractManager.DeadlineWarning warning) {
        Contract contract = warning.contract;
        Nation nation = ChunkClaimManager.getInstance().getNation(contract.getNationId());
        if (nation == null) return;

        String timeStr = warning.warningType.equals("1_HOUR") ? "1 hour" : "1 day";
        String urgency = warning.warningType.equals("1_HOUR") ? "§c" : "§e";

        // Notify contractor (most important!)
        if (contract.getContractorId() != null) {
            ServerPlayer contractor = server.getPlayerList().getPlayer(contract.getContractorId());
            if (contractor != null) {
                contractor.sendSystemMessage(Component.literal(
                    urgency + "[Contracts] §f⚠ Contract " + contract.getContractNumber() +
                    " deadline in " + timeStr + "! Progress: " + contract.getProgressPercent() + "%. " +
                    "Complete your work soon!"));
            }
        }

        // Notify nation leader
        UUID leaderId = nation.getLeaderId();
        if (leaderId != null && !leaderId.equals(contract.getContractorId())) {
            ServerPlayer leader = server.getPlayerList().getPlayer(leaderId);
            if (leader != null) {
                leader.sendSystemMessage(Component.literal(
                    urgency + "[Contracts] §fContract " + contract.getContractNumber() +
                    " deadline in " + timeStr + ". Contractor: " + contract.getContractorName() +
                    ", Progress: " + contract.getProgressPercent() + "%."));
            }
        }
    }

    /**
     * Notify relevant players when bidding closes on a contract
     */
    private static void notifyBiddingClosed(net.minecraft.server.MinecraftServer server, Contract contract) {
        Nation nation = ChunkClaimManager.getInstance().getNation(contract.getNationId());
        if (nation == null) return;

        int bidCount = contract.getBids().size();
        String message = "§e[Contracts] §fBidding has closed on contract " + contract.getContractNumber() +
            " (" + contract.getTitle() + "). " + bidCount + " bid(s) received. Awaiting legislature approval.";

        // Notify nation leader
        UUID leaderId = nation.getLeaderId();
        if (leaderId != null) {
            ServerPlayer leader = server.getPlayerList().getPlayer(leaderId);
            if (leader != null) {
                leader.sendSystemMessage(Component.literal(message));
            }
        }

        // Notify all bidders
        for (ContractBid bid : contract.getBids()) {
            ServerPlayer bidder = server.getPlayerList().getPlayer(bid.getBidderId());
            if (bidder != null && !bid.getBidderId().equals(leaderId)) {
                bidder.sendSystemMessage(Component.literal(
                    "§e[Contracts] §fBidding has closed on contract " + contract.getContractNumber() +
                    ". Your bid is being reviewed by the legislature."));
            }
        }
    }

    /**
     * Notify relevant players when a contract becomes overdue
     */
    private static void notifyContractOverdue(net.minecraft.server.MinecraftServer server, Contract contract) {
        Nation nation = ChunkClaimManager.getInstance().getNation(contract.getNationId());
        if (nation == null) return;

        // Notify contractor
        if (contract.getContractorId() != null) {
            ServerPlayer contractor = server.getPlayerList().getPlayer(contract.getContractorId());
            if (contractor != null) {
                contractor.sendSystemMessage(Component.literal(
                    "§c[Contracts] §f⚠ Contract " + contract.getContractNumber() +
                    " (" + contract.getTitle() + ") is now OVERDUE! Please complete the work or contact the government."));
            }
        }

        // Notify nation leader
        UUID leaderId = nation.getLeaderId();
        if (leaderId != null) {
            ServerPlayer leader = server.getPlayerList().getPlayer(leaderId);
            if (leader != null) {
                leader.sendSystemMessage(Component.literal(
                    "§c[Contracts] §fContract " + contract.getContractNumber() +
                    " (" + contract.getTitle() + ") is now OVERDUE! Contractor: " + contract.getContractorName() +
                    ". Progress: " + contract.getProgressPercent() + "%. Legislative action may be required."));
            }
        }
    }
}

