package dev.statecraft.network;

import dev.statecraft.api.UserError;
import dev.statecraft.api.ui.EntityRef;
import dev.statecraft.api.ui.PersonalDashboard;
import dev.statecraft.api.ui.UiQuery;
import dev.statecraft.api.ui.UiText;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DashboardCodecTest {
    @Test
    void personalDataAndExactNavigationTargetsRoundTripTogether() {
        String account = "player:" + UUID.randomUUID();
        var bank = new PersonalDashboard.Entry(UiText.literal("Personal account"), UiText.literal("$10.00"),
                new UiQuery("economy:atm", new EntityRef("economy", EntityRef.Kind.ACCOUNT, account), "", 0));
        var city = new PersonalDashboard.Entry(UiText.literal("[Oakvale] (Westhaven/Arcadia)"), UiText.EMPTY,
                UiQuery.detail(new EntityRef("statecraft", EntityRef.Kind.GOVERNMENT, UUID.randomUUID().toString())));
        PersonalDashboard data = new PersonalDashboard("Alice", null, null, city, true,
                new PersonalDashboard.Page(List.of(bank), 0, 1), PersonalDashboard.Page.EMPTY, PersonalDashboard.Page.EMPTY, UiText.EMPTY);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            DashboardCodec.dashboard(buffer, data);
            assertEquals(data, DashboardCodec.dashboard(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test
    void onlyBoundedIndependentPageOffsetsAreAccepted() {
        assertThrows(UserError.class, () -> new PersonalDashboard.Request(-1, 0, 0));
        assertThrows(UserError.class, () -> new PersonalDashboard.Request(0, 1, 0));
        assertThrows(UserError.class, () -> new PersonalDashboard.Request(0, 0, 100_008));
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            DashboardCodec.request(buffer, new PersonalDashboard.Request(12, 24, 36));
            assertEquals(new PersonalDashboard.Request(12, 24, 36), DashboardCodec.request(buffer));
            buffer.clear();
            buffer.writeVarInt(PersonalDashboard.PAGE_SIZE + 1);
            assertThrows(DecoderException.class, () -> DashboardCodec.page(buffer));
        } finally {
            buffer.release();
        }
    }
}
