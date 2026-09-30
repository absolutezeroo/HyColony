package dev.hycolony.core.kernel.ai;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * Port of MineColonies' BasicStateMachine + TickRateStateMachine.
 * Each tick evaluates AI_BLOCKING, EVENT, STATE_BLOCKING, then the current state's transitions;
 * the first transition that returns a state ends the tick.
 */
public class TickRateStateMachine<S extends IState> {
    private final Map<S, List<TickingTransition<S>>> transitionMap = new HashMap<>();
    private final List<TickingTransition<S>> aiBlocking = new ArrayList<>();
    private final List<TickingTransition<S>> stateBlocking = new ArrayList<>();
    private final List<TickingTransition<S>> events = new ArrayList<>();
    private final S initState;
    /** The initial state's transitions: mapped at construction and never unmapped. */
    private final List<TickingTransition<S>> initStateTransitions;

    private final Consumer<RuntimeException> exceptionHandler;

    private List<TickingTransition<S>> currentStateTransitions;
    private S state;
    private final int tickRate;
    private @Nullable TickingTransition<S> executedTransition;

    public TickRateStateMachine(S initialState, Consumer<RuntimeException> exceptionHandler) {
        this(initialState, exceptionHandler, 1);
    }

    /** A machine whose every tick counts {@code tickRate} ticks off its transitions' countdowns. */
    public TickRateStateMachine(S initialState, Consumer<RuntimeException> exceptionHandler, int tickRate) {
        this.initState = initialState;
        this.state = initialState;
        this.exceptionHandler = exceptionHandler;
        this.initStateTransitions = new ArrayList<>();
        this.currentStateTransitions = initStateTransitions;
        this.tickRate = tickRate;
        transitionMap.put(initialState, initStateTransitions);
    }

    public void addTransition(TickingTransition<S> transition) {
        S at = transition.getState();
        if (at != null) {
            transitionMap.computeIfAbsent(at, _ -> new ArrayList<>()).add(transition);
        }
        IStateEventType type = transition.getEventType();
        if (type != null) {
            eventList(type).add(transition);
        }
    }

    public void removeTransition(TickingTransition<S> transition) {
        IStateEventType type = transition.getEventType();
        if (type != null) {
            eventList(type).removeIf(transition::equals);
            return;
        }
        List<TickingTransition<S>> ofState = transitionMap.get(transition.getState());
        if (ofState != null) {
            ofState.removeIf(transition::equals);
        }
    }

    private List<TickingTransition<S>> eventList(IStateEventType type) {
        if (!(type instanceof AIBlockingEventType t)) {
            throw new IllegalArgumentException("Unsupported event type " + type);
        }
        return switch (t) {
            case AI_BLOCKING -> aiBlocking;
            case STATE_BLOCKING -> stateBlocking;
            case EVENT -> events;
        };
    }

    public void tick() {
        if (runGroup(aiBlocking) || runGroup(events) || runGroup(stateBlocking)) {
            return;
        }
        runGroup(currentStateTransitions);
    }

    private boolean runGroup(List<TickingTransition<S>> group) {
        for (int i = 0, size = group.size(); i < size && i < group.size(); i++) {
            if (checkTransition(group.get(i))) {
                return true;
            }
        }
        return false;
    }

    private boolean checkTransition(TickingTransition<S> transition) {
        if (transition.countdownTicksToUpdate(tickRate) > 0) {
            return false;
        }
        transition.setTicksToUpdate(transition.getTickRate());
        executedTransition = transition;
        try {
            if (!transition.checkCondition()) {
                return false;
            }
        } catch (RuntimeException e) {
            exceptionHandler.accept(e);
            return false;
        }
        return transitionToNext(transition);
    }

    private boolean transitionToNext(TickingTransition<S> transition) {
        final S newState;
        try {
            newState = transition.getNextState();
        } catch (RuntimeException e) {
            exceptionHandler.accept(e);
            return false;
        }
        if (newState == null) {
            return false;
        }
        if (transition.isOneTime()) {
            removeTransition(transition);
        }
        if (!newState.equals(state)) {
            List<TickingTransition<S>> next = transitionMap.get(newState);
            if (next == null || next.isEmpty()) {
                exceptionHandler.accept(new IllegalStateException("Missing AI transition for state: " + newState));
                reset();
                return true;
            }
            currentStateTransitions = next;
        }
        state = newState;
        return true;
    }

    public S getState() {
        return state;
    }

    public void reset() {
        state = initState;
        currentStateTransitions = initStateTransitions;
    }

    /** Overrides the countdown of the transition currently executing; nothing before any transition ran. */
    public void setCurrentDelay(int ticksToNext) {
        if (executedTransition != null) {
            executedTransition.setTicksToUpdate(ticksToNext);
        }
    }
}
