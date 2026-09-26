package dev.hycolony.core.testing;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.port.WorldQuery;
import java.util.Optional;
import java.util.UUID;

public final class FakeWorld implements WorldQuery {
    public boolean loaded = true;
    /** The world spawn; null when unknown. */
    public BlockPos spawn;

    @Override
    public boolean isLoaded(BlockPos pos) {
        return loaded;
    }

    @Override
    public Optional<BlockPos> spawnPoint(UUID player) {
        return Optional.ofNullable(spawn);
    }
}
