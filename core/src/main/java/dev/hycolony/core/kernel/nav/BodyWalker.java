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
 * until they return true. No walk blocks forever: the {@link StuckHandler} repaths, teleports, then gives up on a body
 * that makes no progress, whatever the nav reports. A plain walk also counts a nav that ends anywhere as arrived; a
 * walk close to a block ({@link #walkCloseTo}) only one that ends near that block.
 */
public final class BodyWalker {
    /** Blocks from the target's centre within which the body has arrived. */
    public static final int ARRIVAL_RANGE = 2;
    /** MC EntityNavigationUtils.REACHED_DIST: blocks from the desired block within which a walk to it has arrived. */
    private static final double REACHED_DIST = 1.5;

    private final CitizenBodies bodies;
    private final BodyId body;
    private final LongSupplier clock;
    private final StuckHandler stuck = new StuckHandler();
    private final WalkListener listener;
    private @Nullable BlockPos navTarget;
    /** A target whose plain walk ended where its nav did, or whose walk was given up: the body works from there. */
    private @Nullable BlockPos settled;
    /** The walk to {@link #navTarget} had arrived: walking there again is watched anew by the stuck handler. */
    private boolean arrived;
    /** The stuck handler teleported the body during the walk under way: its end is {@link WalkEnd#TELEPORTED}. */
    private boolean teleported;
    /** Walks started so far. */
    private int walks;

    public BodyWalker(CitizenBodies bodies, BodyId body, LongSupplier clock) {
        this(bodies, body, clock, WalkListener.NONE);
    }

    /** A walker whose walks {@code listener} hears (diagnostics). */
    public BodyWalker(CitizenBodies bodies, BodyId body, LongSupplier clock, WalkListener listener) {
        this.bodies = bodies;
        this.body = body;
        this.clock = clock;
        this.listener = listener;
    }

    /** True while a walk is under way (for the citizen window). */
    public boolean walking() {
        BlockPos target = navTarget;
        return target != null
                && !target.equals(settled)
                && bodies.position(body).map(p -> !within(p, target)).orElse(false);
    }

    /** How many walks started so far: it changes whenever a walk to a new target starts. */
    public int walks() {
        return walks;
    }

    /**
     * Forgets the walk under way or ended, so the next one starts afresh from wherever the body now is: another AI (a
     * sleep, a meal) has moved it since.
     */
    public void forget() {
        navTarget = null;
        settled = null;
        arrived = false;
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

    /**
     * {@link #walkTo(BlockPos)}, already arrived when within {@code range} blocks of {@code to}: a walk under way to
     * {@code to} then counts as arrived (its end is heard once), and walking there again later is watched anew. A walk
     * started before is not stopped: the body may finish it, which only shows.
     */
    public boolean walkTo(BlockPos to, int range) {
        Vec3 p = bodies.position(body).orElse(null);
        if (p != null && p.distance(Vec3.center(to)) <= range) {
            if (to.equals(navTarget)) {
                ended(to, p, null, WalkEnd.CLOSE, NavStatus.MOVING);
            }
            return true;
        }
        return walkTo(to);
    }

    /** {@link #walkTo(BlockPos)}; the stuck handler teleports to {@code to} only when {@code teleportAllowed}. */
    public boolean walkTo(BlockPos to, boolean teleportAllowed) {
        return walk(to, teleportAllowed, null, 0);
    }

    /**
     * MC EntityNavigationUtils.walkCloseToXNearY / walkToPos: walks to {@code stand}, a cell by the block
     * {@code desired}; true once within {@link #REACHED_DIST} of {@code desired}, or once the nav ended within
     * {@code reach} of it. A nav that ends farther (a partial path, onto a roof) walks again as MC re-paths; the stuck
     * handler then teleports to {@code stand} when {@code verified}, else gives up, which ends the walk.
     *
     * <p>Deviation from MC: {@link #REACHED_DIST} also counts while the nav still runs, as Hytale's nav may keep
     * steering at its goal.
     */
    public boolean walkCloseTo(BlockPos stand, BlockPos desired, int reach, boolean verified) {
        return walk(stand, verified, desired, reach);
    }

    /** A walk to {@code to}; with {@code desired}, it arrives only close to that block (see {@link #walkCloseTo}). */
    private boolean walk(BlockPos to, boolean teleportAllowed, @Nullable BlockPos desired, int reach) {
        Vec3 p = bodies.position(body).orElse(null);
        if (p == null) {
            return false;
        }
        if (alreadyThere(to, p, desired)) {
            return true;
        }
        long now = clock.getAsLong();
        if (!to.equals(navTarget)) {
            navTarget = to;
            settled = null;
            arrived = false;
            walks++;
            teleported = false;
            listener.walkStarted(to, p);
            bodies.moveTo(body, Vec3.center(to));
            stuck.start(Vec3.center(to), p, now, teleportAllowed);
            return false;
        }
        NavStatus s = bodies.navStatus(body);
        if (navDone(s, desired) && withinReach(p, desired, reach)) {
            return arrive(to, p, desired, s);
        }
        if (arrived) {
            arrived = false; // left since: a new walk, whose progress the stuck handler watches from now, once
            teleported = false;
            stuck.start(Vec3.center(to), p, now, teleportAllowed);
            listener.walkStarted(to, p);
        }
        if (s != NavStatus.MOVING) {
            bodies.moveTo(body, Vec3.center(to)); // dropped (e.g. by lookAt), ended too far off, or left since
        }
        return unstick(to, p, now, desired, s);
    }

    /** Whether the walk to {@code to} ended before, or the body got close; the walk under way then ends close. */
    private boolean alreadyThere(BlockPos to, Vec3 p, @Nullable BlockPos desired) {
        if (!to.equals(settled) && !close(p, to, desired)) {
            return false;
        }
        if (to.equals(navTarget)) { // no walk may be under way to another target
            ended(to, p, desired, WalkEnd.CLOSE, NavStatus.MOVING);
        }
        return true;
    }

    /** The walk to {@code to} arrived, its nav over; a plain walk ends wherever its nav ended. Returns true. */
    private boolean arrive(BlockPos to, Vec3 p, @Nullable BlockPos desired, NavStatus nav) {
        if (desired == null) {
            settled = to;
        }
        ended(to, p, desired, desired == null ? WalkEnd.NAV_ENDED : WalkEnd.IN_REACH, nav);
        return true;
    }

    /**
     * Marks the walk to {@code to} arrived, telling the listener the first time only; a close end after a teleport is
     * {@link WalkEnd#TELEPORTED}.
     */
    private void ended(BlockPos to, Vec3 p, @Nullable BlockPos desired, WalkEnd how, NavStatus nav) {
        if (!arrived) {
            WalkEnd end = how == WalkEnd.CLOSE && teleported ? WalkEnd.TELEPORTED : how;
            listener.walkEnded(to, p, end, p.distance(Vec3.center(desired == null ? to : desired)), nav);
        }
        arrived = true;
    }

    /**
     * Follows the stuck handler's advice; true once it gave up (the walk then counts as ended, the nav at
     * {@code nav}).
     */
    private boolean unstick(BlockPos to, Vec3 p, long now, @Nullable BlockPos desired, NavStatus nav) {
        StuckHandler.Action action = stuck.check(p, now);
        if (action != StuckHandler.Action.NONE) {
            listener.stuck(to, p, action);
        }
        switch (action) {
            case REPATH -> bodies.moveTo(body, Vec3.center(to));
            case TELEPORT -> {
                teleported = true;
                bodies.teleport(body, Vec3.center(to));
            }
            case GIVE_UP -> {
                settled = to;
                ended(to, p, desired, WalkEnd.GAVE_UP, nav);
                return true;
            }
            case NONE -> {}
        }
        return false;
    }

    /** Arrived without waiting for the nav: near {@code to}, or near {@code desired} when walking close to it. */
    private static boolean close(Vec3 p, BlockPos to, @Nullable BlockPos desired) {
        return desired == null ? within(p, to) : p.distance(Vec3.center(desired)) <= REACHED_DIST;
    }

    /**
     * Whether the nav is over: arrived, blocked or failed; for a walk close to a block also idle, the nav having ended
     * before (MC nav.isDone), where a plain walk takes idle for a dropped walk to ask again.
     */
    private static boolean navDone(NavStatus s, @Nullable BlockPos desired) {
        return s != NavStatus.MOVING && (desired != null || s != NavStatus.IDLE);
    }

    /** Where an ended nav counts as arrived: anywhere on a plain walk, else within {@code reach} of {@code desired}. */
    private static boolean withinReach(Vec3 p, @Nullable BlockPos desired, int reach) {
        return desired == null || p.distance(Vec3.center(desired)) <= reach;
    }

    private static boolean within(Vec3 p, BlockPos to) {
        return p.distance(Vec3.center(to)) <= ARRIVAL_RANGE;
    }
}
