package dev.hycolony.core.testing;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.port.WorldQuery;

public final class FakeWorld implements WorldQuery {
    public boolean loaded = true;

    @Override
    public boolean isLoaded(BlockPos pos) {
        return loaded;
    }
}
