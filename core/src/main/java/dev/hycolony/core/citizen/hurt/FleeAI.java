package dev.hycolony.core.citizen.hurt;

import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.CitizenState;
import dev.hycolony.core.citizen.vitals.CitizenWalkReports;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.nav.BodyWalker;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.kernel.port.CitizenBodies;
import dev.hycolony.core.kernel.port.body.BodyHealth;
import java.util.Optional;
import java.util.random.RandomGenerator;
import org.jspecify.annotations.Nullable;

/**
 * A hit citizen running away (MC EntityCitizen.performMoveAway), then avoiding the monsters near it until it is safe
 * (MC EntityAICitizenAvoidEntity in FLEE). Its runs go through a walker, whose stuck handler always ends them (MC's
 * navigation and PathingStuckHandler). Deviation from MC: no call for help (no guards yet); no move-away sound (no
 * citizen voice yet); the run keeps the body's speed, where MC's navigation takes 1.1 near a monster and 0.8 further,
 * as the body's speed factor is its job's (a courier's skill).
 */
public final class FleeAI {
    /** MC: its flee steps run every 5 ticks. */
    public static final int RATE_TICKS = 5;
    /** MC performMoveAway: blocks it runs from its attacker. */
    static final double FROM_ATTACKER = 15;
    /** MC performMoveAway: blocks it walks from where it stands, hurt by no entity. */
    static final double FROM_HARM = 5;
    /** MC CitizenConstants.DISTANCE_OF_ENTITY_AVOID: a monster this near is avoided. */
    static final double AVOID_RANGE = 5;
    /** MC CHECKS_BEFORE_SAFE: 20 * 10 / 5 checks without a monster near, and it is safe. */
    static final int CHECKS_BEFORE_SAFE = 20 * 10 / 5;
    /** MC getMoveAwayDist: blocks added to {@link #AVOID_RANGE}, as it is whole, half hurt or worse. */
    static final double MIN_MOVE_AWAY = 7;

    static final double MED_MOVE_AWAY = 15;
    static final double MAX_MOVE_AWAY = 20;
    /**
     * MC getMoveAwayDist: "whole" is within 4 points of the maximum. Deviation from MC (Hytale world): 4 of MC's 20
     * health points → the same share of the body's maximum (100, Server/Entity/Stats/Health.json).
     */
    static final double WHOLE_MARGIN_SHARE = 4.0 / 20;

    private enum Step {
        CHECK_ENTITIES,
        RUNNING
    }

    private final BodyId body;
    private final CitizenBodies bodies;
    private final BodyHealth health;
    private final RandomGenerator random;
    private final BodyWalker walker;
    private Step step = Step.CHECK_ENTITIES;
    private int safeTime;
    private @Nullable Vec3 startingPos;
    /** Where its run under way goes; null once the run ended. */
    private @Nullable BlockPos runTarget;

    public FleeAI(Colony colony, CitizenData data, BodyId body) {
        this.body = body;
        this.bodies = colony.context().bodies();
        this.health = colony.context().health();
        this.random = colony.context().random();
        this.walker = new BodyWalker(
                bodies, body, colony.context().clock()::currentTick, new CitizenWalkReports(colony, data));
    }

    /**
     * MC performMoveAway: hit by {@code attacker} (where it stands), it runs {@link #FROM_ATTACKER} blocks from it and
     * is to flee (true); hurt by no entity, it walks {@link #FROM_HARM} blocks from where it stands (false), a plain
     * walk its next AI waits for.
     */
    public boolean hit(Optional<Vec3> attacker) {
        Vec3 here = bodies.position(body).orElse(null);
        if (here == null) {
            return false;
        }
        if (attacker.isEmpty()) {
            bodies.moveTo(body, awayFrom(here, here, FROM_HARM));
            return false;
        }
        run(awayFrom(here, attacker.get(), FROM_ATTACKER));
        return true;
    }

    /** MC FLEE's transition: it starts checking for monsters from where it stands; a run under way goes on. */
    public void start() {
        step = Step.CHECK_ENTITIES;
        safeTime = 0;
        startingPos = bodies.position(body).orElse(null);
    }

    /** One flee step, every {@link #RATE_TICKS}: IDLE once safe, else null (it stays in FLEE). */
    public @Nullable CitizenState tick() {
        if (runTarget != null && walker.walkTo(runTarget, false)) {
            runTarget = null; // MC moveAwayPath no longer in progress
        }
        return step == Step.CHECK_ENTITIES ? checkEntities() : running();
    }

    /**
     * MC isEntityClose: safe after {@link #CHECKS_BEFORE_SAFE} checks without a monster near, it walks back where it
     * started; a monster near makes it run.
     */
    private @Nullable CitizenState checkEntities() {
        if (++safeTime > CHECKS_BEFORE_SAFE) {
            if (startingPos != null) {
                bodies.moveTo(body, startingPos); // MC reset: walkToPos(startingPos), then IDLE
            }
            runTarget = null;
            start();
            return CitizenState.IDLE;
        }
        if (health.nearestThreat(body, AVOID_RANGE).isPresent()) {
            safeTime = 0;
            moveAway();
            step = Step.RUNNING;
        }
        return null;
    }

    /** MC updateMoving: a monster still near keeps it running; once its run is over, it checks again. */
    private @Nullable CitizenState running() {
        if (health.nearestThreat(body, AVOID_RANGE).isPresent()) {
            moveAway();
        }
        if (runTarget == null) {
            safeTime = 0;
            step = Step.CHECK_ENTITIES;
        }
        return null;
    }

    /**
     * MC performMoveAway (the AI's): once its last run is over, away from a spot by its feet, {@link #AVOID_RANGE} plus
     * its move-away distance.
     */
    private void moveAway() {
        if (runTarget != null) {
            return;
        }
        bodies.position(body)
                .ifPresent(here -> run(awayFrom(
                        here,
                        new Vec3(here.x() + random.nextInt(2), here.y(), here.z() + random.nextInt(2)),
                        AVOID_RANGE + moveAwayDistance())));
    }

    /** Starts a run to {@code to}; the stuck handler gives up rather than teleport to an unchecked spot. */
    private void run(Vec3 to) {
        walker.forget();
        runTarget = to.toBlockPos();
        if (walker.walkTo(runTarget, false)) {
            runTarget = null;
        }
    }

    /** MC getMoveAwayDist: 7 blocks almost whole, 15 at half health or more, else 20. */
    private double moveAwayDistance() {
        double max = health.maxHealth(body);
        double now = health.health(body);
        if (now >= max - max * WHOLE_MARGIN_SHARE) {
            return MIN_MOVE_AWAY;
        }
        return now >= max / 2 ? MED_MOVE_AWAY : MAX_MOVE_AWAY;
    }

    /**
     * MC walkAwayFrom(avoid, distance): the spot {@code distance} blocks from {@code avoid}, on the far side from
     * {@code here}; a random side when it stands on {@code avoid}.
     */
    private Vec3 awayFrom(Vec3 here, Vec3 avoid, double distance) {
        double dx = here.x() - avoid.x();
        double dz = here.z() - avoid.z();
        double length = Math.hypot(dx, dz);
        if (length < 1e-6) {
            double angle = random.nextDouble(2 * Math.PI);
            dx = Math.cos(angle);
            dz = Math.sin(angle);
            length = 1;
        }
        return new Vec3(avoid.x() + dx / length * distance, here.y(), avoid.z() + dz / length * distance);
    }
}
