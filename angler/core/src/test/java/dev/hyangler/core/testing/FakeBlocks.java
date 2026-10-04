package dev.hyangler.core.testing;

import dev.hyangler.core.port.BlockKind;
import dev.hyangler.core.port.BlockProbe;
import java.util.HashMap;
import java.util.Map;

/** A world of blocks for tests: air everywhere but what was filled. */
public final class FakeBlocks implements BlockProbe {
    private final Map<Long, BlockKind> blocks = new HashMap<>();
    private boolean sky = true;

    /** Fills the box from (x0, y0, z0) to (x1, y1, z1), both included, with kind. */
    public FakeBlocks fill(int x0, int y0, int z0, int x1, int y1, int z1, BlockKind kind) {
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    blocks.put(key(x, y, z), kind);
                }
            }
        }
        return this;
    }

    /** Whether every block sees the sky. */
    public FakeBlocks sky(boolean visible) {
        this.sky = visible;
        return this;
    }

    @Override
    public BlockKind kind(int x, int y, int z) {
        return blocks.getOrDefault(key(x, y, z), BlockKind.AIR);
    }

    @Override
    public boolean skyVisible(int x, int y, int z) {
        return sky;
    }

    private static long key(int x, int y, int z) {
        return ((long) x & 0x1FFFFF) << 42 | ((long) y & 0x1FFFFF) << 21 | (long) z & 0x1FFFFF;
    }
}
