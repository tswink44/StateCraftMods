package dev.statecraft.runtime;

import dev.statecraft.api.Actor;
import dev.statecraft.api.ChunkKey;
import dev.statecraft.api.GovernanceAccess;
import dev.statecraft.api.GovernanceAccess.ClaimView;
import dev.statecraft.api.GovernanceAccess.GovernmentView;
import dev.statecraft.api.GovernanceAccess.Kind;
import dev.statecraft.api.TerritorySnapshot;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TerritorySnapshotsTest {
    private final UUID player = UUID.randomUUID();
    private final Map<String, ClaimView> claims = new HashMap<>();
    private final Map<String, GovernmentView> governments = new HashMap<>();
    private int claimReads;
    private int governmentReads;
    private final GovernanceAccess governance = (GovernanceAccess) Proxy.newProxyInstance(
            GovernanceAccess.class.getClassLoader(), new Class<?>[]{GovernanceAccess.class}, (proxy, method, args) -> {
                return switch (method.getName()) {
                    case "claim" -> {
                        claimReads++;
                        ChunkKey.parse((String) args[0]);
                        yield Optional.ofNullable(claims.get(args[0]));
                    }
                    case "government" -> {
                        governmentReads++;
                        yield Optional.ofNullable(governments.get(args[0]));
                    }
                    default -> throw new AssertionError("Unbounded or unexpected lookup: " + method.getName());
                };
            });

    @Test
    void mapUsesAtMost289PointLookupsAndCachesOnlyVisibleGovernments() {
        hierarchy();
        claim("minecraft:overworld", -8, 2);
        claim("minecraft:overworld", 8, -1);
        claim("minecraft:overworld", 9, 0);
        claim("minecraft:the_nether", 0, 0);
        var orphaned = new ArrayList<String>();

        TerritorySnapshot snapshot = TerritorySnapshots.around(actor(0, 0), governance, orphaned::add);

        assertEquals(TerritorySnapshot.MAX_TERRITORIES, claimReads);
        assertEquals(3, governmentReads);
        assertEquals(2, snapshot.territories().size());
        assertEquals(-8, snapshot.territories().get(0).x());
        assertEquals(8, snapshot.territories().get(1).x());
        assertEquals("player:" + player, snapshot.territories().get(0).ownerAccount());
        assertEquals("Nation", snapshot.territories().get(0).nationName());
        assertEquals(4, snapshot.territories().get(0).improvements());
        assertTrue(orphaned.isEmpty());
    }

    @Test
    void worldBorderClipsLookupsWithoutInvalidChunkKeys() {
        var snapshot = TerritorySnapshots.around(actor(ChunkKey.MAX_COORDINATE, -ChunkKey.MAX_COORDINATE),
                governance, key -> fail("Unexpected orphan: " + key));
        assertEquals(81, claimReads);
        assertEquals(0, governmentReads);
        assertEquals(ChunkKey.MAX_COORDINATE, snapshot.centerX());
        assertEquals(-ChunkKey.MAX_COORDINATE, snapshot.centerZ());
        assertTrue(snapshot.territories().isEmpty());
    }

    @Test
    void missingHierarchyIsReportedAndNotRendered() {
        hierarchy();
        governments.remove("c");
        claim("minecraft:overworld", 0, 0);
        claim("minecraft:overworld", 1, 0);
        var orphaned = new ArrayList<String>();
        var snapshot = TerritorySnapshots.around(actor(0, 0), governance, orphaned::add);
        assertTrue(snapshot.territories().isEmpty());
        assertEquals(2, orphaned.size());
        assertEquals(3, governmentReads);
    }

    private Actor actor(int x, int z) {
        return new Actor(player, "Viewer", false, "minecraft:overworld", x, z);
    }

    private void hierarchy() {
        governments.put("n", new GovernmentView("n", Kind.NATION, "Nation", null, "n", player, Set.of(player), Map.of()));
        governments.put("s", new GovernmentView("s", Kind.STATE, "State", "n", "n", player, Set.of(player), Map.of()));
        governments.put("c", new GovernmentView("c", Kind.CITY, "City", "s", "n", player, Set.of(player), Map.of()));
    }

    private void claim(String dimension, int x, int z) {
        String key = new ChunkKey(dimension, x, z).toString();
        claims.put(key, new ClaimView(key, dimension, x, z, "c", "s", "n", "player:" + player, 4, 1));
    }
}
