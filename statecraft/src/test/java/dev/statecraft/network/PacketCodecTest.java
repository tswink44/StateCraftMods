package dev.statecraft.network;

import dev.statecraft.api.TerritorySnapshot;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PacketCodecTest {
    @Test
    void territoryPacketPreservesDimensionHierarchyAndOwnership() {
        TerritorySnapshot snapshot = new TerritorySnapshot("minecraft:the_nether", -4, 6, 8,
                List.of(new TerritorySnapshot.Territory(-5, 7, "nation-id", "state-id", "city-id",
                        "Nation", "State", "City", "player:owner", 0x667788, 12, "Alice")));
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            new SuiteNetwork.TerritoryMessage(snapshot).encode(buffer);
            assertEquals(snapshot, SuiteNetwork.TerritoryMessage.decode(buffer).snapshot());
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test
    void oversizedTerritoryCountIsRejectedBeforeAllocatingEntries() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeUtf("minecraft:overworld");
            buffer.writeInt(0);
            buffer.writeInt(0);
            buffer.writeVarInt(8);
            buffer.writeVarInt(TerritorySnapshot.MAX_TERRITORIES + 1);
            assertThrows(DecoderException.class, () -> SuiteNetwork.TerritoryMessage.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    void serverPacketCannotSmuggleAnOutOfRegionClaim() {
        TerritorySnapshot snapshot = new TerritorySnapshot("minecraft:overworld", 0, 0, 8,
                List.of(new TerritorySnapshot.Territory(9, 0, "n", "s", "c", "N", "S", "C", "city:c", 0, 0)));
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            new SuiteNetwork.TerritoryMessage(snapshot).encode(buffer);
            assertThrows(DecoderException.class, () -> SuiteNetwork.TerritoryMessage.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    void requestsContainCommandsButNeverClientSuppliedAuthority() {
        SuiteNetwork.ActionRequest request = new SuiteNetwork.ActionRequest(17, "statecraft:mail",
                "mail send Player \"Subject\" \"A body\"");
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            request.encode(buffer);
            assertEquals(request, SuiteNetwork.ActionRequest.decode(buffer));
            assertEquals(0, buffer.readableBytes());
            buffer.clear();
            buffer.writeVarInt(-1);
            assertThrows(DecoderException.class, () -> SuiteNetwork.ActionRequest.decode(buffer));
        } finally {
            buffer.release();
        }
    }
}
