package dev.statecraft.runtime;

import dev.statecraft.api.Actor;
import dev.statecraft.api.ChunkKey;
import dev.statecraft.api.GovernanceAccess;
import dev.statecraft.api.GovernanceAccess.GovernmentView;
import dev.statecraft.api.TerritorySnapshot;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

final class TerritorySnapshots {
    private TerritorySnapshots() {}

    static TerritorySnapshot around(Actor actor, GovernanceAccess governance, Consumer<String> orphan) {
        int radius = TerritorySnapshot.MAX_RADIUS;
        Map<String, Optional<GovernmentView>> governments = new HashMap<>();
        var territories = new ArrayList<TerritorySnapshot.Territory>();
        int minX = Math.max(-ChunkKey.MAX_COORDINATE, actor.chunkX() - radius);
        int maxX = Math.min(ChunkKey.MAX_COORDINATE, actor.chunkX() + radius);
        int minZ = Math.max(-ChunkKey.MAX_COORDINATE, actor.chunkZ() - radius);
        int maxZ = Math.min(ChunkKey.MAX_COORDINATE, actor.chunkZ() + radius);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                var found = governance.claim(new ChunkKey(actor.dimension(), x, z).toString());
                if (found.isEmpty()) {
                    continue;
                }
                var claim = found.get();
                var nation = governments.computeIfAbsent(claim.nationId(), governance::government);
                var state = governments.computeIfAbsent(claim.stateId(), governance::government);
                var city = governments.computeIfAbsent(claim.cityId(), governance::government);
                if (nation.isEmpty() || state.isEmpty() || city.isEmpty()) {
                    orphan.accept(claim.key());
                    continue;
                }
                int color = 0x606060 | (claim.nationId().hashCode() & 0x9F9F9F);
                territories.add(new TerritorySnapshot.Territory(x, z, nation.get().id(), state.get().id(), city.get().id(),
                        nation.get().name(), state.get().name(), city.get().name(), claim.ownerAccount(),
                        color, claim.improvements()));
            }
        }
        return new TerritorySnapshot(actor.dimension(), actor.chunkX(), actor.chunkZ(), radius, territories);
    }
}
