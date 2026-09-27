package dev.hycolony.core.kernel.port;

import dev.hycolony.core.kernel.BlockPos;
import java.util.Optional;
import java.util.UUID;

public interface WorldQuery {
    /** Whether the chunk containing {@code pos} is currently loaded. */
    boolean isLoaded(BlockPos pos);

    /** Where {@code player} spawns in this world (MC Level.getSharedSpawnPos); empty if unknown (never throws). */
    Optional<BlockPos> spawnPoint(UUID player);

    /** Whether it rains or snows at {@code pos} (MC Level.isRaining); false when unknown (never throws). */
    boolean isRainingAt(BlockPos pos);
}
