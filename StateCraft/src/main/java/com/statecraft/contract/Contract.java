package com.statecraft.contract;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.ChunkPos;

import java.util.*;

/**
 * Represents a government construction contract
 * Contracts designate chunks for projects with compensation for builders
 */
public class Contract {

    public enum Status {
        DRAFT,          // Being defined, not yet open for bidding
        BIDDING,        // Open for bids from players
        PENDING_APPROVAL, // Bids received, awaiting legislature approval
        ACTIVE,         // Contract awarded and work in progress
        COMPLETED,      // Work finished and verified
        CANCELLED,      // Contract cancelled
        FAILED          // Contractor failed to complete
    }

    public enum CompensationType {
        FIXED,          // Fixed amount paid on completion
        MILESTONE,      // Payments at milestones (25%, 50%, 75%, 100%)
        VALUATION_BASED // Based on chunk valuation increase
    }

    private final UUID contractId;
    private final UUID nationId;
    private final String contractNumber;  // e.g., "CONTRACT-2026-001"

    // Contract details
    private String title;
    private String description;
    private String requirements;  // Detailed build requirements
    private UUID creatorId;
    private String creatorName;
    private long createdTime;

    // Location - chunks designated for this project
    private final List<ChunkPos> designatedChunks;
    private String dimension;

    // Compensation
    private CompensationType compensationType;
    private double totalBudget;           // Total amount budgeted
    private double bondAmount;            // Optional bond required from contractor (0 = no bond)
    private double escrowBalance;         // Current amount held in escrow

    // Milestones (for MILESTONE compensation type)
    private final Map<Integer, String> milestoneDescriptions;  // percentage -> description
    private final Map<Integer, Boolean> milestonesCompleted;   // percentage -> completed

    // Bidding
    private final List<ContractBid> bids;
    private long biddingEndTime;
    private UUID selectedBidId;           // Legislature-approved bid

    // Contractor
    private UUID contractorId;
    private String contractorName;
    private long startTime;
    private long deadline;                // Completion deadline

    // Progress tracking
    private int progressPercent;
    private Status status;

    // Completion
    private long completedTime;
    private double amountPaid;

    public Contract(UUID nationId, String contractNumber, String title, UUID creatorId, String creatorName) {
        this.contractId = UUID.randomUUID();
        this.nationId = nationId;
        this.contractNumber = contractNumber;
        this.title = title;
        this.creatorId = creatorId;
        this.creatorName = creatorName;
        this.createdTime = System.currentTimeMillis();
        this.designatedChunks = new ArrayList<>();
        this.dimension = "minecraft:overworld";
        this.compensationType = CompensationType.MILESTONE;
        this.totalBudget = 0;
        this.bondAmount = 0;
        this.escrowBalance = 0;
        this.milestoneDescriptions = new HashMap<>();
        this.milestonesCompleted = new HashMap<>();
        this.bids = new ArrayList<>();
        this.progressPercent = 0;
        this.status = Status.DRAFT;
        this.amountPaid = 0;

        // Default milestones
        milestoneDescriptions.put(25, "Foundation/Base structure complete");
        milestoneDescriptions.put(50, "Main structure complete");
        milestoneDescriptions.put(75, "Interior/Details complete");
        milestoneDescriptions.put(100, "Final inspection passed");
        milestonesCompleted.put(25, false);
        milestonesCompleted.put(50, false);
        milestonesCompleted.put(75, false);
        milestonesCompleted.put(100, false);
    }

    // Private constructor for NBT loading
    private Contract(UUID contractId, UUID nationId, String contractNumber) {
        this.contractId = contractId;
        this.nationId = nationId;
        this.contractNumber = contractNumber;
        this.designatedChunks = new ArrayList<>();
        this.milestoneDescriptions = new HashMap<>();
        this.milestonesCompleted = new HashMap<>();
        this.bids = new ArrayList<>();
    }

