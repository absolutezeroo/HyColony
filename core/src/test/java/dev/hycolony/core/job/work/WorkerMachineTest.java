package dev.hycolony.core.job.work;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.kernel.ai.IState;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The worker AI shell of MC AbstractAISkeleton / AbstractEntityAIBasic: its rate, its wait and its exception. */
class WorkerMachineTest {
    private enum Step implements IState {
        FIRST,
        SECOND,
        THIRD
    }

    private final WorkDelay delay = new WorkDelay();
    private final WorkerMachine<Step> machine = new WorkerMachine<>(
            Step.FIRST, () -> "Tester", () -> delay.waiting(WorkerMachine.MACHINE_RATE), delay::set);

    private void tick(int gameTicks) {
        for (int i = 0; i < gameTicks; i++) {
            machine.tick();
        }
    }

    @Test
    void machineRunsOnceEveryMachineRateGameTicks() {
        machine.target(Step.FIRST, () -> Step.SECOND, 1);
        machine.target(Step.SECOND, () -> Step.THIRD, 1);

        tick(WorkerMachine.MACHINE_RATE - 1);
        assertEquals(Step.FIRST, machine.state());
        tick(1);
        assertEquals(Step.SECOND, machine.state());
    }

    /** MC AbstractAISkeleton.resetAI: the machine goes back to its first state and runs on from there. */
    @Test
    void resetPutsTheMachineBackInItsFirstState() {
        machine.target(Step.FIRST, () -> Step.SECOND, 1);
        machine.target(Step.SECOND, () -> null, 1);
        tick(WorkerMachine.MACHINE_RATE);
        assertEquals(Step.SECOND, machine.state());

        machine.reset();

        assertEquals(Step.FIRST, machine.state());
        tick(WorkerMachine.MACHINE_RATE);
        assertEquals(Step.SECOND, machine.state());
    }

    @Test
    void machineStaysInItsStateWhileTheWorkerWaits() {
        machine.target(Step.FIRST, () -> Step.SECOND, 1);
        machine.target(Step.SECOND, () -> null, 1);
        delay.set(2 * WorkerMachine.MACHINE_RATE);

        tick(2 * WorkerMachine.MACHINE_RATE);
        assertEquals(Step.FIRST, machine.state());
        tick(WorkerMachine.MACHINE_RATE);
        assertEquals(Step.SECOND, machine.state());
    }

    /**
     * MC AbstractEntityAIBasic.onException: the state stays; setDelay then setCurrentDelay of EXCEPTION_DELAY times a
     * doubling timer, one after the other, so the next tries come 200 then 400 ticks later.
     */
    @Test
    void anExceptionPausesTheWorkerLongerEachTimeInTheSameState() {
        List<Integer> thrownAt = new ArrayList<>();
        int[] now = {0};
        machine.target(Step.FIRST, () -> Step.SECOND, 1);
        machine.target(
                Step.SECOND,
                () -> {
                    thrownAt.add(now[0]);
                    throw new IllegalStateException("broken step");
                },
                1);

        for (; now[0] < 8 * WorkerMachine.EXCEPTION_DELAY && thrownAt.size() < 3; now[0]++) {
            machine.tick();
        }

        assertEquals(Step.SECOND, machine.state());
        assertTrue(machine.lastError().isPresent());
        assertEquals(3, thrownAt.size(), "exceptions at " + thrownAt);
        assertEquals(3, machine.failures(), "each exception counted, for the citizen's vital signs");
        int first = thrownAt.get(1) - thrownAt.get(0);
        int second = thrownAt.get(2) - thrownAt.get(1);
        assertTrue(
                Math.abs(first - 2 * WorkerMachine.EXCEPTION_DELAY) <= WorkerMachine.MACHINE_RATE,
                "first pause " + first);
        assertTrue(
                Math.abs(second - 4 * WorkerMachine.EXCEPTION_DELAY) <= WorkerMachine.MACHINE_RATE,
                "second pause " + second);
    }
}
