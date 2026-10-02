package dev.hycolony.core.citizen.wander;

import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.CitizenState;
import dev.hycolony.core.colony.Colony;
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
 * An idle citizen's wander (MC EntityAICitizenWander), the IDLE transitions of its AI: now and then a leisure walk
 * ({@link LeisureWalk}), else a walk to a random spot around where it stands.
 *
 * <p>Deviation from MC (asked for): a citizen wanders only within its colony's territory, and one idle outside it
 * walks back to its home, else the colony's centre; MC lets citizens drift away.
 */
public final class CitizenWander {
    /**
     * The ticks the wander waits for a walk under way. Deviation from MC: EntityAICitizenWander waits for the nav to
     * be done, which MC's stuck handler ensures on every path (PathingStuckHandler MIN_TP_DELAY, 120 * 20, then
     * completeStuckAction stops the nav); ours watches only walkers' walks, so a nav left running (a stuck wander, a
     * job or commanded walk cut short) is waited for as long, then left.
     */
    public static final int WANDER_TIMEOUT_TICKS = 120 * 20;
    /** MC EntityAICitizenWander.LEISURE_CHANCE: in 100 wander decisions, that many go to a leisure site. */
    static final int LEISURE_CHANCE = 5;
    /** MC decide: setCurrentDelay(60 * 20) once a leisure site is picked. */
    static final int LEISURE_DELAY_TICKS = 60 * 20;
    /**
     * MC EntityAICitizenWander.decide: walkToRandomPos(citizen, 10, speed), whose PathJobRandomPos only ends more
     * than 10 blocks from the citizen's own position.
     */
    private static final int WANDER_RADIUS = 10;
    /**
     * Random wander spots tried before waiting for the next wander decision. Deviation from MC:
     * EntityAICitizenWander's walkToRandomPos runs a path search (PathJobRandomPos) that never ends on a dangerous
     * block; without one, a few spots are drawn.
     */
    private static final int WANDER_TRIES = 10;
    /**
     * Blocks above and below the body's height checked for danger in a wander column: the floor, feet and head, plus
     * the slope the nav may climb or drop on the way to the column's ground.
     */
    static final int WANDER_DANGER_HALF_HEIGHT = 3;

    private static final long NOT_WAITING = -1;

    private final Colony colony;
    private final CitizenData data;
    private final BodyId body;
    private final CitizenBodies bodies;
    private final RandomGenerator random;
    private final DangerousCells danger;
    private final IntConsumer delay;
    private final LeisureWalk leisure;
    /** The tick the wander first saw the walk under way, {@link #NOT_WAITING} while it saw none. */
    private long waitingSince = NOT_WAITING;

    /** {@code delay}: MC setCurrentDelay on the citizen's AI. */
    public CitizenWander(Colony colony, CitizenData data, BodyId body, IntConsumer delay) {
        this.colony = colony;
        this.data = data;
        this.body = body;
        this.bodies = colony.context().bodies();
        this.random = colony.context().random();
        this.danger = new DangerousCells(
                colony.context().ports().blocks(), colony.context().ports().catalog());
        this.delay = delay;
        this.leisure = new LeisureWalk(colony, body, delay, danger);
    }

    /** Restarts the wait for a walk under way: the next wander waits for the walk seen from now on. */
    public void restartWait() {
        waitingSince = NOT_WAITING;
    }

    /** The citizen left IDLE: a leisure walk under way ends (MC leaves its leisure states). */
    public void leftIdle() {
        leisure.stop();
    }

    /**
     * MC EntityAICitizenWander.decide, every 100 ticks while no leisure walk is under way: once the last walk is over
     * (canUse: navigation done), or under way for {@link #WANDER_TIMEOUT_TICKS}, a leisure walk {@link
     * #LEISURE_CHANCE} times in 100 (the AI then pauses {@link #LEISURE_DELAY_TICKS}), else a walk to a random spot
     * around the citizen's own position; the citizen stays IDLE.
     */
    public @Nullable CitizenState wander() {
        if (leisure.active() || walkUnderWay()) {
            return null;
        }
        Optional<Vec3> here = bodies.position(body);
        if (here.isEmpty()) {
            return null;
        }
        BlockPos at = here.get().toBlockPos();
        if (!colony.contains(at)) {
            bodies.moveTo(body, centre(home())); // asked for: back into the colony
            return null;
        }
        if (random.nextInt(100) < LEISURE_CHANCE) {
            leisure.start(LeisureSites.pick(colony, data, random));
            delay.accept(LEISURE_DELAY_TICKS);
            return null;
        }
        wanderTarget(at, here.get().y()).ifPresent(target -> bodies.moveTo(body, target));
        return null;
    }

    /** MC's GO_TO_LEISURE_SITE and WANDER_AT_LEISURE_SITE transitions, every 20 ticks; the citizen stays IDLE. */
    public @Nullable CitizenState leisure() {
        leisure.tick();
        return null;
    }

    /** Whether the last walk is still under way and waited for (at most {@link #WANDER_TIMEOUT_TICKS}). */
    private boolean walkUnderWay() {
        long now = colony.context().clock().currentTick();
        if (bodies.navStatus(body) == NavStatus.MOVING) {
            if (waitingSince == NOT_WAITING) {
                waitingSince = now;
            }
            if (now - waitingSince < WANDER_TIMEOUT_TICKS) {
                return true;
            }
        }
        waitingSince = NOT_WAITING;
        return false;
    }

    /** The citizen's home, else the colony's centre (MC decide's leisure fallbacks). */
    private BlockPos home() {
        BlockPos home = data.homeBuilding();
        return home != null ? home : colony.center();
    }

    /**
     * A random spot just past {@link #WANDER_RADIUS} of {@code anchor} (horizontally), at height {@code y}, in the
     * colony's territory, with no dangerous block within 1 block ({@link DangerousCells#near}); else the first such
     * pick whose own column holds none (MC PathJobRandomPos never ends on one, PathfindingUtils.isDangerous); empty
     * after {@link #WANDER_TRIES} picks. Deviation from MC: without a path search, the spot is the cell 11 blocks away
     * in a random direction; and the territory bound is asked for.
     */
    private Optional<Vec3> wanderTarget(BlockPos anchor, double y) {
        Vec3 columnSafe = null;
        for (int i = 0; i < WANDER_TRIES; i++) {
            double angle = random.nextDouble(2 * Math.PI);
            int dx = (int) Math.round(Math.cos(angle) * (WANDER_RADIUS + 1));
            int dz = (int) Math.round(Math.sin(angle) * (WANDER_RADIUS + 1));
            Vec3 target = new Vec3(anchor.x() + dx + 0.5, y, anchor.z() + dz + 0.5);
            BlockPos cell = target.toBlockPos();
            if (!colony.contains(cell)) {
                continue;
            }
            if (!danger.near(cell, WANDER_DANGER_HALF_HEIGHT)) {
                return Optional.of(target);
            }
            if (columnSafe == null && !danger.inColumn(cell, WANDER_DANGER_HALF_HEIGHT)) {
                columnSafe = target;
            }
        }
        return Optional.ofNullable(columnSafe);
    }

    private static Vec3 centre(BlockPos p) {
        return new Vec3(p.x() + 0.5, p.y(), p.z() + 0.5);
    }
}
