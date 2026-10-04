package dev.hycolony.core.citizen;

import dev.hycolony.core.citizen.food.CitizenEating;
import dev.hycolony.core.citizen.minimal.MinimalAIs;
import dev.hycolony.core.citizen.sleep.CitizenSleep;
import dev.hycolony.core.citizen.sleep.SleepDecision;
import dev.hycolony.core.citizen.vitals.AiWatch;
import dev.hycolony.core.citizen.vitals.WorkExit;
import dev.hycolony.core.citizen.wander.CitizenWander;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.job.CitizenWork;
import dev.hycolony.core.job.JobAI;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.ai.AIBlockingEventType;
import dev.hycolony.core.kernel.ai.AIEventTarget;
import dev.hycolony.core.kernel.ai.AITarget;
import dev.hycolony.core.kernel.ai.IStateSupplier;
import dev.hycolony.core.kernel.ai.TickRateStateMachine;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.kernel.port.Msg;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Top-level citizen AI: sleep, eat, mourn, flee, idle (wandering around) or work its job. Port of MC
 * CitizenAI.calculateNextState in MC's order, sleep then hunger then mourning then rain then work (sickness and raids
 * are not ported), a hit making it flee ({@link MinimalAIs}); when it works is its {@link CitizenWork}'s.
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
    private final CitizenWander wander;
    private final TickRateStateMachine<CitizenState> machine;
    private final AiWatch watch;
    private final CommandedWalk commanded;
    private final CitizenSleep sleep;
    private final CitizenEating eating;
    private final CitizenWork work;
    private final MinimalAIs minimal;
    /**
     * Whether the last decision was EATING (MC {@code lastState == EATING}): it stays so after a meal ends, so a
     * citizen whose meal ended hungry goes back to eat at once, as in MC.
     */
    private boolean decidedEating;

    private boolean failed;

    /** Starts at the idle state and at normal walking speed. */
    public CitizenAI(Colony colony, CitizenData data, BodyId body) {
        this.colony = colony;
        this.data = data;
        this.body = body;
        this.machine = new TickRateStateMachine<>(CitizenState.IDLE, this::onException);
        this.wander = new CitizenWander(colony, data, body, machine::setCurrentDelay);
        this.watch = new AiWatch(colony, data);
        this.commanded = CommandedWalk.of(colony, data, body);
        this.sleep = new CitizenSleep(colony, data, body);
        this.eating = new CitizenEating(colony, data, body);
        this.work = new CitizenWork(colony, data, body, watch, wander);
        watch.afterTick(CitizenState.IDLE, null, 0); // its vital signs know where it starts
        machine.addTransition(
                new AITarget<>(CitizenState.IDLE, (IStateSupplier<CitizenState>) work::idle, DECIDE_INTERVAL_TICKS));
        machine.addTransition(new AITarget<>(
                CitizenState.IDLE, (IStateSupplier<CitizenState>) wander::wander, CitizenWander.WANDER_RATE_TICKS));
        machine.addTransition(
                new AITarget<>(CitizenState.IDLE, (IStateSupplier<CitizenState>) wander::leisure, LEISURE_RATE_TICKS));
        machine.addTransition(new AITarget<>(CitizenState.WORKING, (IStateSupplier<CitizenState>) work::work, 1));
        machine.addTransition(new AIEventTarget<>(AIBlockingEventType.EVENT, this::decide, DECIDE_INTERVAL_TICKS));
        machine.addTransition(new AITarget<>(CitizenState.SLEEP, (IStateSupplier<CitizenState>) this::sleeping, 1));
        machine.addTransition(new AITarget<>(CitizenState.EATING, (IStateSupplier<CitizenState>) this::eat, 1));
        this.minimal = new MinimalAIs(colony, data, body, wander, machine);
        sleep.onBodyAppeared();
        // A body can keep a job's speed across a crash (the Hytale effect is saved with the NPC); a job AI sets its
        // own.
        colony.context().bodies().setMovementSpeed(body, 1);
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
            if (machine.getState() != CitizenState.WORKING) {
                work.resetOnNextWork();
            }
            watch.afterTick(machine.getState(), work.ai().orElse(null), work.actionsDone());
        } finally {
            colony.context().timings().stop(work.timingPart(), start);
        }
    }

    public CitizenState state() {
        return machine.getState();
    }

    /**
     * MC CommandCitizenTriggerWalkTo: the citizen walks to {@code target} (see {@link CommandedWalk}), its AI waiting;
     * a new command replaces the walk under way. Deviation from MC: its job AI is then reset when it works again
     * ({@link JobAI#resetAI}), as its walkers would believe it where it was (MC's walks keep no state); its fields
     * stay.
     */
    public void walkTo(BlockPos target) {
        moved();
        if (!commanded.active()) {
            machine.addTransition(commanded.transition(machine::getState));
        }
        commanded.start(target);
    }

    /**
     * After a hit: woken if asleep; then (MC EntityCitizen.performMoveAway, but from a {@code fall}) it runs from
     * {@code attacker} (where it stands) and flees ({@link MinimalAIs}) from the next tick, leaving its meal or work;
     * hurt by no entity, it only steps away.
     */
    public void hit(@Nullable Vec3 attacker, boolean fall) {
        sleep.hurtAwake();
        if (!fall) {
            minimal.hit(Optional.ofNullable(attacker), now -> leave(now, WorkExit.FLEE));
        }
    }

    /** It leaves {@code now} for a minimal AI: its meal ends, its work is noted left for {@code why}. */
    private void leave(CitizenState now, WorkExit why) {
        leaveEating(now);
        if (now == CitizenState.WORKING) {
            watch.leftWork(why);
        }
        wander.restartWait();
    }

    /**
     * Teleports its body to {@code to}, woken first (MC TeleportHelper); its job AI is reset when it works again, as
     * for {@link #walkTo}.
     */
    public void teleport(Vec3 to) {
        sleep.wakeUp();
        moved();
        colony.context().bodies().teleport(body, to);
    }

    /** A command moved it: its job AI is reset at its next work tick; the wander waits for walks seen from now. */
    private void moved() {
        work.resetOnNextWork();
        wander.restartWait();
    }

    /** It was just fired ({@link dev.hycolony.core.job.WorkerModule#free}): its job AI and job speed go now. */
    public void jobLost() {
        work.jobLost();
    }

    /** Its job's AI, while it has a job; for diagnostics. */
    public Optional<JobAI> jobAi() {
        return work.ai();
    }

    /** The job AI's own line while working. */
    public Optional<Msg> jobActivity() {
        return state() == CitizenState.WORKING ? work.activity() : Optional.empty();
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

    /**
     * MC CitizenAI.decideAiTask, every {@link #DECIDE_INTERVAL_TICKS} in any state: the sleep part first (see
     * {@link SleepDecision}; asleep, it decides again only every 15 s, MC setCurrentDelay), then the hunger part.
     */
    private @Nullable CitizenState decide() {
        CitizenState now = machine.getState();
        if (now == CitizenState.FLEE) {
            return minimal.decideWhileFleeing(this::decideFrom, work::shouldWork);
        }
        CitizenState next = decideFrom(now);
        return next == now ? null : next;
    }

    /** The sleep, hunger and mourning parts of MC calculateNextState from state {@code now}; null to stay asleep. */
    private @Nullable CitizenState decideFrom(CitizenState now) {
        return switch (sleep.decide(now == CitizenState.SLEEP)) {
            case STAY_ASLEEP -> {
                machine.setCurrentDelay(SleepDecision.SLEEP_DECIDE_DELAY_TICKS);
                yield null;
            }
            case GO_TO_SLEEP -> {
                decidedEating = false;
                leaveEating(now);
                wander.restartWait(); // a walk under way is waited for from now
                watch.leftWork(WorkExit.SLEEP); // even mid-task: MC's sleep part comes before canBeInterrupted
                yield CitizenState.SLEEP;
            }
            case WAKE_UP -> mourning(decideHunger(now == CitizenState.SLEEP ? CitizenState.IDLE : now));
            case NONE -> mourning(decideHunger(now));
        };
    }

    /** The mourning part after {@code next}, the hunger part's ({@link MinimalAIs#decideMourning}). */
    private CitizenState mourning(CitizenState next) {
        return minimal.decideMourning(next, left -> leave(left, WorkExit.MOURN), work::shouldWork);
    }

    /**
     * MC calculateNextState's hunger part, after the sleep part and before the rain and work: to EATING when it should
     * eat (its job AI kept, as on leaving WORK), judged as still eating after a decision to eat (MC lastState); out
     * of EATING once it should not, straight to work when it should work. {@code now} is the state the sleep part left
     * it in.
     */
    private CitizenState decideHunger(CitizenState now) {
        boolean eatingNow = now == CitizenState.EATING;
        decidedEating = eating.shouldEat(decidedEating || eatingNow, work.interruptible());
        if (decidedEating) {
            if (!eatingNow) {
                wander.restartWait();
                watch.leftWork(WorkExit.MEAL);
            }
            return CitizenState.EATING;
        }
        if (eatingNow) {
            eating.stop();
            return work.shouldWork() ? CitizenState.WORKING : CitizenState.IDLE;
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
}
