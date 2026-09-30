package dev.hycolony.core.job.work;

import dev.hycolony.core.kernel.ai.AIBlockingEventType;
import dev.hycolony.core.kernel.ai.AIEventTarget;
import dev.hycolony.core.kernel.ai.AITarget;
import dev.hycolony.core.kernel.ai.IState;
import dev.hycolony.core.kernel.ai.IStateSupplier;
import dev.hycolony.core.kernel.ai.TickRateStateMachine;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * A worker AI's state machine, run as MC AbstractAISkeleton and AbstractEntityAIBasic run theirs: once every
 * {@link #MACHINE_RATE} game ticks, kept in its state while the worker waits (MC's AI_BLOCKING waitingForSomething
 * event, registered first), and after an exception logged and paused, longer each time. Each job's AI registers its
 * own targets and events on it.
 */
public final class WorkerMachine<S extends IState> {
    /** MC ENTITY_AI_TICKRATE: the machine runs every 5 game ticks and counts 5 per run. */
    public static final int MACHINE_RATE = 5;

    /** MC EXCEPTION_TIMEOUT: the ticks a worker pauses after its AI's first exception, doubled at each next one. */
    public static final int EXCEPTION_DELAY = 100;

    /**
     * Deviation from MC: MC's {@code 100 * exceptionTimer} overflows the int from the 26th exception on; the timer
     * stops doubling here, at about 61 days of pause.
     */
    private static final int MAX_EXCEPTION_TIMER = 1 << 20;

    private static final System.Logger LOG = System.getLogger(WorkerMachine.class.getName());

    private final TickRateStateMachine<S> machine;
    private final Supplier<String> worker;
    private final IntConsumer pause;
    private @Nullable RuntimeException lastError;
    private int calls;
    private int exceptionTimer = 1;

    /**
     * A machine starting in {@code initial} for the worker {@code worker} names (in the log). It stays in its state
     * while {@code waiting} is true; after an exception it keeps its state and {@code pause} gets
     * {@link #EXCEPTION_DELAY} times a timer that doubles at each exception.
     */
    public WorkerMachine(S initial, Supplier<String> worker, BooleanSupplier waiting, IntConsumer pause) {
        this.machine = new TickRateStateMachine<>(initial, this::onException, MACHINE_RATE);
        this.worker = worker;
        this.pause = pause;
        machine.addTransition(
                new AIEventTarget<>(AIBlockingEventType.AI_BLOCKING, waiting, machine::getState, MACHINE_RATE));
    }

    /** A target of {@code state}: every {@code rate} ticks, {@code action} gives the next state (null: stay). */
    public void target(S state, IStateSupplier<S> action, int rate) {
        machine.addTransition(new AITarget<>(state, action, rate));
    }

    /** A target of {@code state} that runs {@code action} only when {@code when} holds. */
    public void target(S state, BooleanSupplier when, IStateSupplier<S> action, int rate) {
        machine.addTransition(new AITarget<>(state, when, action, rate));
    }

    /** An event checked every {@code rate} ticks in any state: when {@code when} holds, {@code then} is next. */
    public void event(AIBlockingEventType type, BooleanSupplier when, IStateSupplier<S> then, int rate) {
        machine.addTransition(new AIEventTarget<>(type, when, then, rate));
    }

    /** Called every game tick: runs the machine every {@link #MACHINE_RATE} of them. */
    public void tick() {
        if (++calls < MACHINE_RATE) {
            return;
        }
        calls = 0;
        machine.tick();
    }

    public S state() {
        return machine.getState();
    }

    /** The last exception the machine caught; empty while none did (the tests assert so). */
    public Optional<RuntimeException> lastError() {
        return Optional.ofNullable(lastError);
    }

    /**
     * MC AbstractEntityAIBasic.onException: pauses the worker, longer each time; the state stays. Deviation from MC:
     * no WORKER_AI_EXCEPTION interaction (HyColony has no citizen interactions yet), only the log.
     */
    private void onException(RuntimeException e) {
        int timeout = EXCEPTION_DELAY * exceptionTimer;
        LOG.log(
                System.Logger.Level.WARNING,
                "Worker AI failed for " + worker.get() + "; paused " + timeout + " ticks",
                e);
        lastError = e;
        // MC setDelay then setCurrentDelay: the failed target counts down only once the worker's delay is over (the
        // AI_BLOCKING wait ends the machine's tick first), so the next try comes 2 * timeout later, as in MC.
        pause.accept(timeout);
        machine.setCurrentDelay(timeout);
        if (exceptionTimer < MAX_EXCEPTION_TIMER) {
            exceptionTimer *= 2;
        }
    }
}
