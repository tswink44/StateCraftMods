package dev.statecraft.network;

import dev.statecraft.api.GovernanceAccess.Kind;
import dev.statecraft.api.ui.EntityRef;
import dev.statecraft.api.ui.GovernmentOverview;
import dev.statecraft.api.ui.PersonalDashboard;
import dev.statecraft.api.ui.UiAction;
import dev.statecraft.api.ui.UiQuery;
import dev.statecraft.api.ui.UiText;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GovernmentOverviewCodecTest {
    @Test
    void overviewPreservesScopedSettingsAndInboxThroughTheWireFormat() {
        String nation = UUID.randomUUID().toString();
        var leader = new PersonalDashboard.Entry(UiText.literal("Alice"), UiText.literal("Leader"),
                UiQuery.detail(new EntityRef("statecraft", EntityRef.Kind.PLAYER, UUID.randomUUID().toString())));
        UiQuery inbox = new UiQuery("statecraft:official_mail",
                new EntityRef("statecraft", EntityRef.Kind.GOVERNMENT, nation), "", 0);
        UiAction invitation = new UiAction("statecraft:invitations", "government invite <government> <player>",
                UiText.literal("Invite player"), Map.of("government", nation));
        var overview = new GovernmentOverview(nation, Kind.NATION, "Arcadia", "Blue and gold", "A player-run nation",
                leader, null, UiText.literal("$100.00"), PersonalDashboard.Page.EMPTY,
                new PersonalDashboard.Page(List.of(leader), 0, 1),
                List.of(new GovernmentOverview.Group("settings", UiText.literal("Settings"), List.of(invitation))), inbox);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            GovernmentOverviewCodec.overview(buffer, overview);
            assertEquals(overview, GovernmentOverviewCodec.overview(buffer));
            assertEquals(0, buffer.readableBytes());
            buffer.clear();
            var request = new GovernmentOverview.Request(nation, 12, 24);
            GovernmentOverviewCodec.request(buffer, request);
            assertEquals(request, GovernmentOverviewCodec.request(buffer));
        } finally {
            buffer.release();
        }
    }
}
