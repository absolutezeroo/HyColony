package dev.hycolony.core.job.work;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.kernel.ai.IState;
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

    /** MC AbstractEntityAIBasic.onException: the state stays, the pause is EXCEPTION_DELAY times a doubling timer. */
    @Test
    void anExceptionPausesTheWorkerLongerEachTimeInTheSameState() {
        machine.target(Step.FIRST, () -> Step.SECOND, 1);
        machine.target(
                Step.SECOND,
                () -> {
                    throw new IllegalStateException("broken step");
                },
                1);

        tick(2 * WorkerMachine.MACHINE_RATE);
        assertEquals(Step.SECOND, machine.state());
        assertEquals(WorkerMachine.EXCEPTION_DELAY, delay.remaining());
        assertTrue(machine.lastError().isPresent());

        for (int i = 0;
                i < 4 * WorkerMachine.EXCEPTION_DELAY && delay.remaining() <= WorkerMachine.EXCEPTION_DELAY;
                i++) {
            machine.tick(); // until the next exception, once this pause is over
        }
        assertEquals(Step.SECOND, machine.state());
        assertEquals(2 * WorkerMachine.EXCEPTION_DELAY, delay.remaining());
    }
}
