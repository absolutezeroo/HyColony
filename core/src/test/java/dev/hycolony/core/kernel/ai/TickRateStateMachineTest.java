package dev.hycolony.core.kernel.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TickRateStateMachineTest {
    enum S implements IState {
        A,
        B,
        C,
        EMPTY
    }

    private final List<RuntimeException> errors = new ArrayList<>();
    private final List<String> log = new ArrayList<>();

    @BeforeEach
    void resetOffsets() {
        TickingTransition.resetOffsetVariant();
    }

    private TickRateStateMachine<S> machine() {
        return new TickRateStateMachine<>(S.A, errors::add);
    }

    private IStateSupplier<S> record(String name, S next) {
        return () -> {
            log.add(name);
            return next;
        };
    }

    @Test
    void evaluationOrderIsAiBlockingEventStateBlockingThenState() {
        var sm = machine();
        sm.addTransition(new AITarget<>(S.A, record("state", null), 1));
        sm.addTransition(new AIEventTarget<>(AIBlockingEventType.STATE_BLOCKING, record("stateBlocking", null), 1));
        sm.addTransition(new AIEventTarget<>(AIBlockingEventType.EVENT, record("event", null), 1));
        sm.addTransition(new AIEventTarget<>(AIBlockingEventType.AI_BLOCKING, record("aiBlocking", null), 1));
        sm.tick();
        assertEquals(List.of("aiBlocking", "event", "stateBlocking", "state"), log);
    }

    @Test
    void firstTransitionReturningAStateEndsTheTick() {
        var sm = machine();
        sm.addTransition(new AIEventTarget<>(AIBlockingEventType.AI_BLOCKING, record("first", S.A), 1));
        sm.addTransition(new AITarget<>(S.A, record("never", S.B), 1));
        sm.tick();
        assertEquals(List.of("first"), log);
        assertEquals(S.A, sm.getState());
    }

    @Test
    void transitionSwitchesToTargetStateTransitions() {
        var sm = machine();
        sm.addTransition(new AITarget<>(S.A, S.B, 1));
        sm.addTransition(new AITarget<>(S.B, record("inB", null), 1));
        sm.tick();
        assertEquals(S.B, sm.getState());
        sm.tick();
        assertEquals(List.of("inB"), log);
    }

    @Test
    void tickRateLimitsHowOftenATransitionRuns() {
        var sm = machine();
        sm.addTransition(new AITarget<>(S.A, record("x", null), 3));
        for (int i = 0; i < 7; i++) {
            sm.tick();
        }
        assertEquals(3, log.size()); // ticks 1, 4, 7
    }

    @Test
    void tickRateIsClampedToAtLeastOne() {
        var sm = machine();
        sm.addTransition(new AITarget<>(S.A, record("x", null), 0));
        sm.tick();
        sm.tick();
        assertEquals(2, log.size());
    }

    @Test
    void setCurrentDelayPostponesTheExecutedTransition() {
        var sm = machine();
        List<Integer> runs = new ArrayList<>();
        int[] tick = {0};
        sm.addTransition(new AITarget<>(
                S.A,
                (IStateSupplier<S>) () -> {
                    runs.add(tick[0]);
                    sm.setCurrentDelay(5);
                    return null;
                },
                1));
        for (tick[0] = 1; tick[0] <= 7; tick[0]++) {
            sm.tick();
        }
        assertEquals(List.of(1, 6), runs);
    }

    @Test
    void setCurrentDelayBeforeAnyTransitionRanDoesNothing() {
        var sm = machine();
        sm.setCurrentDelay(5);
        assertEquals(S.A, sm.getState());
    }

    @Test
    void removingATransitionOfAStateWithoutTransitionsDoesNothing() {
        var sm = machine();
        sm.removeTransition(new AITarget<>(S.B, S.A, 1));
        assertEquals(S.A, sm.getState());
    }

    @Test
    void missingTransitionsForNewStateReportsAndResets() {
        var sm = machine();
        sm.addTransition(new AITarget<>(S.A, S.EMPTY, 1));
        sm.tick();
        assertEquals(1, errors.size());
        assertEquals(S.A, sm.getState());
    }

    @Test
    void oneTimeEventIsRemovedAfterFiring() {
        var sm = machine();
        sm.addTransition(new AIOneTimeEventTarget<>(record("once", S.A)));
        sm.addTransition(new AITarget<>(S.A, record("state", null), 1));
        sm.tick();
        sm.tick();
        assertEquals(List.of("once", "state"), log);
    }

    @Test
    void exceptionInConditionIsReportedAndEvaluationContinues() {
        var sm = machine();
        sm.addTransition(new AITarget<>(
                S.A,
                () -> {
                    throw new IllegalStateException("cond");
                },
                record("never", null),
                1));
        sm.addTransition(new AITarget<>(S.A, record("next", null), 1));
        sm.tick();
        assertEquals(1, errors.size());
        assertEquals(List.of("next"), log);
    }

    @Test
    void exceptionInActionIsReported() {
        var sm = machine();
        sm.addTransition(new AITarget<>(
                S.A,
                (IStateSupplier<S>) () -> {
                    throw new IllegalStateException("act");
                },
                1));
        sm.tick();
        assertEquals(1, errors.size());
        assertEquals(S.A, sm.getState());
    }

    @Test
    void historyKeepsLastTransitions() {
        var sm = machine();
        sm.addTransition(new AITarget<>(S.A, S.B, 1));
        sm.addTransition(new AITarget<>(S.B, S.A, 1));
        for (int i = 0; i < 30; i++) {
            sm.tick();
        }
        assertEquals(20, sm.history().size());
        assertTrue(sm.history().getLast().endsWith("->A"));
    }
}
