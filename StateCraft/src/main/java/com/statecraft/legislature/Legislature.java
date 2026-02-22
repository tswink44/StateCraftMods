package com.statecraft.legislature;

import com.statecraft.core.Nation;
import com.statecraft.core.State;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.*;

/**
 * Represents the legislature of a single nation
 * Manages active bills, voting members, and emergency powers
 */
public class Legislature {

    private final UUID nationId;
    private final Map<UUID, Bill> activeBills;  // billId -> Bill
    private final Map<UUID, Bill> draftBills;   // billId -> Bill (drafts being written)
    private final List<Bill> billHistory;       // Completed/failed bills
    private final LawCodex codex;

    // Emergency powers tracking
    private final Map<EmergencyPower, Long> emergencyPowerCooldowns;  // power -> end time
    private final Map<EmergencyPower, Long> activeEmergencyPowers;    // power -> end time (0 = permanent)

    // Bill numbering
    private int nextBillNumber;
    private int currentYear;

    public Legislature(UUID nationId) {
        this.nationId = nationId;
        this.activeBills = new HashMap<>();
        this.draftBills = new HashMap<>();
        this.billHistory = new ArrayList<>();
        this.codex = new LawCodex(nationId);
        this.emergencyPowerCooldowns = new HashMap<>();
        this.activeEmergencyPowers = new HashMap<>();
        this.nextBillNumber = 1;
        this.currentYear = java.time.LocalDate.now().getYear();
    }

    public UUID getNationId() { return nationId; }
    public LawCodex getCodex() { return codex; }

    /**
     * Get all legislature members who can vote
     * Includes: All State Governors + Nation Officers
     * Excludes: Nation Leader (unless the nation has fewer than 3 total members,
     *           in which case the leader can also vote and propose legislation)
     */
    public Set<UUID> getVotingMembers(Nation nation) {
        Set<UUID> members = new HashSet<>();
        UUID leaderId = nation.getLeaderId();

        // Add all governors
        for (State state : nation.getAllStates()) {
            UUID governorId = state.getGovernorId();
            // Governors can vote, but Leader cannot vote even if they're a Governor
            // (unless nation is small - handled below)
            if (!governorId.equals(leaderId)) {
                members.add(governorId);
            }
        }

        // Add all officers (but not if they're the leader)
        for (UUID officerId : nation.getOfficers()) {
            if (!officerId.equals(leaderId)) {
                members.add(officerId);
            }
        }

        // If the nation has fewer than 3 members, the leader can also vote and propose legislation
        if (nation.getAllMembers().size() < 3) {
            members.add(leaderId);
        }

        return members;
    }

    /**
     * Check if a player can propose bills (must be a voting member or the nation leader)
     */
    public boolean canProposeBill(Nation nation, UUID playerId) {
        // Nation leader can propose bills
        if (playerId.equals(nation.getLeaderId())) {
            return true;
        }
        return getVotingMembers(nation).contains(playerId);
    }

    /**
     * Generate the next bill number
     */
    public String generateBillNumber() {
        int year = java.time.LocalDate.now().getYear();
        if (year != currentYear) {
            currentYear = year;
            nextBillNumber = 1;
        }
        return String.format("BILL-%d-%03d", year, nextBillNumber++);
    }

    /**
     * Generate a constitutional amendment number
     */
    public String generateAmendmentNumber() {
        int year = java.time.LocalDate.now().getYear();
        if (year != currentYear) {
            currentYear = year;
            nextBillNumber = 1;
        }
        return String.format("AMEND-%d-%03d", year, nextBillNumber++);
    }

    /**
     * Create a new draft bill
     */
    public Bill createDraft(UUID authorId, String authorName, String title, String description) {
        String billNumber = generateBillNumber();
        Bill bill = new Bill(nationId, billNumber, title, description, authorId, authorName);
        draftBills.put(bill.getBillId(), bill);
        return bill;
    }

    /**
     * Create a new draft constitutional amendment
     * Constitutional amendments require 2/3 majority to pass and cannot be vetoed
     */
    public Bill createConstitutionalAmendment(UUID authorId, String authorName, String title, String description) {
        String billNumber = generateAmendmentNumber();
        Bill bill = new Bill(nationId, billNumber, title, description, authorId, authorName, Bill.BillType.CONSTITUTIONAL);
        draftBills.put(bill.getBillId(), bill);
        return bill;
    }

    /**
     * Get a draft bill
     */
    public Bill getDraft(UUID billId) {
        return draftBills.get(billId);
    }

