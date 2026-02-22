package com.statecraft.legislature;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.time.LocalDate;
import java.util.*;

/**
 * Represents a legislative bill proposed in the nation legislature
 * Bills can be amended during debate period (max 3 amendments)
 */
public class Bill {

    public enum Status {
        DRAFT,      // Being written, not yet submitted
        DEBATE,     // Open for discussion and amendments
        VOTING,     // Voting period active
        PASSED,     // Passed by legislature, awaiting Leader action
        VETOED,     // Vetoed by Leader
        ENACTED,    // Became law
        FAILED,     // Did not pass vote
        EXPIRED     // Timed out without action
    }

    public enum Vote {
        YES, NO, ABSTAIN
    }

    /**
     * Type of bill - affects voting requirements and veto rules
     */
    public enum BillType {
        REGULAR,            // Standard bill: simple majority, can be vetoed
        CONSTITUTIONAL,     // Constitutional amendment: 2/3 majority required, cannot be vetoed
        EMERGENCY_RATIFICATION  // Ratification of emergency power: simple majority, cannot be vetoed by leader
    }

    private final UUID billId;
    private final UUID nationId;
    private final String billNumber;  // e.g., "NL-2026-001"
    private BillType billType;        // Regular or Constitutional Amendment
    private String title;
    private String description;
    private UUID authorId;
    private String authorName;
    private long createdTime;

    // Policy changes this bill would enact
    private final Map<PolicyType, String> policyChanges;

    // Amendments (max 3)
    private final List<Amendment> amendments;
    private static final int MAX_AMENDMENTS = 3;

    // Voting
    private final Map<UUID, Vote> votes;
    private Status status;
    private long debateEndTime;
    private long voteEndTime;
    private long enactedTime;

    // Results
    private boolean vetoProof;  // Passed with 2/3+ majority
    private int yesVotes;
    private int noVotes;
    private int abstainVotes;

    public Bill(UUID nationId, String billNumber, String title, String description,
                UUID authorId, String authorName) {
        this(nationId, billNumber, title, description, authorId, authorName, BillType.REGULAR);
    }

    public Bill(UUID nationId, String billNumber, String title, String description,
                UUID authorId, String authorName, BillType billType) {
        this.billId = UUID.randomUUID();
        this.nationId = nationId;
        this.billNumber = billNumber;
        this.billType = billType;
        this.title = title;
        this.description = description;
        this.authorId = authorId;
        this.authorName = authorName;
        this.createdTime = System.currentTimeMillis();
        this.policyChanges = new HashMap<>();
        this.amendments = new ArrayList<>();
        this.votes = new HashMap<>();
        this.status = Status.DRAFT;
    }

    // Private constructor for NBT loading
    private Bill(UUID billId, UUID nationId, String billNumber) {
        this.billId = billId;
        this.nationId = nationId;
        this.billNumber = billNumber;
        this.billType = BillType.REGULAR;  // Default, will be overwritten by load()
        this.authorId = null;
        this.authorName = "";
        this.createdTime = 0;
        this.policyChanges = new HashMap<>();
        this.amendments = new ArrayList<>();
        this.votes = new HashMap<>();
    }