    // Getters
    public UUID getContractId() { return contractId; }
    public UUID getNationId() { return nationId; }
    public String getContractNumber() { return contractNumber; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getRequirements() { return requirements; }
    public UUID getCreatorId() { return creatorId; }
    public String getCreatorName() { return creatorName; }
    public long getCreatedTime() { return createdTime; }
    public List<ChunkPos> getDesignatedChunks() { return Collections.unmodifiableList(designatedChunks); }
    public String getDimension() { return dimension; }
    public CompensationType getCompensationType() { return compensationType; }
    public double getTotalBudget() { return totalBudget; }
    public double getBondAmount() { return bondAmount; }
    public double getEscrowBalance() { return escrowBalance; }
    public Map<Integer, String> getMilestoneDescriptions() { return Collections.unmodifiableMap(milestoneDescriptions); }
    public Map<Integer, Boolean> getMilestonesCompleted() { return Collections.unmodifiableMap(milestonesCompleted); }
    public List<ContractBid> getBids() { return Collections.unmodifiableList(bids); }
    public long getBiddingEndTime() { return biddingEndTime; }
    public UUID getSelectedBidId() { return selectedBidId; }
    public UUID getContractorId() { return contractorId; }
    public String getContractorName() { return contractorName; }
    public long getStartTime() { return startTime; }
    public long getDeadline() { return deadline; }
    public int getProgressPercent() { return progressPercent; }
    public Status getStatus() { return status; }
    public long getCompletedTime() { return completedTime; }
    public double getAmountPaid() { return amountPaid; }

    // Setters for draft stage
    public void setTitle(String title) {
        if (status == Status.DRAFT) {
            this.title = title;
        }
    }

    public void setDescription(String description) {
        if (status == Status.DRAFT || status == Status.BIDDING) {
            this.description = description;
        }
    }

    public void setRequirements(String requirements) {
        if (status == Status.DRAFT || status == Status.BIDDING) {
            this.requirements = requirements;
        }
    }

    public void setDimension(String dimension) {
        if (status == Status.DRAFT) {
            this.dimension = dimension;
        }
    }

    public void setCompensationType(CompensationType type) {
        if (status == Status.DRAFT) {
            this.compensationType = type;
        }
    }

    public void setTotalBudget(double budget) {
        if (status == Status.DRAFT) {
            this.totalBudget = Math.max(0, budget);
        }
    }

    public void setBondAmount(double bond) {
        if (status == Status.DRAFT) {
            this.bondAmount = Math.max(0, bond);
        }
    }

    public void addDesignatedChunk(ChunkPos chunk) {
        if (status == Status.DRAFT && !designatedChunks.contains(chunk)) {
            designatedChunks.add(chunk);
        }
    }

    public void removeDesignatedChunk(ChunkPos chunk) {
        if (status == Status.DRAFT) {
            designatedChunks.remove(chunk);
        }
    }

    public void setMilestoneDescription(int percent, String description) {
        if (status == Status.DRAFT && (percent == 25 || percent == 50 || percent == 75 || percent == 100)) {
            milestoneDescriptions.put(percent, description);
        }
    }

    // Status transitions

    /**
     * Open the contract for bidding
     */
    public boolean openForBidding(long biddingDurationMs) {
        if (status != Status.DRAFT) return false;
        if (designatedChunks.isEmpty()) return false;
        if (totalBudget <= 0) return false;
        if (title == null || title.trim().isEmpty()) return false;

        this.status = Status.BIDDING;
        this.biddingEndTime = System.currentTimeMillis() + biddingDurationMs;
        return true;
    }

    /**
     * Submit a bid on this contract
     */
    public boolean submitBid(ContractBid bid) {
        if (status != Status.BIDDING) return false;
        if (System.currentTimeMillis() > biddingEndTime) return false;

        // Check if player already has a bid
        for (ContractBid existing : bids) {
            if (existing.getBidderId().equals(bid.getBidderId())) {
                // Update existing bid
                bids.remove(existing);
                break;
            }
        }

        bids.add(bid);
        return true;
    }

    /**
     * Close bidding and move to pending approval
     */
    public boolean closeBidding() {
        if (status != Status.BIDDING) return false;

        this.status = Status.PENDING_APPROVAL;
        return true;
    }

    /**
     * Legislature approves a specific bid
     */
    public boolean approveBid(UUID bidId, long deadlineDurationMs) {
        if (status != Status.PENDING_APPROVAL) return false;

        ContractBid selectedBid = null;
        for (ContractBid bid : bids) {
            if (bid.getBidId().equals(bidId)) {
                selectedBid = bid;
                break;
            }
        }

        if (selectedBid == null) return false;

        this.selectedBidId = bidId;
        this.contractorId = selectedBid.getBidderId();
        this.contractorName = selectedBid.getBidderName();
        this.startTime = System.currentTimeMillis();
        this.deadline = this.startTime + deadlineDurationMs;
        this.status = Status.ACTIVE;

        // Use bid amount as actual budget if lower than max budget
        if (selectedBid.getBidAmount() < this.totalBudget) {
            this.totalBudget = selectedBid.getBidAmount();
        }

        return true;
    }

    /**
     * Update progress on the contract
     */
    public void updateProgress(int percent) {
        if (status != Status.ACTIVE) return;
        this.progressPercent = Math.max(0, Math.min(100, percent));
    }

    /**
     * Complete a milestone
     */
    public boolean completeMilestone(int milestonePercent) {
        if (status != Status.ACTIVE) return false;
        if (!milestonesCompleted.containsKey(milestonePercent)) return false;
        if (milestonesCompleted.get(milestonePercent)) return false; // Already completed

        // Check that progress is at or above milestone
        if (progressPercent < milestonePercent) return false;

        milestonesCompleted.put(milestonePercent, true);
        return true;
    }

    /**
     * Record a payment made to contractor
     */
    public void recordPayment(double amount) {
        this.amountPaid += amount;
    }

    /**
     * Add funds to escrow (alias for depositToEscrow)
     */
    public void addToEscrow(double amount) {
        this.escrowBalance += amount;
    }

    /**
     * Deposit funds into escrow
     */
    public void depositToEscrow(double amount) {
        this.escrowBalance += amount;
    }

    /**
     * Withdraw funds from escrow
     * @return true if there were sufficient funds, false otherwise
     */
    public boolean withdrawFromEscrow(double amount) {
        if (this.escrowBalance >= amount) {
            this.escrowBalance -= amount;
            return true;
        }
        return false;
    }

    /**
     * Get the next milestone payment amount
     */
    public double getNextMilestonePayment() {
        if (compensationType != CompensationType.MILESTONE) return 0;

        int[] milestones = {25, 50, 75, 100};
        for (int milestone : milestones) {
            if (!milestonesCompleted.get(milestone)) {
                // Each milestone is 25% of total
                return totalBudget * 0.25;
            }
        }
        return 0;
    }

    /**
     * Get the next incomplete milestone percentage
     */
    public int getNextMilestone() {
        int[] milestones = {25, 50, 75, 100};
        for (int milestone : milestones) {
            if (!milestonesCompleted.getOrDefault(milestone, false)) {
                return milestone;
            }
        }
        return 100;
    }

    /**
     * Complete the contract
     */
    public boolean complete() {
        if (status != Status.ACTIVE) return false;
        if (progressPercent < 100) return false;

        // All milestones must be completed for milestone type
        if (compensationType == CompensationType.MILESTONE) {
            for (Boolean completed : milestonesCompleted.values()) {
                if (!completed) return false;
            }
        }

        this.status = Status.COMPLETED;
        this.completedTime = System.currentTimeMillis();
        return true;
    }

    /**
     * Cancel the contract
     */
    public boolean cancel() {
        if (status == Status.COMPLETED || status == Status.FAILED) return false;

        this.status = Status.CANCELLED;
        return true;
    }

    /**
     * Mark contract as failed (contractor didn't complete)
     */
    public boolean fail() {
        if (status != Status.ACTIVE) return false;

        this.status = Status.FAILED;
        return true;
    }

    /**
     * Check if bidding has expired
     */
    public boolean isBiddingExpired() {
        return status == Status.BIDDING && System.currentTimeMillis() > biddingEndTime;
    }

    /**
     * Check if deadline has passed
     */
    public boolean isOverdue() {
        return status == Status.ACTIVE && System.currentTimeMillis() > deadline;
    }

    /**
     * Get time remaining for bidding
     */
    public long getBiddingTimeRemaining() {
        if (status != Status.BIDDING) return 0;
        return Math.max(0, biddingEndTime - System.currentTimeMillis());
    }

    /**
     * Get time remaining until deadline
     */
    public long getDeadlineTimeRemaining() {
        if (status != Status.ACTIVE) return 0;
        return Math.max(0, deadline - System.currentTimeMillis());
    }

    // NBT Serialization

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();

        tag.putUUID("contractId", contractId);
        tag.putUUID("nationId", nationId);
        tag.putString("contractNumber", contractNumber);
        tag.putString("title", title != null ? title : "");
        tag.putString("description", description != null ? description : "");
        tag.putString("requirements", requirements != null ? requirements : "");
        tag.putUUID("creatorId", creatorId);
        tag.putString("creatorName", creatorName != null ? creatorName : "");
        tag.putLong("createdTime", createdTime);
        tag.putString("dimension", dimension);
        tag.putString("compensationType", compensationType.name());
        tag.putDouble("totalBudget", totalBudget);
        tag.putDouble("bondAmount", bondAmount);
        tag.putDouble("escrowBalance", escrowBalance);
        tag.putLong("biddingEndTime", biddingEndTime);
        tag.putString("status", status.name());
        tag.putInt("progressPercent", progressPercent);
        tag.putLong("completedTime", completedTime);
        tag.putDouble("amountPaid", amountPaid);

        // Designated chunks
        ListTag chunksTag = new ListTag();
        for (ChunkPos chunk : designatedChunks) {
            CompoundTag chunkTag = new CompoundTag();
            chunkTag.putInt("x", chunk.x);
            chunkTag.putInt("z", chunk.z);
            chunksTag.add(chunkTag);
        }
        tag.put("designatedChunks", chunksTag);

        // Milestone descriptions
        CompoundTag milestonesDescTag = new CompoundTag();
        for (Map.Entry<Integer, String> entry : milestoneDescriptions.entrySet()) {
            milestonesDescTag.putString(entry.getKey().toString(), entry.getValue());
        }
        tag.put("milestoneDescriptions", milestonesDescTag);

        // Milestones completed
        CompoundTag milestonesCompTag = new CompoundTag();
        for (Map.Entry<Integer, Boolean> entry : milestonesCompleted.entrySet()) {
            milestonesCompTag.putBoolean(entry.getKey().toString(), entry.getValue());
        }
        tag.put("milestonesCompleted", milestonesCompTag);

        // Bids
        ListTag bidsTag = new ListTag();
        for (ContractBid bid : bids) {
            bidsTag.add(bid.save());
        }
        tag.put("bids", bidsTag);

        // Selected bid and contractor
        if (selectedBidId != null) {
            tag.putUUID("selectedBidId", selectedBidId);
        }
        if (contractorId != null) {
            tag.putUUID("contractorId", contractorId);
            tag.putString("contractorName", contractorName != null ? contractorName : "");
        }
        tag.putLong("startTime", startTime);
        tag.putLong("deadline", deadline);

        return tag;
    }

