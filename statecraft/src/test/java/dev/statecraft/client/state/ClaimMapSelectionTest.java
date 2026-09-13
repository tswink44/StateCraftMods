package dev.statecraft.client.state;

import dev.statecraft.api.ChunkKey;
import dev.statecraft.api.CommandLine;
import dev.statecraft.api.CommandTemplate;
import dev.statecraft.api.TerritorySnapshot;
import dev.statecraft.api.UserError;
import dev.statecraft.api.form.FormConstraints;
import dev.statecraft.api.form.FormContext;
import dev.statecraft.api.form.FormField;
import dev.statecraft.api.form.FormSchema;
import dev.statecraft.api.form.FormValidation;
import dev.statecraft.api.ui.ActionSelection;
import dev.statecraft.api.ui.ClaimMapMode;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClaimMapSelectionTest {
    private static final String DIMENSION = "minecraft:overworld";

    @Test
    void nationsClaimOnlyKnownUnclaimedCells() {
        var selection = new ClaimMapSelection(ClaimMapMode.CLAIM, "nation");
        var snapshot = snapshot(territory(0, null, ""));
        assertEquals(ClaimMapSelection.Change.OUTSIDE_SCOPE, selection.toggle(snapshot, key(0, 0)));
        assertEquals(ClaimMapSelection.Change.ADDED, selection.toggle(snapshot, key(-1, -1)));
        assertEquals(ClaimMapSelection.Change.REMOVED, selection.toggle(snapshot, key(-1, -1)));
        assertEquals(ClaimMapSelection.Change.UNKNOWN, selection.toggle(snapshot, key(9, 0)));
        assertEquals(ClaimMapSelection.Change.UNKNOWN, selection.toggle(TerritorySnapshot.EMPTY, key(0, 0)));
        assertEquals(ClaimMapSelection.Change.UNKNOWN, selection.toggle(snapshot, new ChunkKey("minecraft:the_nether", 0, 0)));
    }

    @Test
    void stateAllocationIncludesNationOnlyStateOnlyAndCityClaimsButNotForeignOrUnclaimedLand() {
        var selection = new ClaimMapSelection(ClaimMapMode.ASSIGN_STATE, "nation");
        var snapshot = snapshot(territory(0, null, null), territory(1, "state", ""), territory(2, "state", "city"),
                new TerritorySnapshot.Territory(3, 0, "other", "", "", "Other", "", "", "owner", 0, 0));
        for (int x = 0; x <= 2; x++) assertEquals(ClaimMapSelection.Change.ADDED, selection.toggle(snapshot, key(x, 0)));
        assertEquals(ClaimMapSelection.Change.OUTSIDE_SCOPE, selection.toggle(snapshot, key(3, 0)));
        assertEquals(ClaimMapSelection.Change.OUTSIDE_SCOPE, selection.toggle(snapshot, key(4, 0)));
        assertEquals("none", selection.values("none").get("state_or_none"));
        assertEquals("nation", selection.values("none").get("nation"));
    }

    @Test
    void cityAllocationRequiresTheSelectedStateEvenWithoutACity() {
        var selection = new ClaimMapSelection(ClaimMapMode.ASSIGN_CITY, "state");
        var snapshot = snapshot(territory(0, null, null), territory(1, "", ""), territory(2, "state", ""),
                territory(3, "state", "city"), territory(4, "other-state", "other-city"));
        for (int x : List.of(0, 1, 4)) assertEquals(ClaimMapSelection.Change.OUTSIDE_SCOPE, selection.toggle(snapshot, key(x, 0)));
        for (int x : List.of(2, 3)) assertEquals(ClaimMapSelection.Change.ADDED, selection.toggle(snapshot, key(x, 0)));
        assertEquals("state", selection.values("city").get("state"));
        assertEquals("city", selection.values("city").get("city_or_none"));
        assertFalse(selection.values("").containsKey("city_or_none"));
    }

    @Test
    void batchLimitDoesNotPreventDeselectingAndCapturedValuesAreImmutableCanonicalSelections() {
        var selection = new ClaimMapSelection(ClaimMapMode.CLAIM, "nation");
        var snapshot = snapshot();
        for (int i = 0; i < 64; i++) {
            assertEquals(ClaimMapSelection.Change.ADDED, selection.toggle(snapshot, key(i % 8 - 4, i / 8 - 4)));
        }
        assertEquals(64, selection.size());
        assertEquals(ClaimMapSelection.Change.LIMIT_REACHED, selection.toggle(snapshot, key(5, 5)));
        var captured = selection.values("");
        assertEquals(64, captured.get("chunks").split(",").length);
        assertTrue(captured.get("chunks").startsWith("minecraft:overworld|-4|-4,"));
        assertEquals(ClaimMapSelection.Change.REMOVED, selection.toggle(snapshot, key(-4, -4)));
        assertEquals(64, captured.get("chunks").split(",").length);
        assertThrows(UnsupportedOperationException.class, () -> captured.put("chunks", "here"));
        assertTrue(selection.retainDimension("minecraft:the_nether"));
        assertEquals(0, selection.size());
        assertEquals("here", selection.values("").get("chunks"));
    }

    @Test
    void refreshedOwnershipInvalidatesButDoesNotSilentlyDiscardReviewedSelections() {
        var selection = new ClaimMapSelection(ClaimMapMode.CLAIM, "nation");
        selection.toggle(snapshot(), key(0, 0));
        assertEquals(ClaimMapSelection.Eligibility.OUTSIDE_SCOPE, selection.selectionEligibility(snapshot(territory(0, "", ""))));
        assertEquals(1, selection.size());
        assertEquals(ClaimMapSelection.Change.REMOVED, selection.toggle(TerritorySnapshot.EMPTY, key(0, 0)));
    }

    @Test
    void sixtyFourContiguousVanillaWorldEdgeChunksFitSharedCapacityForEveryMapAction() {
        for (String dimension : List.of(DIMENSION, "minecraft:the_nether", "minecraft:the_end")) {
            for (ClaimMapMode mode : ClaimMapMode.values()) {
                for (int sign : new int[] {-1, 1}) {
                    var selection = edgeSelection(dimension, mode, sign);
                    String target = mode == ClaimMapMode.CLAIM ? "" : "fedcba98-7654-3210-fedc-ba9876543210";
                    var values = assertDoesNotThrow(() -> selection.validatedValues(target));
                    String chunks = values.get("chunks");
                    assertEquals(64, chunks.split(",").length);
                    assertTrue(chunks.length() > 2048);
                    assertTrue(chunks.length() <= FormSchema.MAX_VALUE_LENGTH);
                    var field = new FormField("chunks", "Chunks", FormField.Kind.TEXT, chunks, "", "", List.of(),
                            false, List.of(), 0, false, FormConstraints.text(FormSchema.MAX_VALUE_LENGTH));
                    assertTrue(FormValidation.errors(new FormSchema(List.of(field)), values).isEmpty());
                    var action = ActionSelection.form("statecraft:claims", mode.template(), values);
                    String command = assertDoesNotThrow(action::rendered);
                    assertTrue(command.length() < CommandLine.MAX_LENGTH);
                    assertEquals(chunks, CommandLine.split(command).get(3));
                }
            }
        }
    }

    @Test
    void fullRenderedCommandRejectsLongDimensionsEvenWhenEveryFieldAndTheirSumFit() {
        var selection = edgeSelection("statecraft:" + "a".repeat(35), ClaimMapMode.ASSIGN_STATE, 1);
        var values = selection.values("none");
        assertTrue(values.get("chunks").length() <= FormSchema.MAX_VALUE_LENGTH);
        assertTrue(values.values().stream().mapToInt(String::length).sum() <= CommandLine.MAX_LENGTH);
        assertDoesNotThrow(() -> FormContext.validateValues(ClaimMapMode.ASSIGN_STATE.template(), values));
        assertThrows(UserError.class, () -> new CommandTemplate(ClaimMapMode.ASSIGN_STATE.template()).render(values));
        assertThrows(UserError.class, () -> selection.validatedValues("none"));
        assertEquals(64, selection.size());
        assertEquals(values, selection.values("none"));
    }

    @Test
    void metadataCanAwaitATargetWithoutLosingTheExplicitFullBatchButOversizedValuesStillReject() {
        var selection = edgeSelection(DIMENSION, ClaimMapMode.ASSIGN_CITY, -1);
        var requested = assertDoesNotThrow(() -> selection.validatedValues(""));
        assertFalse(requested.containsKey("city_or_none"));
        assertEquals(64, requested.get("chunks").split(",").length);
        var oversized = edgeSelection("statecraft:" + "a".repeat(100), ClaimMapMode.CLAIM, 1);
        assertTrue(oversized.values("").get("chunks").length() > FormSchema.MAX_VALUE_LENGTH);
        assertThrows(UserError.class, () -> oversized.validatedValues(""));
        assertEquals(64, oversized.size());
    }

    private static ClaimMapSelection edgeSelection(String dimension, ClaimMapMode mode, int sign) {
        String government = "01234567-89ab-cdef-0123-456789abcdef";
        int edge = sign * (ChunkKey.MAX_COORDINATE - 1);
        var claims = new ArrayList<TerritorySnapshot.Territory>();
        var chunks = new ArrayList<ChunkKey>();
        for (int x = 0; x < 8; x++) {
            for (int z = 0; z < 8; z++) {
                var chunk = new ChunkKey(dimension, edge - sign * x, edge - sign * z);
                chunks.add(chunk);
                if (mode != ClaimMapMode.CLAIM) claims.add(new TerritorySnapshot.Territory(chunk.x(), chunk.z(),
                        government, mode == ClaimMapMode.ASSIGN_CITY ? government : "", "", "Nation", "State", "",
                        "", 0x337755, 0));
            }
        }
        var snapshot = new TerritorySnapshot(dimension, edge, edge, 8, claims);
        var selection = new ClaimMapSelection(mode, government);
        for (ChunkKey chunk : chunks) assertEquals(ClaimMapSelection.Change.ADDED, selection.toggle(snapshot, chunk));
        return selection;
    }

    private static ChunkKey key(int x, int z) { return new ChunkKey(DIMENSION, x, z); }
    private static TerritorySnapshot snapshot(TerritorySnapshot.Territory... territories) {
        return new TerritorySnapshot(DIMENSION, 0, 0, 8, List.of(territories));
    }
    private static TerritorySnapshot.Territory territory(int x, String state, String city) {
        return new TerritorySnapshot.Territory(x, 0, "nation", state, city, "Nation", "State", "City",
                "private-owner", 0x337755, 0);
    }
}
