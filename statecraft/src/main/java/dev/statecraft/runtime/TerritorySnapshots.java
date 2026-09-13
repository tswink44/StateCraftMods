package dev.statecraft.runtime;

import dev.statecraft.api.Actor;
import dev.statecraft.api.ChunkKey;
import dev.statecraft.api.GovernanceAccess;
import dev.statecraft.api.GovernanceAccess.GovernmentView;
import dev.statecraft.api.TerritorySnapshot;
import dev.statecraft.api.ui.DisplayText;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

final class TerritorySnapshots {
    private TerritorySnapshots() {}

    static TerritorySnapshot around(Actor actor, GovernanceAccess governance, Consumer<String> orphan) {
        return around(actor, governance, orphan, id -> Optional.empty());
    }

    static TerritorySnapshot around(Actor actor, GovernanceAccess governance, Consumer<String> orphan,
                                    Function<UUID, Optional<String>> playerNames) {
        int radius = TerritorySnapshot.MAX_RADIUS;
        Map<String, Optional<GovernmentView>> governments = new HashMap<>();
        Map<String, String> owners = new HashMap<>();
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
                var state = claim.stateId() == null ? Optional.<GovernmentView>empty()
                        : governments.computeIfAbsent(claim.stateId(), governance::government);
                var city = claim.cityId() == null ? Optional.<GovernmentView>empty()
                        : governments.computeIfAbsent(claim.cityId(), governance::government);
                if (nation.isEmpty() || claim.stateId() != null && state.isEmpty()
                        || claim.cityId() != null && city.isEmpty() || city.isPresent() && state.isEmpty()) {
                    orphan.accept(claim.key());
                    continue;
                }
                int color = 0x606060 | (claim.nationId().hashCode() & 0x9F9F9F);
                territories.add(new TerritorySnapshot.Territory(x, z, nation.get().id(), state.map(GovernmentView::id).orElse(""),
                        city.map(GovernmentView::id).orElse(""), nation.get().name(), state.map(GovernmentView::name).orElse(""),
                        city.map(GovernmentView::name).orElse(""), claim.ownerAccount(),
                        color, claim.improvements(), owners.computeIfAbsent(claim.ownerAccount(),
                                account -> owner(account, governance, governments, playerNames))));
            }
        }
        return new TerritorySnapshot(actor.dimension(), actor.chunkX(), actor.chunkZ(), radius, territories);
    }

    private static String owner(String account, GovernanceAccess governance, Map<String, Optional<GovernmentView>> governments,
                                Function<UUID, Optional<String>> playerNames) {
        String[] parts = account.split(":", 2);
        if (parts.length != 2) return "Private owner";
        String name = switch (parts[0]) {
            case "player" -> {
                try {
                    yield DisplayText.name(playerNames.apply(UUID.fromString(parts[1])).orElse(null), "Former player");
                } catch (IllegalArgumentException invalid) {
                    yield "Unknown player";
                }
            }
            case "company" -> governance.company(parts[1]).map(company -> company.name() + " (Company)").orElse("Former company");
            case "nation", "state", "city" -> governments.computeIfAbsent(parts[1], governance::government)
                    .map(government -> government.name() + " (" + DisplayText.words(parts[0]) + ")").orElse("Former government");
            default -> "Private owner";
        };
        return name.length() <= 128 ? name : name.substring(0, 125) + "...";
    }
}
