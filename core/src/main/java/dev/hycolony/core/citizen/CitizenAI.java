package dev.hycolony.core.citizen;

import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.job.Job;
import dev.hycolony.core.job.JobAI;
import dev.hycolony.core.job.WorkerModule;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.ai.AITarget;
import dev.hycolony.core.kernel.ai.IStateSupplier;
import dev.hycolony.core.kernel.ai.TickRateStateMachine;
import dev.hycolony.core.kernel.nav.DangerousCells;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.kernel.port.CitizenBodies;
import dev.hycolony.core.kernel.port.Msg;
import dev.hycolony.core.kernel.port.NavStatus;
import java.util.Objects;
import java.util.Optional;
import java.util.random.RandomGenerator;
import org.jspecify.annotations.Nullable;

/**
 * Top-level citizen AI: idle (wandering around) or work its job. Port of MC CitizenAI.calculateNextState, reduced to
 * the work decision: the citizen works only when its job AI cannot go idle ({@link JobAI#canGoIdle}), it is not on a
 * leisure break ({@link #onBreak}) and the rain does not stop it ({@link #rainStopsWork}).
 */
public final class CitizenAI {
    private static final System.Logger LOG = System.getLogger(CitizenAI.class.getName());
    /** MC EntityAICitizenWander.decide: walkToRandomPos(citizen, 10, speed), around the citizen's own position. */
    private static final int WANDER_RADIUS = 10;
    /** MC EntityAICitizenWander: its IDLE transition runs every 100 ticks. */
    private static final int WANDER_RATE_TICKS = 100;
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
    /** MC CitizenAI: decideAiTask runs as an EVENT target every 10 ticks. */
    private static final int DECIDE_INTERVAL_TICKS = 10;

    private final Colony colony;
    private final CitizenData data;
    private final BodyId body;
    private final CitizenBodies bodies;
    private final RandomGenerator random;
    private final DangerousCells danger;
    private final TickRateStateMachine<CitizenState> machine;
    private int workTicks;
    private boolean failed;
    private @Nullable JobAI jobAI;
    /** The job and work building {@link #jobAI} was created for. */
    private @Nullable Job aiJob;

    private @Nullable BlockPos aiWorkBuilding;

    /** Starts at the idle state and at normal walking speed. */
    public CitizenAI(Colony colony, CitizenData data, BodyId body) {
        this.colony = colony;
        this.data = data;
        this.body = body;
        this.bodies = colony.context().bodies();
        this.random = colony.context().random();
        this.danger = new DangerousCells(
                colony.context().ports().blocks(), colony.context().ports().catalog());
        this.machine = new TickRateStateMachine<>(CitizenState.IDLE, this::onException);
        machine.addTransition(
                new AITarget<>(CitizenState.IDLE, (IStateSupplier<CitizenState>) this::idle, DECIDE_INTERVAL_TICKS));
        machine.addTransition(
                new AITarget<>(CitizenState.IDLE, (IStateSupplier<CitizenState>) this::wander, WANDER_RATE_TICKS));
        machine.addTransition(new AITarget<>(CitizenState.WORKING, (IStateSupplier<CitizenState>) this::work, 1));
        // A body can keep a job's speed across a crash (the Hytale effect is saved with the NPC); a job AI sets its
        // own.
        bodies.setMovementSpeed(body, 1);
    }

    public void tick() {
        machine.tick();
    }

    public CitizenState state() {
        return machine.getState();
    }

    /** The job AI's own line while working. */
    public Optional<Msg> jobActivity() {
        return state() == CitizenState.WORKING && jobAI != null ? jobAI.describe() : Optional.empty();
    }

    /**
     * MC AbstractEntityCitizen's AI exception handler: logged only; the citizen keeps its state. The first one is a
     * WARNING, the next ones DEBUG: a target that throws at every tick would flood the log.
     */
    private void onException(RuntimeException e) {
        LOG.log(
                failed ? System.Logger.Level.DEBUG : System.Logger.Level.WARNING,
                "Citizen AI failed for " + data.name(),
                e);
        failed = true;
    }

    /** MC CitizenAI.decideAiTask, every {@link #DECIDE_INTERVAL_TICKS}: to work when it should. */
    private @Nullable CitizenState idle() {
        return shouldWork() ? CitizenState.WORKING : null;
    }

    /**
     * MC EntityAICitizenWander.decide: once the last walk is over (canUse: navigation done), a walk to a random spot
     * around the citizen's own position; the citizen stays IDLE.
     */
    private @Nullable CitizenState wander() {
        if (bodies.navStatus(body) == NavStatus.MOVING) {
            return null;
        }
        bodies.position(body)
                .flatMap(here -> wanderTarget(here.toBlockPos(), here.y()))
                .ifPresent(target -> bodies.moveTo(body, target));
        return null;
    }

