package dev.statecraft.api;

import dev.statecraft.api.ui.ActionDisplay;
import dev.statecraft.api.ui.ActionIntent;
import dev.statecraft.api.ui.ActionOutcome;
import dev.statecraft.api.ui.ActionSelection;
import dev.statecraft.api.ui.DisplayText;
import dev.statecraft.domain.CoreMenus;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DisplayTextTest {
    private static final String ID = "11111111-2222-3333-4444-555555555555";

    @Test
    void knownNamesAndAuthoredTextRemainUnchangedButMissingNamesDoNotExposeIds() {
        assertEquals("Arcadia", DisplayText.name("Arcadia", "Former nation"));
        assertEquals("Former nation", DisplayText.name(ID, "Former nation"));
        assertEquals("Former player", DisplayText.name(null, "Former player"));
        assertEquals("Note: " + ID, DisplayText.name("Note: " + ID, "Former player"));
    }

    @Test
    void ratesAndTimesAreReadableWithoutLosingPrecision() {
        assertEquals("2.5%", DisplayText.percent(250));
        assertEquals("0.01%", DisplayText.percent(1));
        assertEquals("100%", DisplayText.percent(10000));
        assertEquals("1 day", DisplayText.duration(86_400_000));
        assertEquals("1 hour 1 millisecond", DisplayText.duration(3_600_001));
        assertEquals("1 minute 1 second", DisplayText.duration(61_000));
        assertEquals("Sep 13, 2026 at 09:12 UTC", DisplayText.date(Instant.parse("2026-09-13T09:12:40Z").toEpochMilli()));
        assertEquals("Not scheduled", DisplayText.date(0));
        assertEquals("Awaiting Ratification", DisplayText.words("AWAITING_RATIFICATION"));
    }

    @Test
    void locationsUseUsefulCoordinatesNotWireFormat() {
        assertEquals("Chunk -3, 17 (Overworld)", DisplayText.chunk("minecraft:overworld|-3|17"));
        assertEquals("The Nether", DisplayText.dimension("minecraft:the_nether"));
        assertEquals("Crystal Caves (Example)", DisplayText.dimension("example:crystal_caves"));
    }

    @Test
    void guidedSuccessDoesNotEchoCreatedIdsAndFailuresKeepTheirExplanation() {
        CoreMenus.register();
        ActionSelection selection = ActionSelection.form("statecraft:states", "state create <nation> <name> <governor>",
                Map.of("nation", ID, "name", "Westhaven", "governor", ID));
        String title = ActionDisplay.title(selection);
        assertFalse(title.contains(ID));
        assertTrue(title.contains("States"));
        assertTrue(title.contains("Create"));
        assertEquals(title + " completed.", ActionDisplay.feedback(ActionIntent.MUTATION, title, ActionOutcome.COMPLETED,
                "Created state [" + ID + "]"));
        assertEquals("Not enough money.", ActionDisplay.feedback(ActionIntent.MUTATION, title, ActionOutcome.REJECTED,
                "Not enough money."));
        assertEquals("Raw " + ID, ActionDisplay.feedback(ActionIntent.RAW, title, ActionOutcome.COMPLETED, "Raw " + ID));
        assertEquals(ID, selection.values().get("governor"));
    }
}
