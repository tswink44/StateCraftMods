package dev.statecraft.economy.forge;

import dev.statecraft.api.ChunkKey;
import dev.statecraft.api.GovernanceAccess;
import dev.statecraft.economy.ValuationEnvironment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;

public final class ForgeValuationEnvironment implements ValuationEnvironment {
    private final MinecraftServer server;
    private final GovernanceAccess governance;

    public ForgeValuationEnvironment(MinecraftServer server, GovernanceAccess governance) {
        this.server = server;
        this.governance = governance;
    }

    @Override public Conditions sample(GovernanceAccess.ClaimView claim) {
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, new ResourceLocation(claim.dimension())));
        long distance = Math.max(Math.abs((long) claim.x()), Math.abs((long) claim.z()));
        String biome = "unloaded";
        int factor = 10_000;
        if (level != null) {
            distance = Math.max(Math.abs((long) claim.x() - (level.getSharedSpawnPos().getX() >> 4)),
                    Math.abs((long) claim.z() - (level.getSharedSpawnPos().getZ() >> 4)));
            if (level.hasChunk(claim.x(), claim.z())) {
                int x = claim.x() * 16 + 8, z = claim.z() * 16 + 8;
                int y = Math.max(level.getMinBuildHeight(), Math.min(level.getMaxBuildHeight() - 1,
                        level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z)));
                biome = level.getBiome(new BlockPos(x, y, z)).unwrapKey().map(key -> key.location().toString()).orElse("unknown");
                if (biome.contains("ocean")) factor = 8000;
                else if (biome.contains("desert") || biome.contains("badlands")) factor = 9000;
                else if (biome.contains("swamp")) factor = 9500;
                else if (biome.contains("forest")) factor = 10_500;
                else if (biome.contains("plains")) factor = 11_000;
            }
        }
        int nearby = 0;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (dx == 0 && dz == 0) continue;
                long x = (long) claim.x() + dx, z = (long) claim.z() + dz;
                if (x < -1_875_000 || x > 1_875_000 || z < -1_875_000 || z > 1_875_000) continue;
                if (governance.claim(new ChunkKey(claim.dimension(), (int) x, (int) z).toString()).isPresent()) nearby++;
            }
        }
        return new Conditions(biome, factor, distance, nearby);
    }
}
