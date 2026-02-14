package com.statecraft.network.packets;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Server -> Client packet for action results (success/failure)
 */
public class ActionResultPacket {
    private final boolean success;
    private final String message;

    public ActionResultPacket(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public ActionResultPacket(FriendlyByteBuf buf) {
        this.success = buf.readBoolean();
        this.message = buf.readUtf(256);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(success);
        buf.writeUtf(message, 256);
    }

    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
}

