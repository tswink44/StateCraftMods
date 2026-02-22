package com.statecraft.network.packets;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Server -> Client: Sync nation laws/policies data
 */
public class SyncNationLawsPacket {
    private final String nationName;
    private final List<PolicyInfo> policies;
    private final List<EnactedLawInfo> enactedLaws;

    public SyncNationLawsPacket(String nationName, List<PolicyInfo> policies, List<EnactedLawInfo> enactedLaws) {
        this.nationName = nationName;
        this.policies = policies;
        this.enactedLaws = enactedLaws != null ? enactedLaws : new ArrayList<>();
    }

    // Backward-compatible constructor
    public SyncNationLawsPacket(String nationName, List<PolicyInfo> policies) {
        this(nationName, policies, new ArrayList<>());
    }

    public SyncNationLawsPacket(FriendlyByteBuf buf) {
        this.nationName = buf.readUtf(64);
        int count = buf.readVarInt();
        this.policies = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            policies.add(new PolicyInfo(
                buf.readUtf(32),
                buf.readUtf(64),
                buf.readUtf(16),
                buf.readDouble(),
                buf.readUtf(256)
            ));
        }
        // Read enacted laws
        int lawCount = buf.readVarInt();
        this.enactedLaws = new ArrayList<>(lawCount);
        for (int i = 0; i < lawCount; i++) {
            String lawNumber = buf.readUtf(32);
            String title = buf.readUtf(128);
            String description = buf.readUtf(512);
            String authorName = buf.readUtf(64);
            long enactedTime = buf.readLong();
            int yesVotes = buf.readVarInt();
            int noVotes = buf.readVarInt();
            int abstainVotes = buf.readVarInt();
            boolean wasVetoProof = buf.readBoolean();
            boolean isConstitutionalAmendment = buf.readBoolean();
            boolean isRepealed = buf.readBoolean();
            // Read policy changes map
            int policyCount = buf.readVarInt();
            Map<String, String> policyChanges = new HashMap<>();
            for (int j = 0; j < policyCount; j++) {
                policyChanges.put(buf.readUtf(64), buf.readUtf(1024));
            }
            String fullText = buf.readUtf(4096);
            enactedLaws.add(new EnactedLawInfo(lawNumber, title, description, authorName,
                enactedTime, yesVotes, noVotes, abstainVotes, wasVetoProof,
                isConstitutionalAmendment, isRepealed, policyChanges, fullText));
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(nationName, 64);
        buf.writeVarInt(policies.size());
        for (PolicyInfo policy : policies) {
            buf.writeUtf(policy.category, 32);
            buf.writeUtf(policy.name, 64);
            buf.writeUtf(policy.valueType, 16);
            buf.writeDouble(policy.numericValue);
            buf.writeUtf(policy.textValue != null ? policy.textValue : "", 256);
        }
        // Write enacted laws
        buf.writeVarInt(enactedLaws.size());
        for (EnactedLawInfo law : enactedLaws) {
            buf.writeUtf(law.lawNumber, 32);
            buf.writeUtf(law.title, 128);
            buf.writeUtf(law.description != null ? law.description : "", 512);
            buf.writeUtf(law.authorName, 64);
            buf.writeLong(law.enactedTime);
            buf.writeVarInt(law.yesVotes);
            buf.writeVarInt(law.noVotes);
            buf.writeVarInt(law.abstainVotes);
            buf.writeBoolean(law.wasVetoProof);
            buf.writeBoolean(law.isConstitutionalAmendment);
            buf.writeBoolean(law.isRepealed);
            buf.writeVarInt(law.policyChanges.size());
            for (Map.Entry<String, String> entry : law.policyChanges.entrySet()) {
                buf.writeUtf(entry.getKey(), 64);
                buf.writeUtf(entry.getValue(), 1024);
            }
            buf.writeUtf(law.fullText != null ? law.fullText : "", 4096);
        }
    }

    public String getNationName() { return nationName; }
    public List<PolicyInfo> getPolicies() { return policies; }
    public List<EnactedLawInfo> getEnactedLaws() { return enactedLaws; }

    public static class PolicyInfo {
        public final String category;
        public final String name;
        public final String valueType;
        public final double numericValue;
        public final String textValue;

        public PolicyInfo(String category, String name, String valueType, double numericValue, String textValue) {
            this.category = category;
            this.name = name;
            this.valueType = valueType;
            this.numericValue = numericValue;
            this.textValue = textValue;
        }
    }

    public static class EnactedLawInfo {
        public final String lawNumber;
        public final String title;
        public final String description;
        public final String authorName;
        public final long enactedTime;
        public final int yesVotes;
        public final int noVotes;
        public final int abstainVotes;
        public final boolean wasVetoProof;
        public final boolean isConstitutionalAmendment;
        public final boolean isRepealed;
        public final Map<String, String> policyChanges;
        public final String fullText;

        public EnactedLawInfo(String lawNumber, String title, String description, String authorName,
                              long enactedTime, int yesVotes, int noVotes, int abstainVotes,
                              boolean wasVetoProof, boolean isConstitutionalAmendment, boolean isRepealed,
                              Map<String, String> policyChanges, String fullText) {
            this.lawNumber = lawNumber;
            this.title = title;
            this.description = description;
            this.authorName = authorName;
            this.enactedTime = enactedTime;
            this.yesVotes = yesVotes;
            this.noVotes = noVotes;
            this.abstainVotes = abstainVotes;
            this.wasVetoProof = wasVetoProof;
            this.isConstitutionalAmendment = isConstitutionalAmendment;
            this.isRepealed = isRepealed;
            this.policyChanges = policyChanges != null ? policyChanges : new HashMap<>();
            this.fullText = fullText != null ? fullText : "";
        }
    }
}

