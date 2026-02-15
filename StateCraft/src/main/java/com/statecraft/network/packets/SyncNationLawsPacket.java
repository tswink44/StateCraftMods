package com.statecraft.network.packets;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * Server -> Client: Sync nation laws/policies data
 */
public class SyncNationLawsPacket {
    private final String nationName;
    private final List<PolicyInfo> policies;

    public SyncNationLawsPacket(String nationName, List<PolicyInfo> policies) {
        this.nationName = nationName;
        this.policies = policies;
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
    }

    public String getNationName() { return nationName; }
    public List<PolicyInfo> getPolicies() { return policies; }

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
}

