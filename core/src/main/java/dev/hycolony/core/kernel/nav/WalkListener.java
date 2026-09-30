package dev.hycolony.core.kernel.nav;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.port.NavStatus;

/**
 * Hears how one body's walks go, for diagnostics only: never for game rules. Called on the world's thread, once per
 * walk start, stuck action and walk end; the objects it receives are the walker's own, not copies. A walk its caller
 * drops for another target gets no end: the next start closes it. A body that walks again after arriving (it left the
 * reach) starts a new walk to the same target; one swaying at the edge of its reach while the nav still runs starts and
 * ends walks as often.
 */
public interface WalkListener {
    /** Hears nothing. */
    WalkListener NONE = new WalkListener() {};

    /** A walk to {@code target} started, the body at {@code from}. */
    default void walkStarted(BlockPos target, Vec3 from) {}

    /**
     * The walk to {@code target} ended {@code how}, the body at {@code at}, {@code distance} blocks from what it walked
     * to (the block a close walk is for, else the target). {@code nav} is the nav's status when the walk ended
     * ({@link WalkEnd#NAV_ENDED}, {@link WalkEnd#GAVE_UP}); {@code MOVING} when it ended close, without waiting for
     * the nav.
     */
    default void walkEnded(BlockPos target, Vec3 at, WalkEnd how, double distance, NavStatus nav) {}

    /** The stuck handler took {@code action} on the walk to {@code target}, the body at {@code at}. */
    default void stuck(BlockPos target, Vec3 at, StuckHandler.Action action) {}
}
