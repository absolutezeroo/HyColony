package dev.hycolony.core.construction;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.nav.StuckHandler;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.kernel.port.CitizenBodies;
import dev.hycolony.core.kernel.port.NavStatus;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * The builder's non-blocking walks (MC walkToBuilding / walkToConstructionSite), over the same port as wandering. No
 * walk blocks the AI forever: a nav that ends anywhere counts as arrived, and the {@link StuckHandler} repaths,
 * teleports, then gives up on a body that makes no progress, whatever the nav reports.
 */
final class BuilderWalker {
    static final int ARRIVAL_RANGE = 2;

    private final CitizenBodies bodies;
    private final BodyId body;
    private final LongSupplier clock;
    private final StuckHandler stuck = new StuckHandler();
    private BlockPos navTarget;
    /** A target whose walk ended (nav result or given up): the builder works from where it stands. */
    private BlockPos settled;
    private BlockPos workPos;
    /** The block the work spot was already chosen again for, because it was out of reach from the first one. */
    private BlockPos repickedFor;

    BuilderWalker(CitizenBodies bodies, BodyId body, LongSupplier clock) {
        this.bodies = bodies;
        this.body = body;
        this.clock = clock;
    }

    void forgetWorkPos() {
        workPos = null;
        repickedFor = null;
    }

    /** True while a walk is under way (for the citizen window). */
    boolean walking() {
        return navTarget != null && !navTarget.equals(settled) && bodies.position(body)
                .map(p -> !within(p, navTarget)).orElse(false);
    }

    /**
     * EntityAIStructureBuilder.walkToConstructionSite: walks to the block's work spot and keeps it while the block is
     * within {@link WorkSpot#REACH} of where the builder stands; beyond, a new spot is chosen, once per block (a
     * block still out of reach from there is worked from where the builder got).
     */
    boolean walkToWorkPos(BlockPos block, Supplier<BlockPos> spot) {
        if (workPos == null) {
            workPos = spot.get();
            repickedFor = null;
        }
        if (!walkTo(workPos)) {
            return false;
        }
        BlockPos at = bodies.position(body).map(Vec3::toBlockPos).orElse(null);
        if (at == null || WorkSpot.inReach(at, block) || block.equals(repickedFor)) {
            return true;
        }
        repickedFor = block;
        workPos = spot.get();
        return walkTo(workPos);
    }

    /**
     * True once within {@link #ARRIVAL_RANGE} of {@code to}, or once the walk to it ended (nav arrived, blocked or
     * failed, or the stuck handler gave up): the builder then works from where it got rather than stalling. Moves the
     * body only when the target changes, or when the stuck handler says so.
     */
    boolean walkTo(BlockPos to) {
        Vec3 p = bodies.position(body).orElse(null);
        if (p == null) {
            return false;
        }
        if (within(p, to) || to.equals(settled)) {
            return true;
        }
        long now = clock.getAsLong();
        if (!to.equals(navTarget)) {
            navTarget = to;
            settled = null;
            bodies.moveTo(body, Vec3.center(to));
            stuck.start(Vec3.center(to), p, now);
            return false;
        }
        NavStatus s = bodies.navStatus(body);
        if (s != NavStatus.MOVING && s != NavStatus.IDLE) {
            settled = to;
            return true;
        }
        if (s == NavStatus.IDLE) {
            bodies.moveTo(body, Vec3.center(to)); // the walk was dropped (e.g. by lookAt): ask again
        }
        switch (stuck.check(p, now)) {
            case REPATH -> bodies.moveTo(body, Vec3.center(to));
            case TELEPORT -> bodies.teleport(body, Vec3.center(to));
            case GIVE_UP -> {
                settled = to;
                return true;
            }
            case NONE -> { }
        }
        return false;
    }

    private static boolean within(Vec3 p, BlockPos to) {
        return p.distance(Vec3.center(to)) <= ARRIVAL_RANGE;
    }
}
