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
    private final Map<Integer, Long> milestoneApprovalRequests; // percentage -> request timestamp (pending approvals)

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

    // Notification tracking
    private boolean overdueNotificationSent;
    private boolean deadlineWarning1DaySent;
    private boolean deadlineWarning1HourSent;
    private boolean biddingEnding1DaySent;
    private boolean biddingEnding1HourSent;

    // Valuation-based compensation tracking
    private final Map<Long, Integer> baselineImprovementScores;  // chunkKey -> baseline score when contract started
    private double paymentPerImprovementPoint;  // How much to pay per improvement point gained

    // Early completion - contractor can submit for final approval before deadline
    private boolean finalApprovalRequested;
    private long finalApprovalRequestTime;

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
        this.milestoneApprovalRequests = new HashMap<>();
        this.bids = new ArrayList<>();
        this.progressPercent = 0;
        this.status = Status.DRAFT;
        this.amountPaid = 0;
        this.baselineImprovementScores = new HashMap<>();
        this.paymentPerImprovementPoint = 1.0;  // Default $1 per improvement point
        this.overdueNotificationSent = false;
        this.deadlineWarning1DaySent = false;
        this.deadlineWarning1HourSent = false;
        this.biddingEnding1DaySent = false;
        this.biddingEnding1HourSent = false;
        this.finalApprovalRequested = false;
        this.finalApprovalRequestTime = 0;

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
        this.milestoneApprovalRequests = new HashMap<>();
        this.bids = new ArrayList<>();
        this.baselineImprovementScores = new HashMap<>();
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
    public Map<Integer, Long> getMilestoneApprovalRequests() { return Collections.unmodifiableMap(milestoneApprovalRequests); }
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
    public Map<Long, Integer> getBaselineImprovementScores() { return Collections.unmodifiableMap(baselineImprovementScores); }
    public double getPaymentPerImprovementPoint() { return paymentPerImprovementPoint; }
    public boolean isOverdueNotificationSent() { return overdueNotificationSent; }
    public boolean isDeadlineWarning1DaySent() { return deadlineWarning1DaySent; }
    public boolean isDeadlineWarning1HourSent() { return deadlineWarning1HourSent; }
    public boolean isBiddingEnding1DaySent() { return biddingEnding1DaySent; }
    public boolean isBiddingEnding1HourSent() { return biddingEnding1HourSent; }
    public boolean isFinalApprovalRequested() { return finalApprovalRequested; }
    public long getFinalApprovalRequestTime() { return finalApprovalRequestTime; }

    public void setOverdueNotificationSent(boolean sent) { this.overdueNotificationSent = sent; }
    public void setDeadlineWarning1DaySent(boolean sent) { this.deadlineWarning1DaySent = sent; }
    public void setDeadlineWarning1HourSent(boolean sent) { this.deadlineWarning1HourSent = sent; }
    public void setBiddingEnding1DaySent(boolean sent) { this.biddingEnding1DaySent = sent; }
    public void setBiddingEnding1HourSent(boolean sent) { this.biddingEnding1HourSent = sent; }
    public void setFinalApprovalRequested(boolean requested) { this.finalApprovalRequested = requested; }
    public void setFinalApprovalRequestTime(long time) { this.finalApprovalRequestTime = time; }

    // ==================== Admin Setters (bypass status checks) ====================

    /**
     * Admin: Set contract status directly (bypasses normal transitions)
     */
    public void setStatus(Status status) {
        this.status = status;
    }

    /**
     * Admin: Set contractor ID directly
     */
    public void setContractorId(UUID contractorId) {
        this.contractorId = contractorId;
    }

    /**
     * Admin: Set contractor name directly
     */
    public void setContractorName(String contractorName) {
        this.contractorName = contractorName;
    }

    /**
     * Admin: Set progress directly
     */
    public void setProgressPercent(int percent) {
        this.progressPercent = Math.max(0, Math.min(100, percent));
    }

    /**
     * Admin: Set start time directly
     */
    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    /**
     * Admin: Set deadline directly
     */
    public void setDeadline(long deadline) {
        this.deadline = deadline;
    }

    /**
     * Admin: Set completion time directly
     */
    public void setCompletedTime(long completedTime) {
        this.completedTime = completedTime;
    }

    /**
     * Admin: Force set total budget (bypasses draft check)
     */
    public void forceSetTotalBudget(double budget) {
        this.totalBudget = Math.max(0, budget);
    }

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

    public void setPaymentPerImprovementPoint(double paymentPerPoint) {
        if (status == Status.DRAFT) {
            this.paymentPerImprovementPoint = Math.max(0, paymentPerPoint);
        }
    }

    /**
     * Store baseline improvement score for a chunk (called when contract starts)
     */
    public void setBaselineImprovementScore(long chunkKey, int score) {
        baselineImprovementScores.put(chunkKey, score);
    }

    /**
     * Get baseline improvement score for a chunk
     */
    public int getBaselineImprovementScore(long chunkKey) {
        return baselineImprovementScores.getOrDefault(chunkKey, 0);
    }

    /**
     * Calculate total improvement across all designated chunks
     * @param currentScores Map of chunkKey -> current improvement score
     * @return Total improvement points gained since contract started
     */
    public int calculateTotalImprovement(Map<Long, Integer> currentScores) {
        int totalImprovement = 0;
        for (ChunkPos chunk : designatedChunks) {
            long key = chunk.toLong();
            int baseline = baselineImprovementScores.getOrDefault(key, 0);
            int current = currentScores.getOrDefault(key, 0);
            totalImprovement += Math.max(0, current - baseline);
        }
        return totalImprovement;
    }

    /**
     * Calculate valuation-based payment amount
     * @param currentScores Map of chunkKey -> current improvement score
     * @return Payment amount based on improvement
     */
    public double calculateValuationBasedPayment(Map<Long, Integer> currentScores) {
        int improvement = calculateTotalImprovement(currentScores);
        return improvement * paymentPerImprovementPoint;
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
     * Complete a milestone (legislature approval)
     */
    public boolean completeMilestone(int milestonePercent) {
        if (status != Status.ACTIVE) return false;
        if (!milestonesCompleted.containsKey(milestonePercent)) return false;
        if (milestonesCompleted.get(milestonePercent)) return false; // Already completed

        // Check that progress is at or above milestone
        if (progressPercent < milestonePercent) return false;

        milestonesCompleted.put(milestonePercent, true);
        // Remove from pending requests if it was there
        milestoneApprovalRequests.remove(milestonePercent);
        return true;
    }

    /**
     * Contractor requests approval for a milestone
     * @return true if request was submitted, false if invalid
     */
    public boolean requestMilestoneApproval(int milestonePercent) {
        if (status != Status.ACTIVE) return false;
        if (!milestonesCompleted.containsKey(milestonePercent)) return false;
        if (milestonesCompleted.get(milestonePercent)) return false; // Already completed
        if (milestoneApprovalRequests.containsKey(milestonePercent)) return false; // Already requested

        // Check that progress is at or above milestone
        if (progressPercent < milestonePercent) return false;

        // Check that previous milestones are completed
        int[] milestones = {25, 50, 75, 100};
        for (int m : milestones) {
            if (m < milestonePercent && !milestonesCompleted.getOrDefault(m, false)) {
                return false; // Previous milestone not complete
            }
        }

        milestoneApprovalRequests.put(milestonePercent, System.currentTimeMillis());
        return true;
    }

    /**
     * Check if a milestone approval is pending
     */
    public boolean isMilestoneApprovalPending(int milestonePercent) {
        return milestoneApprovalRequests.containsKey(milestonePercent);
    }

    /**
     * Cancel a milestone approval request
     */
    public void cancelMilestoneApprovalRequest(int milestonePercent) {
        milestoneApprovalRequests.remove(milestonePercent);
    }

    /**
     * Contractor requests final approval to complete the contract early.
     * The legislature/leader can then review and approve completion.
     * @return true if request was submitted, false if invalid
     */
    public boolean requestFinalApproval() {
        if (status != Status.ACTIVE) return false;
        if (finalApprovalRequested) return false; // Already requested

        this.finalApprovalRequested = true;
        this.finalApprovalRequestTime = System.currentTimeMillis();
        return true;
    }

    /**
     * Cancel a pending final approval request
     */
    public void cancelFinalApprovalRequest() {
        this.finalApprovalRequested = false;
        this.finalApprovalRequestTime = 0;
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
     * Complete the contract.
     * Can be completed if progress is 100% and milestones are done,
     * OR if the contractor has requested final approval (early completion).
     */
    public boolean complete() {
        if (status != Status.ACTIVE) return false;

        if (finalApprovalRequested) {
            // Legislature is approving early completion
            this.status = Status.COMPLETED;
            this.completedTime = System.currentTimeMillis();
            this.finalApprovalRequested = false;
            return true;
        }

        // Normal completion: requires 100% progress
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
        tag.putBoolean("overdueNotificationSent", overdueNotificationSent);
        tag.putBoolean("deadlineWarning1DaySent", deadlineWarning1DaySent);
        tag.putBoolean("deadlineWarning1HourSent", deadlineWarning1HourSent);
        tag.putBoolean("biddingEnding1DaySent", biddingEnding1DaySent);
        tag.putBoolean("biddingEnding1HourSent", biddingEnding1HourSent);
        tag.putBoolean("finalApprovalRequested", finalApprovalRequested);
        tag.putLong("finalApprovalRequestTime", finalApprovalRequestTime);

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

        // Milestone approval requests
        CompoundTag milestoneRequestsTag = new CompoundTag();
        for (Map.Entry<Integer, Long> entry : milestoneApprovalRequests.entrySet()) {
            milestoneRequestsTag.putLong(entry.getKey().toString(), entry.getValue());
        }
        tag.put("milestoneApprovalRequests", milestoneRequestsTag);

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

        // Baseline improvement scores (for valuation-based compensation)
        CompoundTag baselineTag = new CompoundTag();
        for (Map.Entry<Long, Integer> entry : baselineImprovementScores.entrySet()) {
            baselineTag.putInt(entry.getKey().toString(), entry.getValue());
        }
        tag.put("baselineScores", baselineTag);
        tag.putDouble("paymentPerImprovementPoint", paymentPerImprovementPoint);

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
        contract.overdueNotificationSent = tag.contains("overdueNotificationSent") && tag.getBoolean("overdueNotificationSent");
        contract.deadlineWarning1DaySent = tag.contains("deadlineWarning1DaySent") && tag.getBoolean("deadlineWarning1DaySent");
        contract.deadlineWarning1HourSent = tag.contains("deadlineWarning1HourSent") && tag.getBoolean("deadlineWarning1HourSent");
        contract.biddingEnding1DaySent = tag.contains("biddingEnding1DaySent") && tag.getBoolean("biddingEnding1DaySent");
        contract.biddingEnding1HourSent = tag.contains("biddingEnding1HourSent") && tag.getBoolean("biddingEnding1HourSent");
        contract.finalApprovalRequested = tag.contains("finalApprovalRequested") && tag.getBoolean("finalApprovalRequested");
        contract.finalApprovalRequestTime = tag.contains("finalApprovalRequestTime") ? tag.getLong("finalApprovalRequestTime") : 0;

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

        // Milestone approval requests
        if (tag.contains("milestoneApprovalRequests")) {
            CompoundTag milestoneRequestsTag = tag.getCompound("milestoneApprovalRequests");
            for (String key : milestoneRequestsTag.getAllKeys()) {
                contract.milestoneApprovalRequests.put(Integer.parseInt(key), milestoneRequestsTag.getLong(key));
            }
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

        // Baseline improvement scores (for valuation-based compensation)
        if (tag.contains("baselineScores")) {
            CompoundTag baselineTag = tag.getCompound("baselineScores");
            for (String key : baselineTag.getAllKeys()) {
                contract.baselineImprovementScores.put(Long.parseLong(key), baselineTag.getInt(key));
            }
        }
        contract.paymentPerImprovementPoint = tag.contains("paymentPerImprovementPoint")
            ? tag.getDouble("paymentPerImprovementPoint") : 1.0;

        return contract;
    }
}


