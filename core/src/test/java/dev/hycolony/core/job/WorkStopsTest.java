package dev.hycolony.core.job;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.vitals.WorkExit;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.permission.Permissions;
import dev.hycolony.core.colony.territory.TerritoryIndex;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.testing.TestContexts;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** MC CitizenAI.calculateNextState's reasons to stop working, in its order: rain, nothing to do, a break. */
class WorkStopsTest {
    private final TestContexts t = new TestContexts();
    private final Colony colony = new Colony(
            t.context(),
            new TerritoryIndex(),
            new Colony.Founding(1, "T", new BlockPos(0, 64, 0), Permissions.createDefault(UUID.randomUUID(), "A")));
    private final CitizenData data = new CitizenData(1);
    private final WorkStops stops = new WorkStops(colony, data);

    /** A job AI that may or may not go idle, or be interrupted. */
    private static JobAI ai(boolean canGoIdle, boolean interruptible) {
        return new JobAI() {
            @Override
            public void tick() {}

            @Override
            public String stateName() {
                return "WORK";
            }

            @Override
            public boolean canBeInterrupted() {
                return interruptible;
            }

            @Override
            public boolean canGoIdle() {
                return canGoIdle;
            }
        };
    }

    @Test
    void nothingToDoComesBeforeABreak() {
        data.setLeisureTime(100);

        assertEquals(Optional.of(WorkExit.IDLE), stops.exit(ai(true, true)));
    }

    @Test
    void anInterruptibleWorkerOnLeisureTakesABreak() {
        data.setLeisureTime(100);

        assertEquals(Optional.of(WorkExit.BREAK), stops.exit(ai(false, true)));
    }

    @Test
    void aWorkerThatCannotBeInterruptedWorksOnThroughItsLeisure() {
        data.setLeisureTime(100);

        assertEquals(Optional.empty(), stops.exit(ai(false, false)));
    }

    @Test
    void withoutLeisureNorIdleItWorksOn() {
        assertEquals(Optional.empty(), stops.exit(ai(false, true)));
    }
}