    public static Contract load(CompoundTag tag) {
        UUID contractId = tag.getUUID("contractId");
        UUID nationId = tag.getUUID("nationId");
        String contractNumber = tag.getString("contractNumber");

        Contract contract = new Contract(contractId, nationId, contractNumber);

        contract.title = tag.getString("title");
        contract.description = tag.getString("description");
        contract.requirements = tag.getString("requirements");
        contract.creatorId = tag.getUUID("creatorId");
        contract.creatorName = tag.getString("creatorName");
        contract.createdTime = tag.getLong("createdTime");
        contract.dimension = tag.getString("dimension");
        contract.compensationType = CompensationType.valueOf(tag.getString("compensationType"));
        contract.totalBudget = tag.getDouble("totalBudget");
        contract.bondAmount = tag.getDouble("bondAmount");
        contract.escrowBalance = tag.getDouble("escrowBalance");
        contract.biddingEndTime = tag.getLong("biddingEndTime");
        contract.status = Status.valueOf(tag.getString("status"));
        contract.progressPercent = tag.getInt("progressPercent");
        contract.completedTime = tag.getLong("completedTime");
        contract.amountPaid = tag.getDouble("amountPaid");

        // Designated chunks
        ListTag chunksTag = tag.getList("designatedChunks", Tag.TAG_COMPOUND);
        for (int i = 0; i < chunksTag.size(); i++) {
            CompoundTag chunkTag = chunksTag.getCompound(i);
            contract.designatedChunks.add(new ChunkPos(chunkTag.getInt("x"), chunkTag.getInt("z")));
        }

        // Milestone descriptions
        CompoundTag milestonesDescTag = tag.getCompound("milestoneDescriptions");
        for (String key : milestonesDescTag.getAllKeys()) {
            contract.milestoneDescriptions.put(Integer.parseInt(key), milestonesDescTag.getString(key));
        }

        // Milestones completed
        CompoundTag milestonesCompTag = tag.getCompound("milestonesCompleted");
        for (String key : milestonesCompTag.getAllKeys()) {
            contract.milestonesCompleted.put(Integer.parseInt(key), milestonesCompTag.getBoolean(key));
        }

        // Bids
        ListTag bidsTag = tag.getList("bids", Tag.TAG_COMPOUND);
        for (int i = 0; i < bidsTag.size(); i++) {
            contract.bids.add(ContractBid.load(bidsTag.getCompound(i)));
        }

        // Selected bid and contractor
        if (tag.contains("selectedBidId")) {
            contract.selectedBidId = tag.getUUID("selectedBidId");
        }
        if (tag.contains("contractorId")) {
            contract.contractorId = tag.getUUID("contractorId");
            contract.contractorName = tag.getString("contractorName");
        }
        contract.startTime = tag.getLong("startTime");
        contract.deadline = tag.getLong("deadline");

        return contract;
    }
}


