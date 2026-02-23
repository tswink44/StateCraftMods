package com.statecraft.economy.network.packets;

import net.minecraft.network.FriendlyByteBuf;

import java.util.HashMap;
import java.util.Map;

/**
 * Server -> Client: Sync the item value registry so clients can display
 * accurate sell prices in the Trading Hub UI.
 *
 * Sent on player login and when item values are reloaded via /eco reloaditems.
 */
public class SyncItemValuesPacket {
    private final Map<String, Double> itemValues;

    public SyncItemValuesPacket(Map<String, Double> itemValues) {
        this.itemValues = itemValues;
    }

    public SyncItemValuesPacket(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        this.itemValues = new HashMap<>(size);
        for (int i = 0; i < size; i++) {
            String key = buf.readUtf(256); // max 256 chars for item ID
            double value = buf.readDouble();
            itemValues.put(key, value);
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(itemValues.size());
        for (Map.Entry<String, Double> entry : itemValues.entrySet()) {
            buf.writeUtf(entry.getKey(), 256);
            buf.writeDouble(entry.getValue());
        }
    }

    public Map<String, Double> getItemValues() {
        return itemValues;
    }
}

