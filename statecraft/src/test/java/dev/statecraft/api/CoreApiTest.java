package dev.statecraft.api;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CoreApiTest {
    @Test
    void moneyUsesExactCentsAndRejectsInvalidAmounts() {
        assertEquals(12345, Money.parse("123.45"));
        assertEquals(10, Money.parse("0.10"));
        assertEquals("$1,234.56", Money.format(123456));
        for (String input : List.of("-1", "NaN", "Infinity", "1e9", "1.001", "", "+1", "99999999999999.99")) {
            assertThrows(UserError.class, () -> Money.parse(input), input);
        }
        assertEquals(Money.MAX, Money.parse("90000000000000.00"));
        assertThrows(UserError.class, () -> Money.add(Money.MAX, 1));
        assertThrows(UserError.class, () -> Money.multiply(Money.MAX, Long.MAX_VALUE));
        assertThrows(UserError.class, () -> Money.positive(0));
        assertEquals(Money.MAX, Money.tax(Money.MAX, 10000));
        assertEquals(33, Money.tax(101, 3333));
        assertThrows(UserError.class, () -> Money.tax(100, -1));
        assertThrows(UserError.class, () -> Money.tax(100, 10001));
    }

    @Test
    void quotedTextAndMultilineMailRoundTrip() {
        String body = "A title with \"quotes\", backslashes \\, and\nanother line.";
        assertEquals(List.of("mail", "send", body), CommandLine.split("mail send " + CommandLine.quote(body)));
        assertEquals(List.of("nation", "create", "Two Words"), CommandLine.split("nation create 'Two Words'"));
        assertThrows(UserError.class, () -> CommandLine.split("unclosed \"quote"));
        assertThrows(UserError.class, () -> CommandLine.split("dangling\\"));
        assertThrows(UserError.class, () -> CommandLine.split("line\nbreak"));
        assertThrows(UserError.class, () -> CommandLine.split("x".repeat(CommandLine.MAX_LENGTH + 1)));
        assertThrows(UserError.class, () -> CommandLine.split("x ".repeat(65)));
    }

    @Test
    void templatesTreatEveryFieldAsDataNotCommandSyntax() {
        CommandTemplate template = new CommandTemplate("mail send <recipient> <subject> <body>");
        Map<String, String> values = Map.of("recipient", "Jane", "subject", "An \"important\" note",
                "body", "Two lines\nwith <recipient> literally written");
        assertEquals(List.of("recipient", "subject", "body"), template.fields());
        assertEquals(List.of("mail", "send", "Jane", values.get("subject"), values.get("body")),
                CommandLine.split(template.render(values)));
        assertThrows(UserError.class, () -> template.render(Map.of("recipient", "Jane")));
    }

    @Test
    void chunkAdjacencyIsDimensionScopedAndOverflowSafe() {
        ChunkKey first = ChunkKey.parse("minecraft:overworld|-1|0");
        assertTrue(first.adjacent(new ChunkKey("minecraft:overworld", 0, 0)));
        assertFalse(first.adjacent(new ChunkKey("minecraft:the_nether", 0, 0)));
        assertFalse(first.adjacent(new ChunkKey("minecraft:overworld", 0, 1)));
        assertThrows(UserError.class, () -> ChunkKey.parse("minecraft:overworld|2147483647|0"));
        assertThrows(UserError.class, () -> ChunkKey.parse("invalid|0|0"));
    }

    @Test
    void bordersHideOnlyEdgesInsideTheSelectedGovernment() {
        var first = territory(0, 0, "nation", "state", "city-a");
        var second = territory(1, 0, "nation", "state", "city-b");
        TerritorySnapshot snapshot = new TerritorySnapshot("minecraft:overworld", 0, 0, 8, List.of(first, second));
        assertEquals(0, BoundaryEdges.of(snapshot, BoundaryEdges.Mode.OFF).size());
        assertEquals(7, BoundaryEdges.of(snapshot, BoundaryEdges.Mode.CHUNK).size());
        assertEquals(7, BoundaryEdges.of(snapshot, BoundaryEdges.Mode.CITY).size());
        assertEquals(6, BoundaryEdges.of(snapshot, BoundaryEdges.Mode.STATE).size());
        assertEquals(6, BoundaryEdges.of(snapshot, BoundaryEdges.Mode.NATION).size());
        assertEquals(BoundaryEdges.Mode.OFF, BoundaryEdges.Mode.NATION.next());
    }

    @Test
    void absentEconomyCannotBypassFees() {
        EconomyAccess.UNAVAILABLE.transfer("player:a", "nation:b", 0, "Free action");
        assertThrows(UserError.class, () -> EconomyAccess.UNAVAILABLE.transfer("player:a", "nation:b", 1, "Fee"));
        assertThrows(UserError.class, () -> EconomyAccess.UNAVAILABLE.balance("player:a"));
    }

    @Test
    void unsynchronizedChunksAreNotRenderedAsFalseNationalBorders() {
        TerritorySnapshot snapshot = new TerritorySnapshot("minecraft:overworld", 0, 0, 8,
                List.of(territory(8, 0, "nation", "state", "city")));
        assertEquals(3, BoundaryEdges.of(snapshot, BoundaryEdges.Mode.NATION).size());
    }

    @Test
    void unassignedNationalLandDoesNotCreateImaginaryStateOrCityBorders() {
        var national = new TerritorySnapshot.Territory(0, 0, "nation", null, null,
                "Nation", null, null, "nation:nation", 0, 0, "Nation");
        var snapshot = new TerritorySnapshot("minecraft:overworld", 0, 0, 8, List.of(national));
        assertEquals(4, BoundaryEdges.of(snapshot, BoundaryEdges.Mode.NATION).size());
        assertEquals(0, BoundaryEdges.of(snapshot, BoundaryEdges.Mode.STATE).size());
        assertEquals(0, BoundaryEdges.of(snapshot, BoundaryEdges.Mode.CITY).size());
    }

    @Test
    void resultRowsExposeCopyableIdsAccountsAndChunkKeys() {
        String id = "01234567-89ab-cdef-0123-456789abcdef";
        assertEquals(id, ResultSelection.identifier("Listing " + id + " at minecraft:overworld|1|2"));
        assertEquals("company:" + id, ResultSelection.identifier("Account company:" + id + ": $12.00"));
        assertEquals("escrow:contract:" + id, ResultSelection.identifier("Escrow escrow:contract:" + id + " funded"));
        assertEquals("minecraft:the_nether|-3|4", ResultSelection.identifier("Claim minecraft:the_nether|-3|4"));
        assertEquals("minecraft:wheat", ResultSelection.identifier("minecraft:wheat = $0.25"));
        assertEquals("Plain text", ResultSelection.identifier("  Plain text  "));
    }

    private static TerritorySnapshot.Territory territory(int x, int z, String nation, String state, String city) {
        return new TerritorySnapshot.Territory(x, z, nation, state, city, nation, state, city, "city:" + city, 0x45AA66, 0);
    }
}
