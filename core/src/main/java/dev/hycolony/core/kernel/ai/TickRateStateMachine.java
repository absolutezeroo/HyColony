package dev.hycolony.core.kernel.ai;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Port of MineColonies' BasicStateMachine + TickRateStateMachine.
 * Each tick evaluates AI_BLOCKING, EVENT, STATE_BLOCKING, then the current state's transitions;
 * the first transition that returns a state ends the tick.
 */
public class TickRateStateMachine<S extends IState> {
    private static final int HISTORY_SIZE = 20;

    private final Map<S, List<TickingTransition<S>>> transitionMap = new HashMap<>();
    private final List<TickingTransition<S>> aiBlocking = new ArrayList<>();
    private final List<TickingTransition<S>> stateBlocking = new ArrayList<>();
    private final List<TickingTransition<S>> events = new ArrayList<>();
    private final Deque<String> history = new ArrayDeque<>(HISTORY_SIZE);
    private final S initState;
    private final Consumer<RuntimeException> exceptionHandler;

    private List<TickingTransition<S>> currentStateTransitions;
    private S state;
    private int tickRate = 1;
    private TickingTransition<S> executedTransition;

    public TickRateStateMachine(S initialState, Consumer<RuntimeException> exceptionHandler) {
        this.initState = initialState;
        this.state = initialState;
        this.exceptionHandler = exceptionHandler;
        this.currentStateTransitions = new ArrayList<>();
        transitionMap.put(initialState, currentStateTransitions);
    }

    public TickRateStateMachine(S initialState, Consumer<RuntimeException> exceptionHandler, int tickRate) {
        this(initialState, exceptionHandler);
        this.tickRate = tickRate;
    }

    public void addTransition(TickingTransition<S> transition) {
        if (transition.getState() != null) {
            transitionMap
                    .computeIfAbsent(transition.getState(), k -> new ArrayList<>())
                    .add(transition);
        }
        if (transition.getEventType() != null) {
            eventList(transition.getEventType()).add(transition);
        }
    }

    public void removeTransition(TickingTransition<S> transition) {
        if (transition.getEventType() != null) {
            eventList(transition.getEventType()).removeIf(t -> t == transition);
        } else {
            transitionMap.get(transition.getState()).removeIf(t -> t == transition);
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
        if (newState != state) {
            currentStateTransitions = transitionMap.get(newState);
            if (currentStateTransitions == null || currentStateTransitions.isEmpty()) {
                exceptionHandler.accept(new IllegalStateException("Missing AI transition for state: " + newState));
                reset();
                return true;
            }
            if (history.size() == HISTORY_SIZE) {
                history.removeFirst();
            }
            history.addLast(state + "->" + newState);
        }
        state = newState;
        return true;
    }

    public S getState() {
        return state;
    }

    public void reset() {
        state = initState;
        currentStateTransitions = transitionMap.get(initState);
    }

    /** Overrides the countdown of the transition currently executing. */
    public void setCurrentDelay(int ticksToNext) {
        executedTransition.setTicksToUpdate(ticksToNext);
    }

    public int getTickRate() {
        return tickRate;
    }

    public void setTickRate(int tickRate) {
        this.tickRate = tickRate;
    }

    public List<String> history() {
        return List.copyOf(history);
    }
}