    /**
     * Add a bill to the drafts (used for system-generated bills)
     */
    public void addDraftBill(Bill bill) {
        draftBills.put(bill.getBillId(), bill);
    }

    /**
     * Submit a draft for debate
     */
    public boolean submitForDebate(UUID billId, long debateDurationMs) {
        Bill bill = draftBills.remove(billId);
        if (bill == null || bill.getPolicyChanges().isEmpty()) {
            return false;
        }

        bill.submitForDebate(System.currentTimeMillis() + debateDurationMs);
        activeBills.put(bill.getBillId(), bill);
        return true;
    }

    /**
     * Get an active bill
     */
    public Bill getActiveBill(UUID billId) {
        return activeBills.get(billId);
    }

    /**
     * Add an active bill directly (used for system-generated bills like ratification votes)
     */
    public void addActiveBill(Bill bill) {
        activeBills.put(bill.getBillId(), bill);
    }

    /**
     * Get all active bills
     */
    public Collection<Bill> getActiveBills() {
        return Collections.unmodifiableCollection(activeBills.values());
    }

    /**
     * Get bills in voting phase
     */
    public List<Bill> getVotingBills() {
        List<Bill> voting = new ArrayList<>();
        for (Bill bill : activeBills.values()) {
            if (bill.getStatus() == Bill.Status.VOTING) {
                voting.add(bill);
            }
        }
        return voting;
    }

    /**
     * Get bills in debate phase
     */
    public List<Bill> getDebatingBills() {
        List<Bill> debating = new ArrayList<>();
        for (Bill bill : activeBills.values()) {
            if (bill.getStatus() == Bill.Status.DEBATE) {
                debating.add(bill);
            }
        }
        return debating;
    }

    /**
     * Get bills awaiting leader action (passed but not enacted/vetoed)
     */
    public List<Bill> getPendingLeaderAction() {
        List<Bill> pending = new ArrayList<>();
        for (Bill bill : activeBills.values()) {
            if (bill.getStatus() == Bill.Status.PASSED && !bill.isVetoProof()) {
                pending.add(bill);
            }
        }
        return pending;
    }

    /**
     * Move a bill from active to history
     */
    public void archiveBill(UUID billId) {
        Bill bill = activeBills.remove(billId);
        if (bill != null) {
            billHistory.add(0, bill);  // Add to front (most recent first)
            // Keep only last 100 bills in history
            while (billHistory.size() > 100) {
                billHistory.remove(billHistory.size() - 1);
            }
        }
    }

    /**
     * Get bill history
     */
    public List<Bill> getBillHistory() {
        return Collections.unmodifiableList(billHistory);
    }

    // Emergency power methods

    /**
     * Check if an emergency power is on cooldown
     */
    public boolean isOnCooldown(EmergencyPower power) {
        Long cooldownEnd = emergencyPowerCooldowns.get(power);
        return cooldownEnd != null && System.currentTimeMillis() < cooldownEnd;
    }

    /**
     * Get remaining cooldown time in milliseconds
     */
    public long getCooldownRemaining(EmergencyPower power) {
        Long cooldownEnd = emergencyPowerCooldowns.get(power);
        if (cooldownEnd == null) return 0;
        return Math.max(0, cooldownEnd - System.currentTimeMillis());
    }

    /**
     * Check if an emergency power is currently active
     */
    public boolean isEmergencyPowerActive(EmergencyPower power) {
        Long endTime = activeEmergencyPowers.get(power);
        if (endTime == null) return false;
        if (endTime == 0) return true;  // Permanent
        return System.currentTimeMillis() < endTime;
    }

    /**
     * Activate an emergency power
     */
    public boolean activateEmergencyPower(EmergencyPower power) {
        if (isOnCooldown(power)) return false;

        long endTime = power.isPermanent() ? 0 :
            System.currentTimeMillis() + (power.getDurationHours() * 60 * 60 * 1000L);
        activeEmergencyPowers.put(power, endTime);

        return true;
    }

    /**
     * Deactivate an emergency power and start cooldown
     */
    public void deactivateEmergencyPower(EmergencyPower power) {
        activeEmergencyPowers.remove(power);

        if (power.getCooldownDays() > 0) {
            long cooldownEnd = System.currentTimeMillis() +
                (power.getCooldownDays() * 24 * 60 * 60 * 1000L);
            emergencyPowerCooldowns.put(power, cooldownEnd);
        }
    }

    /**
     * Get all active emergency powers
     */
    public Map<EmergencyPower, Long> getActiveEmergencyPowers() {
        // Clean up expired powers
        activeEmergencyPowers.entrySet().removeIf(entry -> {
            if (entry.getValue() == 0) return false;  // Permanent
            return System.currentTimeMillis() >= entry.getValue();
        });
        return Collections.unmodifiableMap(activeEmergencyPowers);
    }

