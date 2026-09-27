package dev.hycolony.core.kernel.nav;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.kernel.port.CitizenBodies;
import dev.hycolony.core.kernel.port.NavStatus;
import java.util.Optional;
import java.util.function.LongSupplier;
import org.jspecify.annotations.Nullable;

/**
 * One body's non-blocking walks (MC AbstractEntityAIBasic.walkToBuilding / walkToBlock), called again each AI step
 * until they return true. No walk blocks forever: a nav that ends anywhere counts as arrived, and the
 * {@link StuckHandler} repaths, teleports, then gives up on a body that makes no progress, whatever the nav reports.
 */
public final class BodyWalker {
    /** Blocks from the target's centre within which the body has arrived. */
    public static final int ARRIVAL_RANGE = 2;

    private final CitizenBodies bodies;
    private final BodyId body;
    private final LongSupplier clock;
    private final StuckHandler stuck = new StuckHandler();
    private @Nullable BlockPos navTarget;
    /** A target whose walk ended (nav result or given up): the body works from where it stands. */
    private @Nullable BlockPos settled;

    public BodyWalker(CitizenBodies bodies, BodyId body, LongSupplier clock) {
        this.bodies = bodies;
        this.body = body;
        this.clock = clock;
    }

    /** True while a walk is under way (for the citizen window). */
    public boolean walking() {
        BlockPos target = navTarget;
        return target != null
                && !target.equals(settled)
                && bodies.position(body).map(p -> !within(p, target)).orElse(false);
    }

    /** The body's block; empty while it has no body. */
    public Optional<BlockPos> at() {
        return bodies.position(body).map(Vec3::toBlockPos);
    }

    /**
     * True once within {@link #ARRIVAL_RANGE} of {@code to}, or once the walk to it ended (nav arrived, blocked or
     * failed, or the stuck handler gave up); false while walking or without a body. Moves the body only when the
     * target changes, or when the stuck handler says so.
     */
    public boolean walkTo(BlockPos to) {
        return walkTo(to, true);
    }

    /** {@link #walkTo(BlockPos)}; the stuck handler teleports to {@code to} only when {@code teleportAllowed}. */
    public boolean walkTo(BlockPos to, boolean teleportAllowed) {
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
            stuck.start(Vec3.center(to), p, now, teleportAllowed);
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
        return unstick(to, p, now);
    }

    /** Follows the stuck handler's advice; true once it gave up (the walk then counts as ended). */
    private boolean unstick(BlockPos to, Vec3 p, long now) {
        switch (stuck.check(p, now)) {
            case REPATH -> bodies.moveTo(body, Vec3.center(to));
            case TELEPORT -> bodies.teleport(body, Vec3.center(to));
            case GIVE_UP -> {
                settled = to;
                return true;
            }
            case NONE -> {}
        }
        return false;
    }

    private static boolean within(Vec3 p, BlockPos to) {
        return p.distance(Vec3.center(to)) <= ARRIVAL_RANGE;
    }
}
