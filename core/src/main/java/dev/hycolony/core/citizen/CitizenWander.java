package dev.hycolony.core.citizen;

import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.nav.DangerousCells;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.kernel.port.CitizenBodies;
import dev.hycolony.core.kernel.port.NavStatus;
import java.util.Optional;
import java.util.random.RandomGenerator;
import org.jspecify.annotations.Nullable;

/** An idle citizen's wander around where it stands (MC EntityAICitizenWander), the IDLE transition of its AI. */
final class CitizenWander {
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
    private static final int WANDER_DANGER_HALF_HEIGHT = 3;
    /**
     * The ticks the wander waits for a walk under way. Deviation from MC: EntityAICitizenWander waits for the nav to
     * be done, which MC's stuck handler ensures on every path (PathingStuckHandler MIN_TP_DELAY, 120 * 20, then
     * completeStuckAction stops the nav); ours watches only walkers' walks, so a nav left running (a stuck wander, a
     * job or commanded walk cut short) is waited for as long, then left.
     */
    static final int WANDER_TIMEOUT_TICKS = 120 * 20;

    private static final long NOT_WAITING = -1;

    private final Colony colony;
    private final BodyId body;
    private final CitizenBodies bodies;
    private final RandomGenerator random;
    private final DangerousCells danger;
    /** The tick the wander first saw the walk under way, {@link #NOT_WAITING} while it saw none. */
    private long waitingSince = NOT_WAITING;

    CitizenWander(Colony colony, BodyId body) {
        this.colony = colony;
        this.body = body;
        this.bodies = colony.context().bodies();
        this.random = colony.context().random();
        this.danger = new DangerousCells(
                colony.context().ports().blocks(), colony.context().ports().catalog());
    }

    /** Restarts the wait for a walk under way: the next wander waits for the walk seen from now on. */
    void restartWait() {
        waitingSince = NOT_WAITING;
    }

    /**
     * MC EntityAICitizenWander.decide: once the last walk is over (canUse: navigation done), or under way for
     * {@link #WANDER_TIMEOUT_TICKS}, a walk to a random spot
     * around the citizen's own position; the citizen stays IDLE. Deviation from MC: no leisure branch yet (MC
     * LEISURE_CHANCE, 5 %: a leisure site, else its home or the colony's centre, where it wanders, sits or reads).
     */
    @Nullable
    CitizenState wander() {
        long now = colony.context().clock().currentTick();
        if (bodies.navStatus(body) == NavStatus.MOVING) {
            if (waitingSince == NOT_WAITING) {
                waitingSince = now;
            }
            if (now - waitingSince < WANDER_TIMEOUT_TICKS) {
                return null;
            }
        }
        waitingSince = NOT_WAITING;
        bodies.position(body)
                .flatMap(here -> wanderTarget(here.toBlockPos(), here.y()))
                .ifPresent(target -> bodies.moveTo(body, target));
        return null;
    }

    /**
     * A random spot just past {@link #WANDER_RADIUS} of {@code anchor} (horizontally), at height {@code y}, with no
     * dangerous block within 1 block ({@link DangerousCells#near}); else the first pick whose own column holds none (MC
     * PathJobRandomPos never ends on one, PathfindingUtils.isDangerous); empty after {@link #WANDER_TRIES} dangerous
     * picks. Deviation from MC: without a path search, the spot is the cell 11 blocks away in a random direction.
     */
    private Optional<Vec3> wanderTarget(BlockPos anchor, double y) {
        Vec3 columnSafe = null;
        for (int i = 0; i < WANDER_TRIES; i++) {
            double angle = random.nextDouble(2 * Math.PI);
            int dx = (int) Math.round(Math.cos(angle) * (WANDER_RADIUS + 1));
            int dz = (int) Math.round(Math.sin(angle) * (WANDER_RADIUS + 1));
            Vec3 target = new Vec3(anchor.x() + dx + 0.5, y, anchor.z() + dz + 0.5);
            BlockPos cell = target.toBlockPos();
            if (!danger.near(cell, WANDER_DANGER_HALF_HEIGHT)) {
                return Optional.of(target);
            }
            if (columnSafe == null && !danger.inColumn(cell, WANDER_DANGER_HALF_HEIGHT)) {
                columnSafe = target;
            }
        }
        return Optional.ofNullable(columnSafe);
    }
}
