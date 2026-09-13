package dev.statecraft.network;

import dev.statecraft.api.form.*;
import dev.statecraft.api.ui.*;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UiCodecTest {
    private final UUID world = UUID.randomUUID();
    private final OperationRef operation = new OperationRef(world, UUID.randomUUID());
    private final ActionSelection selection = ActionSelection.form("economy:bank", "loan repay <loanId> <amountOrAll>",
            Map.of("loanId", UUID.randomUUID().toString(), "amountOrAll", "all"));

    @Test
    void reviewedRequestsAndUncertainOutcomesRoundTripWithoutLosingIdentity() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            var request = new SuiteNetwork.ActionRequest(4, world, selection, operation);
            request.encode(buffer);
            assertEquals(request, SuiteNetwork.ActionRequest.decode(buffer));
            var response = new SuiteNetwork.ActionResponse(4, world, selection.page(), operation,
                    ActionIntent.MUTATION, ActionOutcome.UNCERTAIN, "Do not repeat.");
            response.encode(buffer);
            assertEquals(response, SuiteNetwork.ActionResponse.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally { buffer.release(); }
    }

    @Test
    void typedRowsSeedsAndAvailabilityRoundTrip() {
        UiView view = new UiView(UiText.tr("ui.title", "Loans"), UiText.literal("Details"),
                List.of(new UiRow("My loan", "Manual repayment", new EntityRef("economy", EntityRef.Kind.LOAN, "loan-id"))),
                List.of(new UiAction(selection.page(), selection.template(), UiText.literal("Repay"), selection.values())
                        .disabled(UiText.tr("ui.reason", "Permission required"))),
                20, true, UiText.literal("No loans"));
        var response = new SuiteNetwork.ViewResponse(3, world, UiQuery.page("economy:bank"), true, UiText.EMPTY, view);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            response.encode(buffer);
            assertEquals(response, SuiteNetwork.ViewResponse.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally { buffer.release(); }
    }

    @Test
    void quotesAndValidationMessagesRoundTrip() {
        var quote = new PreviewQuote(operation, 10_000, new ActionPreview(UiText.literal("Review"),
                List.of(new ActionPreview.Line("Total", "$10.00", true)), UiText.literal("Check terms"), "fingerprint"));
        var response = new SuiteNetwork.PreviewResponse(3, world, selection, true, UiText.EMPTY, quote, Map.of());
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            response.encode(buffer);
            assertEquals(response, SuiteNetwork.PreviewResponse.decode(buffer));
            var denied = new SuiteNetwork.PreviewResponse(4, world, selection, false, UiText.literal("Correct fields"),
                    null, Map.of("amountOrAll", UiText.tr("gui.statecraft.field.money", "Enter an amount.", "Amount")));
            denied.encode(buffer);
            assertEquals(denied, SuiteNetwork.PreviewResponse.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally { buffer.release(); }
    }

    @Test
    void numericConstraintsSurviveFormTransport() {
        FormField field = new FormField("amount", "Amount", FormField.Kind.TEXT, "", "", "", List.of(),
                false, List.of(), 0, false, FormConstraints.money(1, 100_000).or("all"));
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            FormCodec.writeSchema(buffer, new FormSchema(List.of(field)));
            assertEquals(field, FormCodec.readSchema(buffer).fields().get(0));
            assertEquals(0, buffer.readableBytes());
        } finally { buffer.release(); }
    }

    @Test
    void badCountsEnumsAndAggregateTextAreRejectedBeforeUse() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeVarInt(-1);
            assertThrows(DecoderException.class, () -> SuiteNetwork.ViewRequest.decode(buffer));
            buffer.clear();
            buffer.writeByte(255);
            assertThrows(DecoderException.class, () -> UiCodec.enumeration(buffer, ActionOutcome.values()));
            buffer.clear();
            buffer.writeUtf("x");
            buffer.writeUtf("a".repeat(30_000));
            buffer.writeVarInt(1);
            buffer.writeUtf("b");
            assertThrows(DecoderException.class, () -> UiCodec.text(buffer));
        } finally { buffer.release(); }
    }
}
