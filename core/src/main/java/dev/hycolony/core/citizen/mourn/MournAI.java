package dev.hycolony.core.citizen.mourn;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.CitizenState;
import dev.hycolony.core.citizen.wander.CitizenWander;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.kernel.port.CitizenBodies;
import dev.hycolony.core.kernel.port.NavStatus;
import java.util.Optional;
import java.util.random.RandomGenerator;
import org.jspecify.annotations.Nullable;

/**
 * A mourning citizen's day (MC EntityAIMournCitizen): it stares at a citizen close by, or walks to the town hall (else
 * its home) when far from it, else wanders around. Deviation from MC: no graveyard nor graves (not ported), so its
 * graveyard steps never run; it stares at its own colony's citizens only.
 */
public final class MournAI {
    /** MC: each mourning step runs every 20 ticks. */
    public static final int RATE_TICKS = 20;
    /** MC Player eye height (1.62), the citizen's model: where a mourner looks at another citizen. */
    static final double EYE_HEIGHT = 1.62;
    /** MC MIN_DESTINATION_TO_LOCATION: past this x + z distance from its mourning place, it walks there. */
    static final int MIN_DESTINATION_TO_LOCATION = 225;
    /** MC AVERAGE_STARE_TIME: a stare ends with one chance in this many at each step. */
    static final int AVERAGE_STARE_TIME = 10 * 20;
    /** MC stare: the nearest citizen within its bounding box inflated by 3 blocks. */
    static final double STARE_RANGE = 3;

    private enum Step {
        DECIDE,
        WALKING_TO_TOWNHALL,
        STARING
    }

    private final Colony colony;
    private final CitizenData data;
    private final BodyId body;
    private final CitizenBodies bodies;
    private final CitizenWander wander;
    private final RandomGenerator random;
    private Step step = Step.DECIDE;
    private @Nullable BodyId stared;

    public MournAI(Colony colony, CitizenData data, BodyId body, CitizenWander wander) {
        this.colony = colony;
        this.data = data;
        this.body = body;
        this.bodies = colony.context().bodies();
        this.wander = wander;
        this.random = colony.context().random();
    }

    /** MC reset, on entering MOURN: it decides afresh. */
    public void reset() {
        step = Step.DECIDE;
        stared = null;
    }

    /** One mourning step, every {@link #RATE_TICKS}; it stays in MOURN (null). */
    public @Nullable CitizenState tick() {
        switch (step) {
            case DECIDE -> decide();
            case WALKING_TO_TOWNHALL -> {
                if (bodies.navStatus(body) != NavStatus.MOVING) {
                    step = Step.DECIDE; // MC walkToBuilding done: IDLE, then MOURN decides again
                }
            }
            case STARING -> stare();
        }
        return null;
    }

    /**
     * MC decide: once its last walk is over, half the time it stares; else it looks down and walks to its mourning
     * place when far from it, or wanders.
     */
    private void decide() {
        if (bodies.navStatus(body) == NavStatus.MOVING) {
            return;
        }
        if (random.nextBoolean()) {
            step = Step.STARING;
            return;
        }
        Optional<Vec3> here = bodies.position(body);
        if (here.isEmpty()) {
            return;
        }
        bodies.lookAt(
                body, new Vec3(here.get().x(), here.get().y() - 10, here.get().z()));
        Optional<BlockPos> place = mournPlace();
        if (place.isPresent() && distance2D(here.get().toBlockPos(), place.get()) > MIN_DESTINATION_TO_LOCATION) {
            bodies.moveTo(body, Vec3.center(place.get()));
            step = Step.WALKING_TO_TOWNHALL;
            return;
        }
        wander.walkToRandomSpot(); // MC WANDERING: walkToRandomPos, then IDLE and MOURN decides again
    }

    /**
     * MC stare: ends one time in {@link #AVERAGE_STARE_TIME}; else looks at the nearest citizen within {@link
     * #STARE_RANGE}, kept while it stays; none: it decides again.
     */
    private void stare() {
        if (random.nextInt(AVERAGE_STARE_TIME) < 1) {
            reset();
            return;
        }
        if (stared == null || !bodies.isAlive(stared)) {
            stared = nearestCitizen().orElse(null);
            if (stared == null) {
                step = Step.DECIDE;
                return;
            }
        }
        bodies.position(stared).ifPresent(at -> bodies.lookAt(body, new Vec3(at.x(), at.y() + EYE_HEIGHT, at.z())));
    }

    /** The living body of another citizen nearest to it, within {@link #STARE_RANGE} on each axis. */
    private Optional<BodyId> nearestCitizen() {
        Vec3 here = bodies.position(body).orElse(null);
        if (here == null) {
            return Optional.empty();
        }
        BodyId best = null;
        double bestDistance = Double.MAX_VALUE;
        for (CitizenData other : colony.citizens().all()) {
            BodyId b = colony.citizens().bodyOf(other.id()).orElse(null);
            Vec3 at = b == null || other.id() == data.id()
                    ? null
                    : bodies.position(b).orElse(null);
            if (at != null && near(here, at) && here.distance(at) < bestDistance) {
                best = b;
                bestDistance = here.distance(at);
            }
        }
        return Optional.ofNullable(best);
    }

    private static boolean near(Vec3 a, Vec3 b) {
        return Math.abs(a.x() - b.x()) <= STARE_RANGE
                && Math.abs(a.y() - b.y()) <= STARE_RANGE
                && Math.abs(a.z() - b.z()) <= STARE_RANGE;
    }

    /** MC getMournLocation: the town hall, else its home; empty with neither. */
    private Optional<BlockPos> mournPlace() {
        Optional<BlockPos> hall = colony.buildings().townHall().map(Building::position);
        return hall.isPresent() ? hall : Optional.ofNullable(data.homeBuilding());
    }

    /** MC BlockPosUtil.getDistance2D: |dx| + |dz|. */
    private static long distance2D(BlockPos a, BlockPos b) {
        return Math.abs((long) a.x() - b.x()) + Math.abs((long) a.z() - b.z());
    }
}
