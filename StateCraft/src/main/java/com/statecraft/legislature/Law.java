package com.statecraft.legislature;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.*;

/**
 * Represents an enacted law in the Law Codex
 * Laws are immutable records of passed legislation
 */
public class Law {

    public enum Type {
        NATION_LAW("NL"),           // Normal legislation passed by legislature
        EXECUTIVE_ORDER("EO");      // Emergency power invocation by Leader

        private final String prefix;

        Type(String prefix) {
            this.prefix = prefix;
        }

        public String getPrefix() { return prefix; }
    }

    private final UUID lawId;
    private final UUID nationId;
    private final String lawNumber;  // e.g., "NL-2026-001" or "EO-2026-001"
    private final Type type;
    private final String title;
    private final String description;
    private final String authorName;
    private final long enactedTime;

    // What this law changed
    private final Map<PolicyType, String> policyChanges;

    // Voting record (for regular laws)
    private final int yesVotes;
    private final int noVotes;
    private final int abstainVotes;
    private final boolean wasVetoProof;

    // For tracking repeal/supersession
    private boolean repealed;
    private String repealedByLawNumber;
    private long repealedTime;

    // Full text for custom laws
    private final String fullText;

    // Search keywords (auto-generated from title/description)
    private final Set<String> keywords;

    public Law(UUID nationId, String lawNumber, Type type, String title, String description,
               String authorName, Map<PolicyType, String> policyChanges,
               int yesVotes, int noVotes, int abstainVotes, boolean wasVetoProof, String fullText) {
        this.lawId = UUID.randomUUID();
        this.nationId = nationId;
        this.lawNumber = lawNumber;
        this.type = type;
        this.title = title;
        this.description = description;
        this.authorName = authorName;
        this.enactedTime = System.currentTimeMillis();
        this.policyChanges = new HashMap<>(policyChanges);
        this.yesVotes = yesVotes;
        this.noVotes = noVotes;
        this.abstainVotes = abstainVotes;
        this.wasVetoProof = wasVetoProof;
        this.repealed = false;
        this.fullText = fullText != null ? fullText : "";
        this.keywords = generateKeywords();
    }

    // Constructor for NBT loading
    private Law(UUID lawId, UUID nationId, String lawNumber, Type type) {
        this.lawId = lawId;
        this.nationId = nationId;
        this.lawNumber = lawNumber;
        this.type = type;
        this.title = "";
        this.description = "";
        this.authorName = "";
        this.enactedTime = 0;
        this.policyChanges = new HashMap<>();
        this.yesVotes = 0;
        this.noVotes = 0;
        this.abstainVotes = 0;
        this.wasVetoProof = false;
        this.fullText = "";
        this.keywords = new HashSet<>();
    }

    private Set<String> generateKeywords() {
        Set<String> words = new HashSet<>();

        // Add words from title
        for (String word : title.toLowerCase().split("\\s+")) {
            if (word.length() > 2) {
                words.add(word);
            }
        }

        // Add words from description
        for (String word : description.toLowerCase().split("\\s+")) {
            if (word.length() > 2) {
                words.add(word);
            }
        }

        // Add policy type names
        for (PolicyType policy : policyChanges.keySet()) {
            words.add(policy.name().toLowerCase());
            words.add(policy.getDisplayName().toLowerCase());
            words.add(policy.getCategory().name().toLowerCase());
        }

        return words;
    }

