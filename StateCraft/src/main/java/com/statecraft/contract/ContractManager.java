package com.statecraft.contract;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.*;

/**
 * Manages all government contracts across all nations
 */
public class ContractManager {

    private static ContractManager instance;

    // All contracts: contractId -> Contract
    private final Map<UUID, Contract> contracts = new HashMap<>();

    // Nation index: nationId -> Set of contractIds
    private final Map<UUID, Set<UUID>> nationContracts = new HashMap<>();

    // Contractor index: playerId -> Set of contractIds (active contracts)
    private final Map<UUID, Set<UUID>> contractorContracts = new HashMap<>();

    // Contract numbering per nation
    private final Map<UUID, Integer> nextContractNumber = new HashMap<>();
    private int currentYear;

    private boolean dirty = false;

    private ContractManager() {
        this.currentYear = java.time.LocalDate.now().getYear();
    }

    public static ContractManager getInstance() {
        if (instance == null) {
            instance = new ContractManager();
        }
        return instance;
    }

    public static void resetInstance() {
        instance = null;
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

    // ==================== Contract Creation ====================

    /**
     * Generate a contract number for a nation
     */
    public String generateContractNumber(UUID nationId) {
        int year = java.time.LocalDate.now().getYear();
        if (year != currentYear) {
            currentYear = year;
            nextContractNumber.clear();
        }

        int num = nextContractNumber.getOrDefault(nationId, 1);
        nextContractNumber.put(nationId, num + 1);

        return String.format("CONTRACT-%d-%03d", year, num);
    }

    /**
     * Create a new draft contract
     */
    public Contract createContract(UUID nationId, String title, UUID creatorId, String creatorName) {
        String contractNumber = generateContractNumber(nationId);
        Contract contract = new Contract(nationId, contractNumber, title, creatorId, creatorName);

        contracts.put(contract.getContractId(), contract);
        nationContracts.computeIfAbsent(nationId, k -> new HashSet<>()).add(contract.getContractId());

        dirty = true;
        return contract;
    }

    // ==================== Contract Retrieval ====================

    /**
     * Get a contract by ID
     */
    public Contract getContract(UUID contractId) {
        return contracts.get(contractId);
    }

    /**
     * Get all contracts for a nation
     */
    public List<Contract> getNationContracts(UUID nationId) {
        Set<UUID> ids = nationContracts.get(nationId);
        if (ids == null) return Collections.emptyList();

        List<Contract> result = new ArrayList<>();
        for (UUID id : ids) {
            Contract contract = contracts.get(id);
            if (contract != null) {
                result.add(contract);
            }
        }

        // Sort by created time descending (newest first)
        result.sort((a, b) -> Long.compare(b.getCreatedTime(), a.getCreatedTime()));
        return result;
    }

    /**
     * Get contracts by status for a nation
     */
    public List<Contract> getNationContractsByStatus(UUID nationId, Contract.Status status) {
        List<Contract> result = new ArrayList<>();
        for (Contract contract : getNationContracts(nationId)) {
            if (contract.getStatus() == status) {
                result.add(contract);
            }
        }
        return result;
    }

    /**
     * Get all active contracts for a contractor
     */
    public List<Contract> getContractorContracts(UUID contractorId) {
        Set<UUID> ids = contractorContracts.get(contractorId);
        if (ids == null) return Collections.emptyList();

        List<Contract> result = new ArrayList<>();
        for (UUID id : ids) {
            Contract contract = contracts.get(id);
            if (contract != null && contract.getStatus() == Contract.Status.ACTIVE) {
                result.add(contract);
            }
        }
        return result;
    }

    /**
     * Get contracts open for bidding
     */
    public List<Contract> getOpenForBidding(UUID nationId) {
        return getNationContractsByStatus(nationId, Contract.Status.BIDDING);
    }

    /**
     * Get contracts pending legislature approval
     */
    public List<Contract> getPendingApproval(UUID nationId) {
        return getNationContractsByStatus(nationId, Contract.Status.PENDING_APPROVAL);
    }

    /**
     * Get active contracts
     */
    public List<Contract> getActiveContracts(UUID nationId) {
        return getNationContractsByStatus(nationId, Contract.Status.ACTIVE);
    }

    /**
     * Get completed contracts
     */
    public List<Contract> getCompletedContracts(UUID nationId) {
        List<Contract> result = new ArrayList<>();
        for (Contract contract : getNationContracts(nationId)) {
            if (contract.getStatus() == Contract.Status.COMPLETED ||
                contract.getStatus() == Contract.Status.CANCELLED ||
                contract.getStatus() == Contract.Status.FAILED) {
                result.add(contract);
            }
        }
        return result;
    }

    // ==================== Contract Actions ====================

    /**
     * Open a contract for bidding
     */
    public boolean openForBidding(UUID contractId, long biddingDurationMs) {
        Contract contract = contracts.get(contractId);
        if (contract == null) return false;

        boolean success = contract.openForBidding(biddingDurationMs);
        if (success) {
            dirty = true;
        }
        return success;
    }

    /**
     * Submit a bid on a contract
     */
    public boolean submitBid(UUID contractId, ContractBid bid) {
        Contract contract = contracts.get(contractId);
        if (contract == null) return false;

        boolean success = contract.submitBid(bid);
        if (success) {
            dirty = true;
        }
        return success;
    }

    /**
     * Close bidding on a contract
     */
    public boolean closeBidding(UUID contractId) {
        Contract contract = contracts.get(contractId);
        if (contract == null) return false;

        boolean success = contract.closeBidding();
        if (success) {
            dirty = true;
        }
        return success;
    }

    /**
     * Approve a bid (legislature action)
     */
    public boolean approveBid(UUID contractId, UUID bidId, long deadlineDurationMs) {
        Contract contract = contracts.get(contractId);
        if (contract == null) return false;

        boolean success = contract.approveBid(bidId, deadlineDurationMs);
        if (success) {
            // Index the contractor
            contractorContracts.computeIfAbsent(contract.getContractorId(), k -> new HashSet<>())
                .add(contractId);
            dirty = true;
        }
        return success;
    }

    /**
     * Update contract progress
     */
    public void updateProgress(UUID contractId, int percent) {
        Contract contract = contracts.get(contractId);
        if (contract != null) {
            contract.updateProgress(percent);
            dirty = true;
        }
    }

    /**
     * Complete a milestone
     */
    public boolean completeMilestone(UUID contractId, int milestonePercent) {
        Contract contract = contracts.get(contractId);
        if (contract == null) return false;

        boolean success = contract.completeMilestone(milestonePercent);
        if (success) {
            dirty = true;
        }
        return success;
    }

    /**
     * Complete a contract
     */
    public boolean completeContract(UUID contractId) {
        Contract contract = contracts.get(contractId);
        if (contract == null) return false;

        boolean success = contract.complete();
        if (success) {
            // Remove from contractor index
            if (contract.getContractorId() != null) {
                Set<UUID> ids = contractorContracts.get(contract.getContractorId());
                if (ids != null) {
                    ids.remove(contractId);
                }
            }
            dirty = true;
        }
        return success;
    }

    /**
     * Cancel a contract
     */
    public boolean cancelContract(UUID contractId) {
        Contract contract = contracts.get(contractId);
        if (contract == null) return false;

        boolean success = contract.cancel();
        if (success) {
            // Remove from contractor index
            if (contract.getContractorId() != null) {
                Set<UUID> ids = contractorContracts.get(contract.getContractorId());
                if (ids != null) {
                    ids.remove(contractId);
                }
            }
            dirty = true;
        }
        return success;
    }

    /**
     * Fail a contract (contractor didn't complete)
     */
    public boolean failContract(UUID contractId) {
        Contract contract = contracts.get(contractId);
        if (contract == null) return false;

        boolean success = contract.fail();
        if (success) {
            // Remove from contractor index
            if (contract.getContractorId() != null) {
                Set<UUID> ids = contractorContracts.get(contract.getContractorId());
                if (ids != null) {
                    ids.remove(contractId);
                }
            }
            dirty = true;
        }
        return success;
    }

    /**
     * Delete a contract (only if in DRAFT status)
     */
    public boolean deleteContract(UUID contractId) {
        Contract contract = contracts.get(contractId);
        if (contract == null) return false;
        if (contract.getStatus() != Contract.Status.DRAFT) return false;

        contracts.remove(contractId);
        Set<UUID> nationIds = nationContracts.get(contract.getNationId());
        if (nationIds != null) {
            nationIds.remove(contractId);
        }

        dirty = true;
        return true;
    }

    /**
     * Admin: Force remove a contract regardless of status
     */
    public boolean removeContract(UUID contractId) {
        Contract contract = contracts.get(contractId);
        if (contract == null) return false;

        contracts.remove(contractId);

        // Remove from nation index
        Set<UUID> nationIds = nationContracts.get(contract.getNationId());
        if (nationIds != null) {
            nationIds.remove(contractId);
        }

        // Remove from contractor index
        if (contract.getContractorId() != null) {
            Set<UUID> contractorIds = contractorContracts.get(contract.getContractorId());
            if (contractorIds != null) {
                contractorIds.remove(contractId);
            }
        }

        dirty = true;
        return true;
    }

    // ==================== Tick Processing ====================

    /**
     * Process expired bidding periods
     * Should be called periodically (e.g., every minute)
     * @return List of contracts whose bidding just closed (for notifications)
     */
    public java.util.List<Contract> processBiddingExpirations() {
        java.util.List<Contract> closedContracts = new java.util.ArrayList<>();
        for (Contract contract : contracts.values()) {
            if (contract.isBiddingExpired()) {
                contract.closeBidding();
                closedContracts.add(contract);
                dirty = true;
            }
        }
        return closedContracts;
    }

    /**
     * Get all contracts that are past their deadline
     * These require legislative review but don't auto-fail
     */
    public java.util.List<Contract> getOverdueContracts() {
        java.util.List<Contract> overdueContracts = new java.util.ArrayList<>();
        for (Contract contract : contracts.values()) {
            if (contract.isOverdue()) {
                overdueContracts.add(contract);
            }
        }
        return overdueContracts;
    }

    /**
     * Process overdue contracts and return those that are newly overdue (for notification)
     * Marks contracts as notified to avoid repeat notifications
     */
    public java.util.List<Contract> processOverdueContracts() {
        java.util.List<Contract> newlyOverdueContracts = new java.util.ArrayList<>();
        for (Contract contract : contracts.values()) {
            if (contract.isOverdue() && !contract.isOverdueNotificationSent()) {
                contract.setOverdueNotificationSent(true);
                newlyOverdueContracts.add(contract);
                dirty = true;
            }
        }
        return newlyOverdueContracts;
    }

    /**
     * Notification data for deadline warnings
     */
    public static class DeadlineWarning {
        public final Contract contract;
        public final long timeRemaining;  // ms until deadline
        public final String warningType;  // "1_DAY" or "1_HOUR"

        public DeadlineWarning(Contract contract, long timeRemaining, String warningType) {
            this.contract = contract;
            this.timeRemaining = timeRemaining;
            this.warningType = warningType;
        }
    }

    /**
     * Process deadline warnings for active contracts
     * @return List of contracts approaching their deadline (for notifications)
     */
    public java.util.List<DeadlineWarning> processDeadlineWarnings() {
        java.util.List<DeadlineWarning> warnings = new java.util.ArrayList<>();
        long oneDayMs = 24 * 60 * 60 * 1000;
        long oneHourMs = 60 * 60 * 1000;

        for (Contract contract : contracts.values()) {
            if (contract.getStatus() != Contract.Status.ACTIVE) continue;

            long timeRemaining = contract.getDeadlineTimeRemaining();
            if (timeRemaining <= 0) continue;  // Already overdue, handled separately

            // 1 hour warning
            if (timeRemaining <= oneHourMs && !contract.isDeadlineWarning1HourSent()) {
                contract.setDeadlineWarning1HourSent(true);
                warnings.add(new DeadlineWarning(contract, timeRemaining, "1_HOUR"));
                dirty = true;
            }
            // 1 day warning (only if 1 hour hasn't been sent yet to avoid duplicate)
            else if (timeRemaining <= oneDayMs && timeRemaining > oneHourMs && !contract.isDeadlineWarning1DaySent()) {
                contract.setDeadlineWarning1DaySent(true);
                warnings.add(new DeadlineWarning(contract, timeRemaining, "1_DAY"));
                dirty = true;
            }
        }
        return warnings;
    }

    /**
     * Notification data for bidding ending warnings
     */
    public static class BiddingEndingWarning {
        public final Contract contract;
        public final long timeRemaining;  // ms until bidding ends
        public final String warningType;  // "1_DAY" or "1_HOUR"

        public BiddingEndingWarning(Contract contract, long timeRemaining, String warningType) {
            this.contract = contract;
            this.timeRemaining = timeRemaining;
            this.warningType = warningType;
        }
    }

    /**
     * Process bidding ending warnings for contracts open for bidding
     * @return List of contracts whose bidding is about to end (for notifications)
     */
    public java.util.List<BiddingEndingWarning> processBiddingEndingWarnings() {
        java.util.List<BiddingEndingWarning> warnings = new java.util.ArrayList<>();
        long oneDayMs = 24 * 60 * 60 * 1000;
        long oneHourMs = 60 * 60 * 1000;

        for (Contract contract : contracts.values()) {
            if (contract.getStatus() != Contract.Status.BIDDING) continue;

            long timeRemaining = contract.getBiddingTimeRemaining();
            if (timeRemaining <= 0) continue;  // Already closed, handled separately

            // 1 hour warning
            if (timeRemaining <= oneHourMs && !contract.isBiddingEnding1HourSent()) {
                contract.setBiddingEnding1HourSent(true);
                warnings.add(new BiddingEndingWarning(contract, timeRemaining, "1_HOUR"));
                dirty = true;
            }
            // 1 day warning
            else if (timeRemaining <= oneDayMs && timeRemaining > oneHourMs && !contract.isBiddingEnding1DaySent()) {
                contract.setBiddingEnding1DaySent(true);
                warnings.add(new BiddingEndingWarning(contract, timeRemaining, "1_DAY"));
                dirty = true;
            }
        }
        return warnings;
    }

    /**
     * Get contracts with pending milestone approval requests
     * Used for notifying legislature of pending approvals
     */
    public java.util.List<Contract> getContractsWithPendingMilestones() {
        java.util.List<Contract> contractsWithPending = new java.util.ArrayList<>();
        for (Contract contract : contracts.values()) {
            if (contract.getStatus() == Contract.Status.ACTIVE &&
                !contract.getMilestoneApprovalRequests().isEmpty()) {
                contractsWithPending.add(contract);
            }
        }
        return contractsWithPending;
    }    // ==================== NBT Serialization ====================

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();

        // Save contracts
        ListTag contractsTag = new ListTag();
        for (Contract contract : contracts.values()) {
            contractsTag.add(contract.save());
        }
        tag.put("contracts", contractsTag);

        // Save next contract numbers
        CompoundTag numbersTag = new CompoundTag();
        for (Map.Entry<UUID, Integer> entry : nextContractNumber.entrySet()) {
            numbersTag.putInt(entry.getKey().toString(), entry.getValue());
        }
        tag.put("nextContractNumbers", numbersTag);
        tag.putInt("currentYear", currentYear);

        return tag;
    }

    public void load(CompoundTag tag) {
        contracts.clear();
        nationContracts.clear();
        contractorContracts.clear();
        nextContractNumber.clear();

        // Load contracts
        ListTag contractsTag = tag.getList("contracts", Tag.TAG_COMPOUND);
        for (int i = 0; i < contractsTag.size(); i++) {
            Contract contract = Contract.load(contractsTag.getCompound(i));
            contracts.put(contract.getContractId(), contract);

            // Index by nation
            nationContracts.computeIfAbsent(contract.getNationId(), k -> new HashSet<>())
                .add(contract.getContractId());

            // Index by contractor if active
            if (contract.getStatus() == Contract.Status.ACTIVE && contract.getContractorId() != null) {
                contractorContracts.computeIfAbsent(contract.getContractorId(), k -> new HashSet<>())
                    .add(contract.getContractId());
            }
        }

        // Load next contract numbers
        CompoundTag numbersTag = tag.getCompound("nextContractNumbers");
        for (String key : numbersTag.getAllKeys()) {
            nextContractNumber.put(UUID.fromString(key), numbersTag.getInt(key));
        }

        currentYear = tag.contains("currentYear") ? tag.getInt("currentYear") : java.time.LocalDate.now().getYear();

        dirty = false;
    }
}


