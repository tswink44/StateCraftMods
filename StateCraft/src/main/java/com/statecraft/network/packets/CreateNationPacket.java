package com.statecraft.network.packets;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Packet to create a new nation
 */
public class CreateNationPacket {
    private final String name;
    private final String tag;
    private final String description;

    public CreateNationPacket(String name, String tag, String description) {
        this.name = name;
        this.tag = tag;
        this.description = description;
    }

    public CreateNationPacket(FriendlyByteBuf buf) {
        this.name = buf.readUtf(24);
        this.tag = buf.readUtf(5);
        this.description = buf.readUtf(100);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(name, 24);
        buf.writeUtf(tag, 5);
        buf.writeUtf(description, 100);
    }

    public String getName() {
        return name;
    }

    public String getTag() {
        return tag;
    }

    public String getDescription() {
        return description;
    }
}

