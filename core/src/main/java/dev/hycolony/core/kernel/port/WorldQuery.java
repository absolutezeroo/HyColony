package dev.hycolony.core.kernel.port;

import dev.hycolony.core.kernel.BlockPos;

public interface WorldQuery {
    /** Whether the chunk containing {@code pos} is currently loaded. */
    boolean isLoaded(BlockPos pos);
}
