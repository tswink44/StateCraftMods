package com.statecraft.network.packets;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client -> Server: Leader invokes or revokes an emergency power.
 */
public class InvokeEmergencyPowerPacket {

    public enum Action { INVOKE, REVOKE }

    private final String nationName;
    private final String powerName; // EmergencyPower enum name
    private final Action action;
    private final String targetValue; // e.g., nation name for DIPLOMATIC_CRISIS, player name for SUCCESSION_CRISIS

    public InvokeEmergencyPowerPacket(String nationName, String powerName, Action action, String targetValue) {
        this.nationName = nationName;
        this.powerName = powerName;
        this.action = action;
        this.targetValue = targetValue != null ? targetValue : "";
    }

    public InvokeEmergencyPowerPacket(FriendlyByteBuf buf) {
        this.nationName = buf.readUtf();
        this.powerName = buf.readUtf();
        this.action = buf.readEnum(Action.class);
        this.targetValue = buf.readUtf();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(nationName);
        buf.writeUtf(powerName);
        buf.writeEnum(action);
        buf.writeUtf(targetValue);
    }

    public String getNationName() { return nationName; }
    public String getPowerName() { return powerName; }
    public Action getAction() { return action; }
    public String getTargetValue() { return targetValue; }
}

