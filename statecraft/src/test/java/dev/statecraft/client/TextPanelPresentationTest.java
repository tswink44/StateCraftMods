package dev.statecraft.client;

import dev.statecraft.api.ui.EntityRef;
import dev.statecraft.client.state.UiPresentation;
import java.util.stream.Collectors;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TextPanelPresentationTest {
    @Test
    void fieldCardsRetainEveryTermAndNeverInferAnEntity() {
        String terms = "$1,234.56; tax $23.45; fee $4.56\nNo automatic recovery; contact an operator.";
        var entry = TextPanel.Entry.field(Component.literal("Total debit"), Component.literal(terms), UiPresentation.Tone.ACCENT);
        assertEquals("Total debit: " + terms, entry.copy());
        assertEquals("Total debit\n" + terms, entry.text().getString());
        assertEquals(EntityRef.NONE, entry.entity());
        assertEquals(UiPresentation.Icon.NONE, entry.icon());
    }

    @Test
    void paragraphCardsRetainBlankLinesAndCompleteCopyAndNarrationText() {
        String text = "Payer: Alex\n\nWarning: do not retry.\nFee: $2.00\n";
        var entries = TextPanel.Entry.paragraphs(Component.literal(text));
        assertEquals(text, entries.stream().map(TextPanel.Entry::copy).collect(Collectors.joining("\n")));
        assertEquals(text, entries.stream().map(entry -> entry.text().getString()).collect(Collectors.joining("\n")));
        assertTrue(entries.stream().noneMatch(entry -> entry.entity().present()));
    }

    @Test
    void existingEntryConstructorPreservesExactHiddenSelectionAndSafeCaption() {
        EntityRef ref = new EntityRef("economy", EntityRef.Kind.ACCOUNT, "private-account-key");
        Component caption = Component.literal("Arcadia treasury\nAvailable: $10.00");
        var entry = new TextPanel.Entry(caption, caption.getString(), ref);
        assertSame(ref, entry.entity());
        assertEquals(UiPresentation.Icon.ACCOUNT, entry.icon());
        assertFalse(entry.text().getString().contains(ref.id()));
        assertFalse(entry.copy().contains(ref.id()));
    }
}
