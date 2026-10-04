package dev.hyangler.core.context;

import dev.hyangler.core.port.BlockKind;
import dev.hyangler.core.port.BlockProbe;

/** The depth of water under a bobber: water blocks from its own block down, bounded (CLAUDE.md § 4). */
public final class WaterColumn {
    /** The deepest a scan looks, in blocks. */
    static final int SCAN_LIMIT = 32;

    private WaterColumn() {}

    /** Water blocks from (x, y, z) down, its own block included, at most SCAN_LIMIT. */
    public static int depth(BlockProbe probe, int x, int y, int z) {
        int depth = 0;
        while (depth < SCAN_LIMIT) {
            BlockKind kind = probe.kind(x, y - depth, z);
            if (kind != BlockKind.WATER_SOURCE && kind != BlockKind.WATER_FLOWING) {
                break;
            }
            depth++;
        }
        return depth;
    }
}