    // Getters
    public UUID getBillId() { return billId; }
    public UUID getNationId() { return nationId; }
    public String getBillNumber() { return billNumber; }
    public BillType getBillType() { return billType; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public UUID getAuthorId() { return authorId; }
    public String getAuthorName() { return authorName; }
    public long getCreatedTime() { return createdTime; }
    public Status getStatus() { return status; }
    public long getDebateEndTime() { return debateEndTime; }
    public long getVoteEndTime() { return voteEndTime; }
    public long getEnactedTime() { return enactedTime; }
    public boolean isVetoProof() { return vetoProof; }
    public int getYesVotes() { return yesVotes; }
    public int getNoVotes() { return noVotes; }
    public int getAbstainVotes() { return abstainVotes; }

    /**
     * Check if this bill is a constitutional amendment
     */
    public boolean isConstitutionalAmendment() {
        return billType == BillType.CONSTITUTIONAL;
    }

    /**
     * Check if this bill is an emergency power ratification vote
     */
    public boolean isEmergencyRatification() {
        return billType == BillType.EMERGENCY_RATIFICATION;
    }

    /**
     * Check if this bill can be vetoed
     * Constitutional amendments and emergency ratification votes cannot be vetoed
     */
    public boolean canBeVetoed() {
        return billType == BillType.REGULAR && !vetoProof;
    }

    public Map<PolicyType, String> getPolicyChanges() {
        return Collections.unmodifiableMap(policyChanges);
    }

    public List<Amendment> getAmendments() {
        return Collections.unmodifiableList(amendments);
    }

    public Map<UUID, Vote> getVotes() {
        return Collections.unmodifiableMap(votes);
    }

    // Setters for draft stage
    public void setTitle(String title) {
        if (status == Status.DRAFT) {
            this.title = title;
        }
    }

    public void setDescription(String description) {
        if (status == Status.DRAFT || status == Status.DEBATE) {
            this.description = description;
        }
    }

    public void addPolicyChange(PolicyType policy, String value) {
        if (status == Status.DRAFT && policy.isValidValue(value)) {
            policyChanges.put(policy, value);
        }
    }

    public void removePolicyChange(PolicyType policy) {
        if (status == Status.DRAFT) {
            policyChanges.remove(policy);
        }
    }

    // Amendment methods
    public boolean canAmend() {
        return status == Status.DEBATE && amendments.size() < MAX_AMENDMENTS;
    }

    public boolean addAmendment(UUID authorId, String authorName, String text) {
        if (!canAmend()) return false;

        int number = amendments.size() + 1;
        amendments.add(new Amendment(number, authorId, authorName, text, System.currentTimeMillis()));
        return true;
    }

    // Status transitions
    public void submitForDebate(long debateEndTime) {
        if (status == Status.DRAFT && !policyChanges.isEmpty()) {
            this.status = Status.DEBATE;
            this.debateEndTime = debateEndTime;
        }
    }

    public void startVoting(long voteEndTime) {
        if (status == Status.DEBATE) {
            this.status = Status.VOTING;
            this.voteEndTime = voteEndTime;
        }
    }

    public boolean castVote(UUID voterId, Vote vote) {
        if (status != Status.VOTING) return false;
        if (votes.containsKey(voterId)) return false;  // Already voted

        votes.put(voterId, vote);
        return true;
    }

    /**
     * Tally votes and determine outcome
     * Regular bills: simple majority (>50%), can be vetoed unless veto-proof
     * Constitutional amendments: require 2/3 majority (67%), cannot be vetoed
     *
     * @param totalEligibleVoters Total legislature members who could vote
     * @param quorumPercent Minimum participation required (0-100)
     * @param vetoOverridePercent Percentage for veto-proof majority (0-100)
     */
    public void tallyVotes(int totalEligibleVoters, int quorumPercent, int vetoOverridePercent) {
        if (status != Status.VOTING) return;

        yesVotes = 0;
        noVotes = 0;
        abstainVotes = 0;

        for (Vote vote : votes.values()) {
            switch (vote) {
                case YES -> yesVotes++;
                case NO -> noVotes++;
                case ABSTAIN -> abstainVotes++;
            }
        }

        // Non-voters count as abstain for quorum but not for majority
        int totalVotes = yesVotes + noVotes + abstainVotes;
        int participation = totalEligibleVoters > 0 ? (totalVotes * 100) / totalEligibleVoters : 0;

        // Check quorum (abstains count toward participation)
        if (participation < quorumPercent) {
            status = Status.FAILED;
            return;
        }

        // Calculate majority based on YES vs NO only (abstains don't count)
        int decisiveVotes = yesVotes + noVotes;
        if (decisiveVotes == 0) {
            status = Status.FAILED;
            return;
        }

        int yesPercent = (yesVotes * 100) / decisiveVotes;

        // Constitutional amendments require 2/3 majority (67%)
        if (billType == BillType.CONSTITUTIONAL) {
            int constitutionalThreshold = 67; // 2/3 majority
            if (yesPercent >= constitutionalThreshold) {
                // Constitutional amendments pass directly to ENACTED (cannot be vetoed)
                status = Status.ENACTED;
                enactedTime = System.currentTimeMillis();
                vetoProof = true; // Mark as veto-proof for records
            } else {
                status = Status.FAILED;
            }
        } else if (billType == BillType.EMERGENCY_RATIFICATION) {
            // Emergency ratification: simple majority, bypasses leader (goes directly to ENACTED/FAILED)
            if (yesPercent > 50) {
                status = Status.ENACTED;
                enactedTime = System.currentTimeMillis();
                vetoProof = true; // Cannot be vetoed
            } else {
                status = Status.FAILED;
            }
        } else {
            // Regular bills: simple majority
            if (yesPercent > 50) {
                status = Status.PASSED;
                vetoProof = yesPercent >= vetoOverridePercent;
            } else {
                status = Status.FAILED;
            }
        }
    }

    public void veto() {
        // Constitutional amendments cannot be vetoed
        if (billType == BillType.CONSTITUTIONAL) return;
        if (status == Status.PASSED && !vetoProof) {
            status = Status.VETOED;
        }
    }

    public void enact() {
        if (status == Status.PASSED) {
            status = Status.ENACTED;
            enactedTime = System.currentTimeMillis();
        }
    }

    public void expire() {
        if (status == Status.DRAFT || status == Status.DEBATE || status == Status.PASSED) {
            status = Status.EXPIRED;
        }
    }

    // NBT Serialization
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("billId", billId);
        tag.putUUID("nationId", nationId);
        tag.putString("billNumber", billNumber);
        tag.putString("billType", billType.name());
        tag.putString("title", title);
        tag.putString("description", description);
        if (authorId != null) {
            tag.putUUID("authorId", authorId);
        }
        tag.putString("authorName", authorName);
        tag.putLong("createdTime", createdTime);
        tag.putString("status", status.name());
        tag.putLong("debateEndTime", debateEndTime);
        tag.putLong("voteEndTime", voteEndTime);
        tag.putLong("enactedTime", enactedTime);
        tag.putBoolean("vetoProof", vetoProof);
        tag.putInt("yesVotes", yesVotes);
        tag.putInt("noVotes", noVotes);
        tag.putInt("abstainVotes", abstainVotes);

        // Policy changes
        ListTag policiesList = new ListTag();
        for (Map.Entry<PolicyType, String> entry : policyChanges.entrySet()) {
            CompoundTag policyTag = new CompoundTag();
            policyTag.putString("policy", entry.getKey().name());
            policyTag.putString("value", entry.getValue());
            policiesList.add(policyTag);
        }
        tag.put("policyChanges", policiesList);

        // Amendments
        ListTag amendmentsList = new ListTag();
        for (Amendment amendment : amendments) {
            amendmentsList.add(amendment.save());
        }
        tag.put("amendments", amendmentsList);

        // Votes
        ListTag votesList = new ListTag();
        for (Map.Entry<UUID, Vote> entry : votes.entrySet()) {
            CompoundTag voteTag = new CompoundTag();
            voteTag.putUUID("voterId", entry.getKey());
            voteTag.putString("vote", entry.getValue().name());
            votesList.add(voteTag);
        }
        tag.put("votes", votesList);

        return tag;
    }