    /**
     * A random spot within {@link #WANDER_RADIUS} of {@code anchor}, at height {@code y}, with no dangerous block
     * within 1 block ({@link DangerousCells#near}); else the first pick whose own column holds none (MC
     * PathJobRandomPos never ends on one, PathfindingUtils.isDangerous); empty after {@link #WANDER_TRIES} dangerous
     * picks.
     */
    private Optional<Vec3> wanderTarget(BlockPos anchor, double y) {
        Vec3 columnSafe = null;
        for (int i = 0; i < WANDER_TRIES; i++) {
            int dx = random.nextInt(2 * WANDER_RADIUS + 1) - WANDER_RADIUS;
            int dz = random.nextInt(2 * WANDER_RADIUS + 1) - WANDER_RADIUS;
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

    private @Nullable CitizenState work() {
        Job job = data.job().orElse(null);
        if (job == null) {
            dropJobAI();
            return CitizenState.IDLE;
        }
        JobAI ai = jobAI;
        if (ai == null || !job.equals(aiJob) || !Objects.equals(data.workBuilding(), aiWorkBuilding)) {
            ai = startJob(job); // fired and hired again (elsewhere) between two ticks: bound to the new hut
        }
        // MC re-decides every DECIDE_INTERVAL_TICKS, which also keeps the order lookup off the per-tick path.
        if (++workTicks % DECIDE_INTERVAL_TICKS == 0 && (rainStopsWork() || ai.canGoIdle() || onBreak(ai))) {
            dropJobAI();
            return CitizenState.IDLE;
        }
        ai.tick();
        return null;
    }

    /**
     * MC calculateNextState: work only when the rain does not stop it ({@link #rainStopsWork}, checked first as in MC),
     * the job AI cannot go idle and the citizen is not on a break ({@link #onBreak}). Asks a fresh job AI, which then
     * starts from its first state like MC's resetAI on entering WORK.
     */
    private boolean shouldWork() {
        Job job = data.job().orElse(null);
        if (job == null || !bodies.isAlive(body) || rainStopsWork()) {
            return false;
        }
        JobAI ai = jobAI;
        if (ai == null || !job.equals(aiJob) || !Objects.equals(data.workBuilding(), aiWorkBuilding)) {
            ai = startJob(job);
        }
        return !ai.canGoIdle() && !onBreak(ai);
    }

    /**
     * MC calculateNextState: a citizen on leisure ({@link CitizenData#leisureTime}) idles, unless its job AI cannot be
     * interrupted right now.
     */
    private boolean onBreak(JobAI ai) {
        return data.leisureTime() > 0 && ai.canBeInterrupted();
    }

    /**
     * MC calculateNextState: while it rains a worker idles, even mid-task, unless shouldWorkWhileRaining (the config
     * workersAlwaysWorkInRain, or its hut's {@link WorkerModule#canWorkDuringTheRain}). Deviation from MC: rain or
     * snow at the work hut (Hytale weather is per zone), not a world-wide flag; no WORKING_IN_RAIN research nor
     * BAD_WEATHER status line; a worker without a work hut is left to its job AI (MC idles it).
     */
    private boolean rainStopsWork() {
        BlockPos at = data.workBuilding();
        if (at == null || colony.context().config().gameplay().workersAlwaysWorkInRain()) {
            return false;
        }
        return colony.buildings()
                .at(at)
                .filter(hut -> !hut.module(WorkerModule.class)
                        .map(m -> m.canWorkDuringTheRain(hut))
                        .orElse(false))
                .map(hut -> colony.context().worldQuery().isRainingAt(hut.position()))
                .orElse(false);
    }

    /**
     * Forgets the job AI and its held item (MC resetAI clears the render metadata), and its walking speed (MC
     * BuildingDeliveryman removes the courier's speed modifier with the job; a job AI sets its own again).
     */
    private void dropJobAI() {
        jobAI = null;
        aiJob = null;
        bodies.setHeldItem(body, Optional.empty());
        bodies.setMovementSpeed(body, 1);
    }

    /** A fresh job AI, now current, at normal speed: a courier hired for another job loses its Agility bonus (MC). */
    private JobAI startJob(Job job) {
        bodies.setMovementSpeed(body, 1);
        aiJob = job;
        aiWorkBuilding = data.workBuilding();
        JobAI ai = job.createAI(colony, body);
        jobAI = ai;
        return ai;
    }
}