    // Getters
    public UUID getLawId() { return lawId; }
    public UUID getNationId() { return nationId; }
    public String getLawNumber() { return lawNumber; }
    public Type getType() { return type; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getAuthorName() { return authorName; }
    public long getEnactedTime() { return enactedTime; }
    public Map<PolicyType, String> getPolicyChanges() { return Collections.unmodifiableMap(policyChanges); }
    public int getYesVotes() { return yesVotes; }
    public int getNoVotes() { return noVotes; }
    public int getAbstainVotes() { return abstainVotes; }
    public boolean wasVetoProof() { return wasVetoProof; }
    public boolean isRepealed() { return repealed; }
    public String getRepealedByLawNumber() { return repealedByLawNumber; }
    public long getRepealedTime() { return repealedTime; }
    public String getFullText() { return fullText; }
    public Set<String> getKeywords() { return Collections.unmodifiableSet(keywords); }

    public void repeal(String repealedByLawNumber) {
        this.repealed = true;
        this.repealedByLawNumber = repealedByLawNumber;
        this.repealedTime = System.currentTimeMillis();
    }

    /**
     * Check if this law matches a search query
     */
    public boolean matchesSearch(String query) {
        if (query == null || query.trim().isEmpty()) return true;

        String lowerQuery = query.toLowerCase();

        // Check law number
        if (lawNumber.toLowerCase().contains(lowerQuery)) return true;

        // Check title
        if (title.toLowerCase().contains(lowerQuery)) return true;

        // Check keywords
        for (String keyword : keywords) {
            if (keyword.contains(lowerQuery)) return true;
        }

        // Check full text
        if (fullText.toLowerCase().contains(lowerQuery)) return true;

        return false;
    }

    // NBT Serialization
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("lawId", lawId);
        tag.putUUID("nationId", nationId);
        tag.putString("lawNumber", lawNumber);
        tag.putString("type", type.name());
        tag.putString("title", title);
        tag.putString("description", description);
        tag.putString("authorName", authorName);
        tag.putLong("enactedTime", enactedTime);
        tag.putInt("yesVotes", yesVotes);
        tag.putInt("noVotes", noVotes);
        tag.putInt("abstainVotes", abstainVotes);
        tag.putBoolean("wasVetoProof", wasVetoProof);
        tag.putBoolean("repealed", repealed);
        tag.putString("repealedByLawNumber", repealedByLawNumber != null ? repealedByLawNumber : "");
        tag.putLong("repealedTime", repealedTime);
        tag.putString("fullText", fullText);

        // Policy changes
        ListTag policiesList = new ListTag();
        for (Map.Entry<PolicyType, String> entry : policyChanges.entrySet()) {
            CompoundTag policyTag = new CompoundTag();
            policyTag.putString("policy", entry.getKey().name());
            policyTag.putString("value", entry.getValue());
            policiesList.add(policyTag);
        }
        tag.put("policyChanges", policiesList);

        return tag;
    }

    public static Law load(CompoundTag tag) {
        UUID lawId = tag.getUUID("lawId");
        UUID nationId = tag.getUUID("nationId");
        String lawNumber = tag.getString("lawNumber");
        Type type = Type.valueOf(tag.getString("type"));

        Law law = new Law(lawId, nationId, lawNumber, type);

        // Use reflection to set final fields (or use a builder pattern in production)
        // For now, we'll create a new object with all fields
        Map<PolicyType, String> policies = new HashMap<>();
        ListTag policiesList = tag.getList("policyChanges", Tag.TAG_COMPOUND);
        for (int i = 0; i < policiesList.size(); i++) {
            CompoundTag policyTag = policiesList.getCompound(i);
            try {
                PolicyType policy = PolicyType.valueOf(policyTag.getString("policy"));
                String value = policyTag.getString("value");
                policies.put(policy, value);
            } catch (IllegalArgumentException ignored) {}
        }

        Law loadedLaw = new Law(
            nationId, lawNumber, type,
            tag.getString("title"),
            tag.getString("description"),
            tag.getString("authorName"),
            policies,
            tag.getInt("yesVotes"),
            tag.getInt("noVotes"),
            tag.getInt("abstainVotes"),
            tag.getBoolean("wasVetoProof"),
            tag.getString("fullText")
        );

        if (tag.getBoolean("repealed")) {
            loadedLaw.repealed = true;
            loadedLaw.repealedByLawNumber = tag.getString("repealedByLawNumber");
            loadedLaw.repealedTime = tag.getLong("repealedTime");
        }

        return loadedLaw;
    }
}