    public static Bill load(CompoundTag tag) {
        UUID billId = tag.getUUID("billId");
        UUID nationId = tag.getUUID("nationId");
        String billNumber = tag.getString("billNumber");

        Bill bill = new Bill(billId, nationId, billNumber);

        // Load bill type (default to REGULAR for backwards compatibility)
        if (tag.contains("billType")) {
            try {
                bill.billType = BillType.valueOf(tag.getString("billType"));
            } catch (IllegalArgumentException e) {
                bill.billType = BillType.REGULAR;
            }
        } else {
            bill.billType = BillType.REGULAR;
        }

        bill.title = tag.getString("title");
        bill.description = tag.getString("description");

        // Load authorId if present
        if (tag.contains("authorId")) {
            bill.authorId = tag.getUUID("authorId");
        }
        bill.authorName = tag.getString("authorName");
        bill.createdTime = tag.getLong("createdTime");

        bill.status = Status.valueOf(tag.getString("status"));
        bill.debateEndTime = tag.getLong("debateEndTime");
        bill.voteEndTime = tag.getLong("voteEndTime");
        bill.enactedTime = tag.getLong("enactedTime");
        bill.vetoProof = tag.getBoolean("vetoProof");
        bill.yesVotes = tag.getInt("yesVotes");
        bill.noVotes = tag.getInt("noVotes");
        bill.abstainVotes = tag.getInt("abstainVotes");

        // Policy changes
        ListTag policiesList = tag.getList("policyChanges", Tag.TAG_COMPOUND);
        for (int i = 0; i < policiesList.size(); i++) {
            CompoundTag policyTag = policiesList.getCompound(i);
            try {
                PolicyType policy = PolicyType.valueOf(policyTag.getString("policy"));
                String value = policyTag.getString("value");
                bill.policyChanges.put(policy, value);
            } catch (IllegalArgumentException ignored) {}
        }

        // Amendments
        ListTag amendmentsList = tag.getList("amendments", Tag.TAG_COMPOUND);
        for (int i = 0; i < amendmentsList.size(); i++) {
            bill.amendments.add(Amendment.load(amendmentsList.getCompound(i)));
        }

        // Votes
        ListTag votesList = tag.getList("votes", Tag.TAG_COMPOUND);
        for (int i = 0; i < votesList.size(); i++) {
            CompoundTag voteTag = votesList.getCompound(i);
            UUID voterId = voteTag.getUUID("voterId");
            Vote vote = Vote.valueOf(voteTag.getString("vote"));
            bill.votes.put(voterId, vote);
        }

        return bill;
    }

    /**
     * Represents an amendment to a bill
     */
    public static class Amendment {
        private final int number;
        private final UUID authorId;
        private final String authorName;
        private final String text;
        private final long timestamp;

        public Amendment(int number, UUID authorId, String authorName, String text, long timestamp) {
            this.number = number;
            this.authorId = authorId;
            this.authorName = authorName;
            this.text = text;
            this.timestamp = timestamp;
        }

        public int getNumber() { return number; }
        public UUID getAuthorId() { return authorId; }
        public String getAuthorName() { return authorName; }
        public String getText() { return text; }
        public long getTimestamp() { return timestamp; }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("number", number);
            tag.putUUID("authorId", authorId);
            tag.putString("authorName", authorName);
            tag.putString("text", text);
            tag.putLong("timestamp", timestamp);
            return tag;
        }

        public static Amendment load(CompoundTag tag) {
            return new Amendment(
                tag.getInt("number"),
                tag.getUUID("authorId"),
                tag.getString("authorName"),
                tag.getString("text"),
                tag.getLong("timestamp")
            );
        }
    }
}

