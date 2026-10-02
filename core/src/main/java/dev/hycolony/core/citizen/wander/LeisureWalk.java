package dev.hycolony.core.citizen.wander;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.HutFootprint;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.nav.DangerousCells;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.kernel.port.CitizenBodies;
import dev.hycolony.core.kernel.port.NavStatus;
import java.util.Optional;
import java.util.function.IntConsumer;
import java.util.random.RandomGenerator;
import org.jspecify.annotations.Nullable;

/**
 * A citizen's leisure walk, MC EntityAICitizenWander's GO_TO_LEISURE_SITE then WANDER_AT_LEISURE_SITE: to the site,
 * then strolls in its building until it leaves, one time in 300.
 *
 * <p>Deviation from MC: a sub-state of IDLE, not states of their own; no tagged seats nor stands (HyColony's plans
 * have no tags) nor reading; without a path search, a stroll targets a random spot of the building's box at the hut's
 * height, more than 10 blocks away and off danger, and walks once the last stroll is over (MC's walkToRandomPosWithin
 * also skips a draw while its last random path result is kept, 20 s after the walk), with no rain preference (MC's
 * preferInside, which in its code rejects covered spots); the walk to the site is given up after
 * {@link CitizenWander#WANDER_TIMEOUT_TICKS} (MC's stuck handler teleports); a citizen found already there keeps any
 * walk under way (MC's walkToPos stops it; the body port has no stop).
 */
final class LeisureWalk {
    /**
     * MC goToLeisureSite: walkToPos(citizen, leisureSite, 3, true), arrived within that many blocks once stopped. All
     * distances here are from block to block, as MC's BlockPosUtil.dist and PathJobRandomPos distSqr.
     */
    private static final int ARRIVED_BLOCKS = 3;
    /** MC EntityNavigationUtils.walkToPos: before any walk, already there within REACHED_DIST blocks. */
    private static final double REACHED_DIST = 1.5;
    /** MC wanderAtLeisureSite: nextInt(60 * 5) < 1 leaves the site. */
    private static final int LEAVE_BOUND = 60 * 5;
    /** MC wanderAtLeisureSite: nextInt(10) <= 0 strolls to a random spot of the building. */
    private static final int STROLL_BOUND = 10;
    /** MC wanderAtLeisureSite: setCurrentDelay(30) once a stroll is drawn. */
    static final int STROLL_DELAY_TICKS = 30;
    /** MC walkToRandomPosWithin(citizen, 10, …): PathJobRandomPos ends more than that many blocks away. */
    private static final int STROLL_MIN_BLOCKS = 10;
    /** Spots of the box drawn for a stroll before giving it up (no path search to find one). */
    private static final int STROLL_TRIES = 10;

    private static final long NOT_STARTED = -1;

    private final Colony colony;
    private final BodyId body;
    private final CitizenBodies bodies;
    private final RandomGenerator random;
    private final IntConsumer delay;
    private final DangerousCells danger;
    private @Nullable BlockPos site;
    private boolean atSite;
    /** The tick the walk to the site was first ticked, {@link #NOT_STARTED} before. */
    private long goingSince = NOT_STARTED;

    LeisureWalk(Colony colony, BodyId body, IntConsumer delay, DangerousCells danger) {
        this.colony = colony;
        this.body = body;
        this.bodies = colony.context().bodies();
        this.random = colony.context().random();
        this.delay = delay;
        this.danger = danger;
    }

    boolean active() {
        return site != null;
    }

    /** MC decide: off to {@code to}. */
    void start(BlockPos to) {
        site = to;
        atSite = false;
        goingSince = NOT_STARTED;
    }

    /** Back to plain wandering (MC: the state machine back in IDLE). */
    void stop() {
        site = null;
        atSite = false;
    }

    /** One leisure step, every 20 ticks (MC's GO_TO_LEISURE_SITE and WANDER_AT_LEISURE_SITE transitions). */
    void tick() {
        BlockPos to = site;
        if (to == null) {
            return;
        }
        if (atSite) {
            strollAt(to);
        } else {
            goTo(to);
        }
    }

    /**
     * MC goToLeisureSite with walkToPos: the first step finds the citizen already there (within 1.5 blocks) or sends
     * it, replacing any walk; then, once its walk is over, it is there within {@link #ARRIVED_BLOCKS} or sent again.
     * Given up after the timeout.
     */
    private void goTo(BlockPos to) {
        long now = colony.context().clock().currentTick();
        Optional<Vec3> here = bodies.position(body);
        if (here.isEmpty()) {
            return;
        }
        if (goingSince == NOT_STARTED) {
            goingSince = now;
            if (here.get().toBlockPos().distSq(to) <= REACHED_DIST * REACHED_DIST) {
                atSite = true;
            } else {
                bodies.moveTo(body, Vec3.center(to));
            }
        } else if (now - goingSince >= CitizenWander.WANDER_TIMEOUT_TICKS) {
            stop();
        } else if (bodies.navStatus(body) != NavStatus.MOVING) {
            if (here.get().toBlockPos().distSq(to) <= (long) ARRIVED_BLOCKS * ARRIVED_BLOCKS) {
                atSite = true;
            } else {
                bodies.moveTo(body, Vec3.center(to));
            }
        }
    }

    /**
     * MC wanderAtLeisureSite: leaves one time in 300, or if the site is no building; else, one time in 10, a stroll in
     * the building (the transition then waits {@link #STROLL_DELAY_TICKS}).
     */
    private void strollAt(BlockPos to) {
        Optional<Building> building = colony.buildings().at(to);
        if (random.nextInt(LEAVE_BOUND) < 1 || building.isEmpty()) {
            stop();
            return;
        }
        if (random.nextInt(STROLL_BOUND) <= 0) {
            if (bodies.navStatus(body) != NavStatus.MOVING) {
                strollSpot(HutFootprint.of(colony.context().ports(), building.get()), to.y())
                        .ifPresent(spot -> bodies.moveTo(body, Vec3.center(spot)));
            }
            delay.accept(STROLL_DELAY_TICKS);
        }
    }

    /** A random spot of {@code box} at height {@code y}, more than 10 blocks from the citizen and off danger. */
    private Optional<BlockPos> strollSpot(HutFootprint.Box box, int y) {
        Optional<Vec3> here = bodies.position(body);
        if (here.isEmpty()) {
            return Optional.empty();
        }
        for (int i = 0; i < STROLL_TRIES; i++) {
            int x = box.min().x() + random.nextInt(box.max().x() - box.min().x() + 1);
            int z = box.min().z() + random.nextInt(box.max().z() - box.min().z() + 1);
            BlockPos spot = new BlockPos(x, y, z);
            if (here.get().toBlockPos().distSq(spot) > (long) STROLL_MIN_BLOCKS * STROLL_MIN_BLOCKS
                    && !danger.near(spot, CitizenWander.WANDER_DANGER_HALF_HEIGHT)) {
                return Optional.of(spot);
            }
        }
        return Optional.empty();
    }
}
