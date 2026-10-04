package dev.hyangler.core.port;

/** Blocks around a bobber. A port: never throws; an unloaded block is OTHER and sees no sky. */
public interface BlockProbe {
    /** What the block at (x, y, z) is. */
    BlockKind kind(int x, int y, int z);

    /** Whether the block at (x, y, z) sees the sky. */
    boolean skyVisible(int x, int y, int z);
}