    // NBT Serialization
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("nationId", nationId);
        tag.putInt("nextBillNumber", nextBillNumber);
        tag.putInt("currentYear", currentYear);

        // Save active bills
        ListTag activeBillsList = new ListTag();
        for (Bill bill : activeBills.values()) {
            activeBillsList.add(bill.save());
        }
        tag.put("activeBills", activeBillsList);

        // Save draft bills
        ListTag draftBillsList = new ListTag();
        for (Bill bill : draftBills.values()) {
            draftBillsList.add(bill.save());
        }
        tag.put("draftBills", draftBillsList);

        // Save bill history
        ListTag historyList = new ListTag();
        for (Bill bill : billHistory) {
            historyList.add(bill.save());
        }
        tag.put("billHistory", historyList);

        // Save codex
        tag.put("codex", codex.save());

        // Save emergency power cooldowns
        ListTag cooldownsList = new ListTag();
        for (Map.Entry<EmergencyPower, Long> entry : emergencyPowerCooldowns.entrySet()) {
            CompoundTag cooldownTag = new CompoundTag();
            cooldownTag.putString("power", entry.getKey().name());
            cooldownTag.putLong("endTime", entry.getValue());
            cooldownsList.add(cooldownTag);
        }
        tag.put("emergencyCooldowns", cooldownsList);

        // Save active emergency powers
        ListTag activePowersList = new ListTag();
        for (Map.Entry<EmergencyPower, Long> entry : activeEmergencyPowers.entrySet()) {
            CompoundTag powerTag = new CompoundTag();
            powerTag.putString("power", entry.getKey().name());
            powerTag.putLong("endTime", entry.getValue());
            activePowersList.add(powerTag);
        }
        tag.put("activeEmergencyPowers", activePowersList);

        return tag;
    }

    public static Legislature load(CompoundTag tag) {
        UUID nationId = tag.getUUID("nationId");
        Legislature legislature = new Legislature(nationId);
        legislature.nextBillNumber = tag.getInt("nextBillNumber");
        legislature.currentYear = tag.getInt("currentYear");

        // Load active bills
        ListTag activeBillsList = tag.getList("activeBills", Tag.TAG_COMPOUND);
        for (int i = 0; i < activeBillsList.size(); i++) {
            Bill bill = Bill.load(activeBillsList.getCompound(i));
            legislature.activeBills.put(bill.getBillId(), bill);
        }

        // Load draft bills
        ListTag draftBillsList = tag.getList("draftBills", Tag.TAG_COMPOUND);
        for (int i = 0; i < draftBillsList.size(); i++) {
            Bill bill = Bill.load(draftBillsList.getCompound(i));
            legislature.draftBills.put(bill.getBillId(), bill);
        }

        // Load bill history
        ListTag historyList = tag.getList("billHistory", Tag.TAG_COMPOUND);
        for (int i = 0; i < historyList.size(); i++) {
            legislature.billHistory.add(Bill.load(historyList.getCompound(i)));
        }

        // Load codex (replace the auto-created one)
        if (tag.contains("codex")) {
            // Need to load into existing codex or replace
            LawCodex loadedCodex = LawCodex.load(tag.getCompound("codex"));
            // Copy laws from loaded codex
            for (Law law : loadedCodex.getAllLaws()) {
                legislature.codex.addLaw(law);
            }
        }

        // Load emergency power cooldowns
        ListTag cooldownsList = tag.getList("emergencyCooldowns", Tag.TAG_COMPOUND);
        for (int i = 0; i < cooldownsList.size(); i++) {
            CompoundTag cooldownTag = cooldownsList.getCompound(i);
            try {
                EmergencyPower power = EmergencyPower.valueOf(cooldownTag.getString("power"));
                legislature.emergencyPowerCooldowns.put(power, cooldownTag.getLong("endTime"));
            } catch (IllegalArgumentException ignored) {}
        }

        // Load active emergency powers
        ListTag activePowersList = tag.getList("activeEmergencyPowers", Tag.TAG_COMPOUND);
        for (int i = 0; i < activePowersList.size(); i++) {
            CompoundTag powerTag = activePowersList.getCompound(i);
            try {
                EmergencyPower power = EmergencyPower.valueOf(powerTag.getString("power"));
                legislature.activeEmergencyPowers.put(power, powerTag.getLong("endTime"));
            } catch (IllegalArgumentException ignored) {}
        }

        return legislature;
    }
}

