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

/**
 * Top-level citizen AI: idle, wander, or work its job. Port of MC CitizenAI.calculateNextState, reduced to the work
 * decision: the citizen works only when its job AI cannot go idle ({@link JobAI#canGoIdle}) and the rain does not stop
 * it ({@link #rainStopsWork}). Deviation from MC: no leisure time yet (no leisure system), so a worker with work
 * never takes a break.
 */
public final class CitizenAI {
    private static final System.Logger LOG = System.getLogger(CitizenAI.class.getName());
    private static final int WANDER_RADIUS = 10;
    private static final int IDLE_MIN_TICKS = 200, IDLE_MAX_TICKS = 400;
    private static final int WANDER_TIMEOUT_TICKS = 600;
    /**
     * Random wander spots tried before resting again. Deviation from MC: EntityAICitizenWander's walkToRandomPos runs
     * a path search (PathJobRandomPos) that never ends on a dangerous block; without one, a few spots are drawn.
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
    private int idleTicksLeft;
    private int wanderTicks;
    private int workTicks;
    private JobAI jobAI;
    /** The job and work building {@link #jobAI} was created for. */
    private Job aiJob;

    private BlockPos aiWorkBuilding;

    /** Starts at the idle state and at normal walking speed. */
    public CitizenAI(Colony colony, CitizenData data, BodyId body) {
        this.colony = colony;
        this.data = data;
        this.body = body;
        this.bodies = colony.context().bodies();
        this.random = colony.context().random();
        this.danger = new DangerousCells(
                colony.context().ports().blocks(), colony.context().ports().catalog());
        this.idleTicksLeft = nextIdle();
        this.machine = new TickRateStateMachine<>(CitizenState.IDLE, this::onException);
        machine.addTransition(new AITarget<>(CitizenState.IDLE, (IStateSupplier<CitizenState>) this::idle, 20));
        machine.addTransition(new AITarget<>(CitizenState.WANDERING, (IStateSupplier<CitizenState>) this::wander, 5));
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

    private void onException(RuntimeException e) {
        LOG.log(System.Logger.Level.WARNING, "Citizen AI failed for " + data.name(), e);
        machine.reset();
    }

    private CitizenState idle() {
        if (shouldWork()) {
            return CitizenState.WORKING;
        }
        idleTicksLeft -= 20;
        if (idleTicksLeft > 0) {
            return null;
        }
        Vec3 here = bodies.position(body).orElse(null);
        if (here == null) {
            return null;
        }
        BlockPos anchor = colony.buildings().townHall().map(b -> b.position()).orElse(here.toBlockPos());
        Vec3 target = wanderTarget(anchor, here.y()).orElse(null);
        if (target == null) {
            idleTicksLeft = nextIdle();
            return null;
        }
        bodies.moveTo(body, target);
        wanderTicks = 0;
        return CitizenState.WANDERING;
    }

    /**
     * A random spot within {@link #WANDER_RADIUS} of {@code anchor}, at height {@code y}, with no dangerous block within
     * 1 block ({@link DangerousCells#near}); else the first pick whose own column holds none (MC PathJobRandomPos never
     * ends on one, PathfindingUtils.isDangerous); empty after {@link #WANDER_TRIES} dangerous picks.
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

    private CitizenState wander() {
        wanderTicks += 5;
        // MC re-decides every DECIDE_INTERVAL_TICKS in any state: a worker stopped by the rain resumes mid-walk.
        if (wanderTicks % DECIDE_INTERVAL_TICKS == 0 && shouldWork()) {
            return CitizenState.WORKING;
        }
        NavStatus status = bodies.navStatus(body);
        boolean done = status == NavStatus.ARRIVED || status == NavStatus.BLOCKED || status == NavStatus.FAILED;
        if (done || wanderTicks >= WANDER_TIMEOUT_TICKS) {
            idleTicksLeft = nextIdle();
            return CitizenState.IDLE;
        }
        return null;
    }

    private CitizenState work() {
        Job job = data.job().orElse(null);
        if (job == null) {
            dropJobAI();
            return CitizenState.IDLE;
        }
        if (!job.equals(aiJob) || !Objects.equals(data.workBuilding(), aiWorkBuilding)) {
            startJob(job); // fired and hired again (elsewhere) between two ticks: bound to the new hut
        }
        // MC re-decides every DECIDE_INTERVAL_TICKS, which also keeps the order lookup off the per-tick path.
        if (++workTicks % DECIDE_INTERVAL_TICKS == 0 && (rainStopsWork() || jobAI.canGoIdle())) {
            dropJobAI();
            idleTicksLeft = 0; // the next idle decision wanders, replacing the job's unfinished walk
            return CitizenState.IDLE;
        }
        jobAI.tick();
        return null;
    }

    /**
     * MC calculateNextState: work only when the rain does not stop it ({@link #rainStopsWork}, checked first as in MC)
     * and the job AI cannot go idle. Asks a fresh job AI, which then starts from its first state like MC's resetAI on
     * entering WORK.
     */
    private boolean shouldWork() {
        Job job = data.job().orElse(null);
        if (job == null || !bodies.isAlive(body) || rainStopsWork()) {
            return false;
        }
        if (jobAI == null || !job.equals(aiJob) || !Objects.equals(data.workBuilding(), aiWorkBuilding)) {
            startJob(job);
        }
        return !jobAI.canGoIdle();
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

    /** A fresh job AI, at normal speed: a courier hired for another job loses its Agility bonus (MC). */
    private void startJob(Job job) {
        bodies.setMovementSpeed(body, 1);
        aiJob = job;
        aiWorkBuilding = data.workBuilding();
        jobAI = job.createAI(colony, body);
    }

    private int nextIdle() {
        return IDLE_MIN_TICKS + random.nextInt(IDLE_MAX_TICKS - IDLE_MIN_TICKS + 1);
    }
}
