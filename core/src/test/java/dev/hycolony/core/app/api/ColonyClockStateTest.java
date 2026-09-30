package dev.hycolony.core.app.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

/** One world's colony clock, as a debugging tool pauses and steps it (spec 2026-09-30, § 5, clock). */
class ColonyClockStateTest {
    private final ColonyClockState clock = new ColonyClockState();

    @Test
    void runningClockLetsEveryDueTickRun() {
        assertEquals(3, clock.allow(3));
        assertFalse(clock.paused());
    }

    @Test
    void pausedClockRunsOnlyItsSteps() {
        assertTrue(clock.pause("Tests:HyLens"));
        assertEquals(0, clock.allow(3));

        assertTrue(clock.step(4));

        assertEquals(1, clock.allow(1), "at the pace of time: the bodies walk as they would");
        assertEquals(3, clock.allow(5), "the steps left");
        assertEquals(0, clock.allow(3), "once");
    }

    @Test
    void stepsStayPendingThroughAServerTickWithNoCoreTickDue() {
        clock.pause("Tests:HyLens");
        clock.step(4);
        clock.allow(1);

        assertEquals(0, clock.allow(0), "a world ticks 30 times a second, the core 20");
        assertTrue(clock.stepsPending(), "the bodies keep walking");
        clock.allow(3);
        assertFalse(clock.stepsPending());
    }

    @Test
    void stepsAreBoundedAsTheTickSystemsCatchUp() {
        clock.pause("Tests:HyLens");

        clock.step(6);
        clock.step(6);

        assertEquals(ColonyClockState.MAX_STEP, clock.allow(20));
    }

    @Test
    void steppingARunningClockIsRefused() {
        assertFalse(clock.step(3));
        assertEquals(2, clock.allow(2));
    }

    @Test
    void anotherOwnerCannotTakeThePause() {
        clock.pause("Tests:HyLens");

        assertFalse(clock.pause("Tests:Other"));
        assertTrue(clock.pause("Tests:HyLens"), "its owner may pause again");
        assertEquals(Optional.of("Tests:HyLens"), clock.owner());
    }

    @Test
    void resumingDropsTheStepsNotRun() {
        clock.pause("Tests:HyLens");
        clock.step(5);

        clock.resume();

        assertEquals(1, clock.allow(1));
        assertEquals(Optional.empty(), clock.owner());
        clock.pause("Tests:HyLens");
        assertEquals(0, clock.allow(1), "paused again: the old steps are gone");
    }

    @Test
    void ownerStoppingOnAnotherThreadLiftsThePauseAtTheNextTick() throws InterruptedException {
        clock.pause("Tests:HyLens");
        clock.step(3);

        Thread unloading = new Thread(() -> clock.release("Tests:HyLens"));
        unloading.start();
        unloading.join();

        assertEquals(2, clock.allow(2), "running again, its steps dropped");
        assertFalse(clock.paused());
    }

    @Test
    void ownerStoppedBeforeItsPauseRanCannotPause() {
        clock.release("Tests:HyLens");

        assertFalse(clock.pause("Tests:HyLens"), "its pause would never be lifted");
        assertFalse(clock.paused());
    }

    @Test
    void ownerStoppedLongBeforeCannotPauseEither() {
        clock.release("Tests:HyLens");
        clock.allow(1);

        assertFalse(clock.pause("Tests:HyLens"));
        assertEquals(1, clock.allow(1));
    }

    @Test
    void anotherPluginStoppingLeavesThePause() {
        clock.pause("Tests:HyLens");

        clock.release("Tests:Other");

        assertTrue(clock.paused());
        assertEquals(0, clock.allow(2));
    }
}
