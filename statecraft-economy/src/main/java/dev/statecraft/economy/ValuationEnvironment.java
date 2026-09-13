package dev.statecraft.economy;

import dev.statecraft.api.GovernanceAccess;

@FunctionalInterface
public interface ValuationEnvironment {
    record Conditions(String biome, int biomeBps, long distanceChunks, int nearbyClaims) {}
    Conditions sample(GovernanceAccess.ClaimView claim);

    ValuationEnvironment NEUTRAL = claim ->
            new Conditions("unknown", 10_000, Math.max(Math.abs((long) claim.x()), Math.abs((long) claim.z())), 0);
}
