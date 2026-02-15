package com.statecraft.network.packets;

import net.minecraft.network.FriendlyByteBuf;

import java.util.HashMap;
import java.util.Map;

/**
 * Client -> Server: Submit a new bill proposal to the legislature
 */
public class ProposeBillPacket {

    private final String nationName;
    private final String title;
    private final String description;
    private final Map<String, String> policyChanges; // PolicyType name -> value

    public ProposeBillPacket(String nationName, String title, String description, Map<String, String> policyChanges) {
        this.nationName = nationName;
        this.title = title;
        this.description = description;
        this.policyChanges = policyChanges;
    }

    public ProposeBillPacket(FriendlyByteBuf buf) {
        this.nationName = buf.readUtf(256);
        this.title = buf.readUtf(128);
        this.description = buf.readUtf(1024);

        int count = buf.readVarInt();
        this.policyChanges = new HashMap<>(count);
        for (int i = 0; i < count; i++) {
            String policyName = buf.readUtf(64);
            String value = buf.readUtf(256);
            policyChanges.put(policyName, value);
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(nationName, 256);
        buf.writeUtf(title, 128);
        buf.writeUtf(description, 1024);

        buf.writeVarInt(policyChanges.size());
        for (Map.Entry<String, String> entry : policyChanges.entrySet()) {
            buf.writeUtf(entry.getKey(), 64);
            buf.writeUtf(entry.getValue(), 256);
        }
    }

    public String getNationName() { return nationName; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public Map<String, String> getPolicyChanges() { return policyChanges; }
}

