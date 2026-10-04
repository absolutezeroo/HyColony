package dev.hycolony.core.citizen.mourn;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.CitizenState;
import dev.hycolony.core.citizen.vitals.CitizenWalkReports;
import dev.hycolony.core.citizen.wander.CitizenWander;
import dev.hycolony.core.colony.BlockApproach;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.nav.BodyWalker;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.kernel.port.CitizenBodies;
import java.util.Optional;
import java.util.random.RandomGenerator;
import org.jspecify.annotations.Nullable;

/**
 * A mourning citizen's day (MC EntityAIMournCitizen): it stares at a citizen close by, or walks to the town hall (else
 * its home) when far from it, else wanders around. Deviation from MC: no graveyard nor graves (not ported), so its
 * graveyard steps never run; it stares at its own colony's citizens only; no "I'm still processing X's death" chat
 * (MC CitizenAI's StandardInteraction: citizen interactions are not ported).
 */
public final class MournAI {
    /** MC: each mourning step runs every 20 ticks. */
    public static final int RATE_TICKS = 20;
    /**
     * Where a mourner looks at another citizen, above its feet. Deviation from MC (Hytale world): MC's citizen eye
     * height (0.95 of its 1.8, AbstractCivilianEntity.getStandingEyeHeight) → the citizen model's EyeHeight, 1.6
     * (Server/Models/Human/Player.json, PlayerTestModel_V's parent).
     */
    static final double EYE_HEIGHT = 1.6;
    /** MC MIN_DESTINATION_TO_LOCATION: past this x + z distance from its mourning place, it walks there. */
    static final int MIN_DESTINATION_TO_LOCATION = 225;
    /** MC AVERAGE_STARE_TIME: a stare ends with one chance in this many at each step. */
    static final int AVERAGE_STARE_TIME = 10 * 20;
    /** MC stare: the nearest citizen within its bounding box inflated by 3 blocks. */
    static final double STARE_RANGE = 3;
    /** The citizen model's hitbox half width, in blocks (Server/Models/Human/Player.json, HitBox Max X). */
    private static final double HALF_WIDTH = 0.325;
    /** The citizen model's hitbox height, in blocks (Server/Models/Human/Player.json, HitBox Max Y). */
    private static final double HEIGHT = 1.85;

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
    private final BlockApproach approach;
    private Step step = Step.DECIDE;
    private @Nullable BodyId stared;
    /** Where it walks to mourn; null before its first walk there. */
    private @Nullable Building place;

    public MournAI(Colony colony, CitizenData data, BodyId body, CitizenWander wander) {
        this.colony = colony;
        this.data = data;
        this.body = body;
        this.bodies = colony.context().bodies();
        this.wander = wander;
        this.random = colony.context().random();
        this.approach = new BlockApproach(
                colony.context().ports(),
                new BodyWalker(
                        bodies, body, colony.context().clock()::currentTick, new CitizenWalkReports(colony, data)));
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
                if (place == null || approach.walkToBuilding(place)) {
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
        if (wander.walkUnderWay()) {
            return; // MC: until the navigation is done; a walk that never ends is waited for at most 2 min
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
        Building to = mournPlace().orElse(null);
        if (to != null && distance2D(here.get().toBlockPos(), to.position()) > MIN_DESTINATION_TO_LOCATION) {
            place = to;
            approach.forget();
            approach.walkToBuilding(to);
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

    /** The living body of another citizen nearest to it, {@link #near} it. */
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

    /**
     * MC getNearestEntity in its bounding box inflated by {@link #STARE_RANGE}: the other's box meets it, both a
     * citizen's. Deviation from MC (Hytale world): MC's 0.6 by 1.8 citizen box → the citizen model's hitbox, 0.65 by
     * 1.85 (Server/Models/Human/Player.json).
     */
    private static boolean near(Vec3 a, Vec3 b) {
        double reach = STARE_RANGE + 2 * HALF_WIDTH;
        return Math.abs(a.x() - b.x()) <= reach
                && Math.abs(a.z() - b.z()) <= reach
                && Math.abs(a.y() - b.y()) <= STARE_RANGE + HEIGHT;
    }

    /** MC getMournLocation: the town hall, else its home; empty with neither. */
    private Optional<Building> mournPlace() {
        Optional<Building> hall = colony.buildings().townHall();
        return hall.isPresent()
                ? hall
                : Optional.ofNullable(data.homeBuilding()).flatMap(colony.buildings()::at);
    }

    /** MC BlockPosUtil.getDistance2D: |dx| + |dz|. */
    private static long distance2D(BlockPos a, BlockPos b) {
        return Math.abs((long) a.x() - b.x()) + Math.abs((long) a.z() - b.z());
    }
}
