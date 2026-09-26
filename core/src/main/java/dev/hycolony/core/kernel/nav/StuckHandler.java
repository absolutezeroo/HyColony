package dev.hycolony.core.kernel.nav;

import dev.hycolony.core.kernel.Vec3;

/**
 * MineColonies PathingStuckHandler (as MinecoloniesAdvancedPathNavigate configures it for citizens: teleport on full
 * stuck), reduced to what a port without path nodes can observe: progress is the body's own position, whatever the
 * nav reports. One walk at a time: {@link #start} for each new destination, then {@link #check} as often as wanted.
 *
 * <ul>
 *   <li>Checked every {@link #CHECK_INTERVAL} ticks (MC checkStuckDelay).</li>
 *   <li>No progress of {@link #PROGRESS_DIST} for {@link #DELAY_BEFORE_ACTIONS} ticks: the walk is issued again
 *       (MC stuck level 0, recalc). Still none {@link #NEXT_ACTION_DELAY} ticks later: teleport to the destination
 *       (MC's later levels end in completeStuckAction, which teleports citizens near their goal). Still none after
 *       that: give up, the caller works from where the body stands.</li>
 *   <li>Within {@link #MIN_TARGET_DIST} of the destination the body is never "stuck" (MC resets its timers there):
 *       without progress it simply gives up the walk, close enough.</li>
 *   <li>MC's global timeout: the same destination for more than max({@link #MIN_TP_DELAY},
 *       {@link #TIME_PER_BLOCK} x max({@link #MIN_DIST_FOR_TP}, Manhattan distance)) ticks teleports, even while the
 *       body moves (circling).</li>
 * </ul>
 *
 * <p>Deviation from MC: no path nodes to skip, no move-away, ladders or block breaking (Hytale's nav owns the
 * path); the levels collapse to repath, teleport, give up.
 */
public final class StuckHandler {
    public enum Action { NONE, REPATH, TELEPORT, GIVE_UP }

    static final int CHECK_INTERVAL = 10;
    static final int DELAY_BEFORE_ACTIONS = 5 * 20;
    static final int NEXT_ACTION_DELAY = 200;
    static final double MIN_TARGET_DIST = 3;
    static final int MIN_TP_DELAY = 120 * 20;
    static final int TIME_PER_BLOCK = 200;
    static final int MIN_DIST_FOR_TP = 10;
    /** Ours: MC counts path nodes passed; a body that moved a block made progress. */
    static final double PROGRESS_DIST = 1;

    private Vec3 destination;
    private long startTick;
    private long lastCheck;
    private Vec3 lastPos;
    private long lastProgress;
    private int level;
    private boolean teleported;

    public void start(Vec3 destination, Vec3 from, long now) {
        this.destination = destination;
        startTick = now;
        lastCheck = now;
        lastPos = from;
        lastProgress = now;
        level = 0;
        teleported = false;
    }

    public Action check(Vec3 pos, long now) {
        if (destination == null || now - lastCheck < CHECK_INTERVAL) {
            return Action.NONE;
        }
        lastCheck = now;
        if (!teleported && now - startTick > globalTimeout(pos)) {
            return teleport(pos, now);
        }
        if (pos.distance(lastPos) >= PROGRESS_DIST) {
            lastPos = pos;
            lastProgress = now;
            level = 0;
            return Action.NONE;
        }
        if (now - lastProgress < (level == 0 ? DELAY_BEFORE_ACTIONS : NEXT_ACTION_DELAY)) {
            return Action.NONE;
        }
        lastProgress = now;
        if (pos.distance(destination) < MIN_TARGET_DIST || teleported) {
            destination = null;
            return Action.GIVE_UP;
        }
        return level++ == 0 ? Action.REPATH : teleport(pos, now);
    }

    private Action teleport(Vec3 pos, long now) {
        teleported = true;
        lastPos = pos; // a teleport that worked shows as progress; one that did not, as none
        lastProgress = now;
        level = 1;
        return Action.TELEPORT;
    }

    private long globalTimeout(Vec3 pos) {
        long manhattan = Math.round(Math.abs(pos.x() - destination.x()) + Math.abs(pos.y() - destination.y())
                + Math.abs(pos.z() - destination.z()));
        return Math.max(MIN_TP_DELAY, (long) TIME_PER_BLOCK * Math.max(MIN_DIST_FOR_TP, manhattan));
    }
}
