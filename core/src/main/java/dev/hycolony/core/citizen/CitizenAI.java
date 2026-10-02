package dev.hycolony.core.citizen;

import dev.hycolony.core.citizen.food.CitizenEating;
import dev.hycolony.core.citizen.sleep.CitizenSleep;
import dev.hycolony.core.citizen.sleep.SleepDecision;
import dev.hycolony.core.citizen.vitals.AiWatch;
import dev.hycolony.core.citizen.vitals.CitizenWalkReports;
import dev.hycolony.core.citizen.wander.CitizenWander;
import dev.hycolony.core.colony.BlockApproach;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.job.Job;
import dev.hycolony.core.job.JobAI;
import dev.hycolony.core.job.WorkerModule;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.ai.AIBlockingEventType;
import dev.hycolony.core.kernel.ai.AIEventTarget;
import dev.hycolony.core.kernel.ai.AITarget;
import dev.hycolony.core.kernel.ai.IStateSupplier;
import dev.hycolony.core.kernel.ai.TickRateStateMachine;
import dev.hycolony.core.kernel.nav.BodyWalker;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.kernel.port.CitizenBodies;
import dev.hycolony.core.kernel.port.Msg;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Top-level citizen AI: sleep, eat, idle (wandering around) or work its job. Port of MC CitizenAI.calculateNextState
 * in MC's order, sleep then hunger then rain then work (sickness, mourning and raids are not ported): the citizen
 * works only when its job AI cannot go idle ({@link JobAI#canGoIdle}), it is not on a leisure break
 * ({@link #onBreak}) and the rain does not stop it ({@link #rainStopsWork}).
 */
public final class CitizenAI {
    private static final System.Logger LOG = System.getLogger(CitizenAI.class.getName());
    /** MC EntityAICitizenWander: its leisure transitions run every 20 ticks. */
    private static final int LEISURE_RATE_TICKS = 20;
    /** MC CitizenAI: decideAiTask runs as an EVENT target every 10 ticks. */
    private static final int DECIDE_INTERVAL_TICKS = 10;

    private final Colony colony;
    private final CitizenData data;
    private final BodyId body;
    private final CitizenBodies bodies;
    private final CitizenWander wander;
    private final TickRateStateMachine<CitizenState> machine;
    private final AiWatch watch;
    private final CommandedWalk commanded;
    private final CitizenSleep sleep;
    private final CitizenEating eating;
    /**
     * Whether the last decision was EATING (MC {@code lastState == EATING}): it stays so after a meal ends, so a
     * citizen whose meal ended hungry goes back to eat at once, as in MC.
     */
    private boolean decidedEating;

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
        this.machine = new TickRateStateMachine<>(CitizenState.IDLE, this::onException);
        this.wander = new CitizenWander(colony, data, body, machine::setCurrentDelay);
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
        this.sleep = new CitizenSleep(colony, data, body);
        this.eating = new CitizenEating(colony, data, body);
        watch.afterTick(CitizenState.IDLE, null, 0); // its vital signs know where it starts
        machine.addTransition(
                new AITarget<>(CitizenState.IDLE, (IStateSupplier<CitizenState>) this::idle, DECIDE_INTERVAL_TICKS));
        machine.addTransition(new AITarget<>(
                CitizenState.IDLE, (IStateSupplier<CitizenState>) wander::wander, CitizenWander.WANDER_RATE_TICKS));
        machine.addTransition(
                new AITarget<>(CitizenState.IDLE, (IStateSupplier<CitizenState>) wander::leisure, LEISURE_RATE_TICKS));
        machine.addTransition(new AITarget<>(CitizenState.WORKING, (IStateSupplier<CitizenState>) this::work, 1));
        machine.addTransition(new AIEventTarget<>(AIBlockingEventType.EVENT, this::decide, DECIDE_INTERVAL_TICKS));
        machine.addTransition(new AITarget<>(CitizenState.SLEEP, (IStateSupplier<CitizenState>) this::sleeping, 1));
        machine.addTransition(new AITarget<>(CitizenState.EATING, (IStateSupplier<CitizenState>) this::eat, 1));
        sleep.onBodyAppeared();
        // A body can keep a job's speed across a crash (the Hytale effect is saved with the NPC); a job AI sets its
        // own.
        bodies.setMovementSpeed(body, 1);
    }

    /**
     * One AI tick; out of IDLE, a leisure walk under way ends (MC leaves its leisure states); then its vital signs
     * note the state, the job step, failures and actions (diagnostics). Timed under its job (HyLens's /hylens perf).
     */
    public void tick() {
        long start = colony.context().timings().start();
        try {
            machine.tick();
            if (machine.getState() != CitizenState.IDLE) {
                wander.leftIdle();
            }
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

    /**
     * Teleports its body to {@code to}, woken first (MC TeleportHelper); its job AI starts afresh, as for
     * {@link #walkTo}.
     */
    public void teleport(Vec3 to) {
        sleep.wakeUp();
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
     * MC CitizenAI.decideAiTask, every {@link #DECIDE_INTERVAL_TICKS} in any state: the sleep part first (see
     * {@link SleepDecision}; asleep, it decides again only every 15 s, MC setCurrentDelay), then the hunger part.
     */
    private @Nullable CitizenState decide() {
        CitizenState now = machine.getState();
        CitizenState next = switch (sleep.decide(now == CitizenState.SLEEP)) {
            case STAY_ASLEEP -> {
                machine.setCurrentDelay(SleepDecision.SLEEP_DECIDE_DELAY_TICKS);
                yield null;
            }
            case GO_TO_SLEEP -> {
                decidedEating = false;
                leaveEating(now);
                dropJobAI(); // the sleep decision ignores canBeInterrupted
                yield CitizenState.SLEEP;
            }
            case WAKE_UP -> decideHunger(now == CitizenState.SLEEP ? CitizenState.IDLE : now);
            case NONE -> decideHunger(now);
        };
        return next == now ? null : next;
    }

    /**
     * MC calculateNextState's hunger part, after the sleep part and before the rain and work: to EATING when it should
     * eat (its job AI dropped, as on leaving WORK), judged as still eating after a decision to eat (MC lastState); out
     * of EATING once it should not, straight to work when it should work. {@code now} is the state the sleep part left
     * it in.
     */
    private CitizenState decideHunger(CitizenState now) {
        boolean eatingNow = now == CitizenState.EATING;
        JobAI ai = jobAI;
        decidedEating = eating.shouldEat(decidedEating || eatingNow, ai == null || ai.canBeInterrupted());
        if (decidedEating) {
            if (!eatingNow) {
                dropJobAI();
            }
            return CitizenState.EATING;
        }
        if (eatingNow) {
            eating.stop();
            return shouldWork() ? CitizenState.WORKING : CitizenState.IDLE;
        }
        return now;
    }

    /** MC EntityAIEatTask: one tick of the meal; IDLE once it is over. */
    private @Nullable CitizenState eat() {
        if (eating.tick()) {
            eating.stop();
            return CitizenState.IDLE;
        }
        return null;
    }

    /** A meal under way ends when another state takes over (MC reset). */
    private void leaveEating(CitizenState now) {
        if (now == CitizenState.EATING) {
            eating.stop();
        }
    }

    /** MC EntityAISleep's transitions, while in SLEEP. */
    private @Nullable CitizenState sleeping() {
        sleep.tick();
        return null;
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
     * starts from its first state as MC's resetAI on entering WORK makes it (its fields too: {@link #dropJobAI}).
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
     * Forgets the job AI, the next WORK making a fresh one, and its walking speed (MC BuildingDeliveryman removes the
     * courier's speed modifier with the job; a job AI sets its own again); restarts the wander's wait for a walk under
     * way. The hand is left as it is, as MC.
     *
     * <p>Deviation from MC: the whole job AI goes, its fields too (the farmer's skippedState and forceLeave, say); MC
     * keeps its AI and only resets its state machine and render metadata on entering WORK again (resetAI).
     */
    private void dropJobAI() {
        wander.restartWait(); // back to IDLE: the walk under way is waited for from now
        jobAI = null;
        aiJob = null;
        bodies.setMovementSpeed(body, 1);
    }

    /**
     * Forgets the job AI only, so the next work tick makes a fresh one; its speed and held item stay, as MC's command
     * leaves them (the courier's Agility is an attribute modifier kept with the job); restarts the wander's wait for a
     * walk under way.
     */
    private void forgetJobAI() {
        wander.restartWait();
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
