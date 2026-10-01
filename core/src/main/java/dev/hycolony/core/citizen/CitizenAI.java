package dev.hycolony.core.citizen;

import dev.hycolony.core.citizen.vitals.AiWatch;
import dev.hycolony.core.citizen.vitals.CitizenWalkReports;
import dev.hycolony.core.colony.BlockApproach;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.job.Job;
import dev.hycolony.core.job.JobAI;
import dev.hycolony.core.job.WorkerModule;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.ai.AITarget;
import dev.hycolony.core.kernel.ai.IStateSupplier;
import dev.hycolony.core.kernel.ai.TickRateStateMachine;
import dev.hycolony.core.kernel.nav.BodyWalker;
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
    /**
     * MC EntityAICitizenWander.decide: walkToRandomPos(citizen, 10, speed), whose PathJobRandomPos only ends more
     * than 10 blocks from the citizen's own position.
     */
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
    /**
     * The ticks the wander waits for a walk under way. Deviation from MC: EntityAICitizenWander waits for the nav to
     * be done, which MC's stuck handler ensures on every path (PathingStuckHandler MIN_TP_DELAY, 120 * 20, then
     * completeStuckAction stops the nav); ours watches only walkers' walks, so a nav left running (a stuck wander, a
     * job or commanded walk cut short) is waited for as long, then left.
     */
    static final int WANDER_TIMEOUT_TICKS = 120 * 20;
    /** MC CitizenAI: decideAiTask runs as an EVENT target every 10 ticks. */
    private static final int DECIDE_INTERVAL_TICKS = 10;

    private static final long NOT_WAITING = -1;

    private final Colony colony;
    private final CitizenData data;
    private final BodyId body;
    private final CitizenBodies bodies;
    private final RandomGenerator random;
    private final DangerousCells danger;
    private final TickRateStateMachine<CitizenState> machine;
    private final AiWatch watch;
    private final CommandedWalk commanded;
    private int workTicks;
    /** The tick the wander first saw the walk under way, {@link #NOT_WAITING} while it saw none. */
    private long waitingSince = NOT_WAITING;

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
        this.watch = new AiWatch(colony, data);
        this.commanded = new CommandedWalk(
                () -> new BlockApproach(
                        colony.context().ports(),
                        new BodyWalker(
                                bodies,
                                body,
                                colony.context().clock()::currentTick,
                                new CitizenWalkReports(colony, data))),
                colony.context().clock()::currentTick);
        this.machine = new TickRateStateMachine<>(CitizenState.IDLE, this::onException);
        watch.afterTick(CitizenState.IDLE, null, 0); // its vital signs know where it starts
        machine.addTransition(
                new AITarget<>(CitizenState.IDLE, (IStateSupplier<CitizenState>) this::idle, DECIDE_INTERVAL_TICKS));
        machine.addTransition(
                new AITarget<>(CitizenState.IDLE, (IStateSupplier<CitizenState>) this::wander, WANDER_RATE_TICKS));
        machine.addTransition(new AITarget<>(CitizenState.WORKING, (IStateSupplier<CitizenState>) this::work, 1));
        // A body can keep a job's speed across a crash (the Hytale effect is saved with the NPC); a job AI sets its
        // own.
        bodies.setMovementSpeed(body, 1);
    }

    /**
     * One AI tick; then its vital signs note the state, the job step, failures and actions (diagnostics). Timed under
     * its job (HyLens's /hylens perf).
     */
    public void tick() {
        long start = colony.context().timings().start();
        try {
            machine.tick();
            watch.afterTick(machine.getState(), jobAI, aiJob == null ? 0 : aiJob.actionsDone());
        } finally {
            colony.context().timings().stop(timingPart(), start);
        }
    }

    public CitizenState state() {
        return machine.getState();
    }

    /**
     * MC CommandCitizenTriggerWalkTo: the citizen walks to {@code target} (see {@link CommandedWalk}), its AI waiting;
     * a new command replaces the walk under way. Deviation from MC: its job AI then starts afresh, as its walkers would
     * believe it where it was (MC's walks keep no state).
     */
    public void walkTo(BlockPos target) {
        forgetJobAI();
        if (!commanded.active()) {
            machine.addTransition(commanded.transition(machine::getState));
        }
        commanded.start(target);
    }

    /** Teleports its body to {@code to}; its job AI starts afresh, as for {@link #walkTo}. */
    public void teleport(Vec3 to) {
        forgetJobAI();
        bodies.teleport(body, to);
    }

    /** The part its ticks are timed as: its job AI's job type id, else {@code "citizen"}; allocates nothing. */
    private String timingPart() {
        return aiJob == null ? "citizen" : aiJob.type().id();
    }

    /** Its job's AI, while it has a job; for diagnostics. */
    public Optional<JobAI> jobAi() {
        return Optional.ofNullable(jobAI);
    }

    /** The job AI's own line while working. */
    public Optional<Msg> jobActivity() {
        return state() == CitizenState.WORKING && jobAI != null ? jobAI.describe() : Optional.empty();
    }

    /**
     * MC EntityCitizen.citizenAI's exception handler: the citizen keeps its state. Deviation from MC: MC's handler is
     * silent; here the first exception is a WARNING and the next ones DEBUG, so a target throwing at every tick is seen
     * without flooding the log.
     */
    private void onException(RuntimeException e) {
        LOG.log(
                failed ? System.Logger.Level.DEBUG : System.Logger.Level.WARNING,
                "Citizen AI failed for " + data.name(),
                e);
        failed = true;
        watch.failed();
    }

    /** MC CitizenAI.decideAiTask, every {@link #DECIDE_INTERVAL_TICKS}: to work when it should. */
    private @Nullable CitizenState idle() {
        return shouldWork() ? CitizenState.WORKING : null;
    }

    /**
     * MC EntityAICitizenWander.decide: once the last walk is over (canUse: navigation done), or under way for
     * {@link #WANDER_TIMEOUT_TICKS}, a walk to a random spot
     * around the citizen's own position; the citizen stays IDLE. Deviation from MC: no leisure branch yet (MC
     * LEISURE_CHANCE, 5 %: a leisure site, else its home or the colony's centre, where it wanders, sits or reads).
     */
    private @Nullable CitizenState wander() {
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
     * BuildingDeliveryman removes the courier's speed modifier with the job; a job AI sets its own again); restarts
     * the wander's wait for a walk under way.
     */
    private void dropJobAI() {
        waitingSince = NOT_WAITING; // back to IDLE: the walk under way is waited for from now
        jobAI = null;
        aiJob = null;
        bodies.setHeldItem(body, Optional.empty());
        bodies.setMovementSpeed(body, 1);
    }

    /**
     * Forgets the job AI only, so the next work tick makes a fresh one; its speed and held item stay, as MC's command
     * leaves them (the courier's Agility is an attribute modifier kept with the job); restarts the wander's wait for a
     * walk under way.
     */
    private void forgetJobAI() {
        waitingSince = NOT_WAITING;
        jobAI = null;
        aiJob = null;
    }

    /** A fresh job AI, now current, at normal speed: a courier hired for another job loses its Agility bonus (MC). */
    private JobAI startJob(Job job) {
        bodies.setMovementSpeed(body, 1);
        aiJob = job;
        aiWorkBuilding = data.workBuilding();
        JobAI ai = job.createAI(colony, body);
        jobAI = ai;
        watch.jobStarted(); // once made: a failing createAI leaves the old AI counted as it was
        return ai;
    }
}
