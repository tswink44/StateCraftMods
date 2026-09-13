package dev.statecraft.network;

import dev.statecraft.api.form.FormChoice;
import dev.statecraft.api.form.FormField;
import dev.statecraft.api.form.FormQuery;
import dev.statecraft.api.form.FormSchema;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import java.util.List;
import java.util.Map;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FormCodecTest {
    @Test
    void requestsCarrySelectionsAndSearchButNoAuthority() {
        var request = new SuiteNetwork.FormRequest(45, "statecraft:states",
                "state create <nation> <name> <governor>", Map.of("nation", "nation-id", "name", "New State"),
                new FormQuery("governor", "Alice", 20));
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            request.encode(buffer);
            assertEquals(request, SuiteNetwork.FormRequest.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test
    void responseRoundTripPreservesDependenciesLabelsAndPaging() {
        var nation = new FormField("nation", "Nation", FormField.Kind.CHOICE, "n", "Nation", "",
                List.of(), false, List.of(new FormChoice("n", "Nation", "Managed")), 0, false);
        var governor = new FormField("governor", "Governor", FormField.Kind.CHOICE, "g", "Alice", "Eligible citizens",
                List.of("nation"), false, List.of(new FormChoice("g", "Alice", "Citizen")), 20, true);
        var response = new SuiteNetwork.FormResponse(45, "statecraft:states", "state create <nation> <governor>",
                true, "", new FormSchema(List.of(nation, governor)));
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            response.encode(buffer);
            assertEquals(response, SuiteNetwork.FormResponse.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test
    void duplicateAndExcessiveValuesAreRejectedBeforeUse() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeVarInt(FormSchema.MAX_FIELDS + 1);
            assertThrows(DecoderException.class, () -> FormCodec.readValues(buffer));
            buffer.clear();
            buffer.writeVarInt(2);
            buffer.writeUtf("nation");
            buffer.writeUtf("first");
            buffer.writeUtf("nation");
            buffer.writeUtf("second");
            assertThrows(DecoderException.class, () -> FormCodec.readValues(buffer));
        } finally {
            buffer.release();
        }
    }
}
