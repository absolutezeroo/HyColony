package dev.hycolony.core.testing;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.port.WorldQuery;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class FakeWorld implements WorldQuery {
    public boolean loaded = true;
    /** Positions read as unloaded even while {@link #loaded}. */
    public final Set<BlockPos> unloaded = new HashSet<>();
    /** The world spawn; null when unknown. */
    public BlockPos spawn;
    /** Rain or snow everywhere. */
    public boolean raining;
    /** The biome everywhere; empty when unknown. */
    public Optional<String> biome = Optional.empty();

    @Override
    public boolean isLoaded(BlockPos pos) {
        return loaded && !unloaded.contains(pos);
    }

    @Override
    public Optional<BlockPos> spawnPoint(UUID player) {
        return Optional.ofNullable(spawn);
    }

    @Override
    public boolean isRainingAt(BlockPos pos) {
        return raining;
    }

    @Override
    public Optional<String> biome(BlockPos pos) {
        return biome;
    }
}
