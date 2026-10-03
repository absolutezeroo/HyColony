package dev.hycolony.core.job;

import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.vitals.WorkExit;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.kernel.BlockPos;
import java.util.Optional;

/** MC CitizenAI.calculateNextState's reasons for one worker to stop working: the rain, nothing to do, a break. */
public final class WorkStops {
    private static final Optional<WorkExit> RAIN = Optional.of(WorkExit.RAIN);
    private static final Optional<WorkExit> IDLE = Optional.of(WorkExit.IDLE);
    private static final Optional<WorkExit> BREAK = Optional.of(WorkExit.BREAK);

    private final Colony colony;
    private final CitizenData data;

    public WorkStops(Colony colony, CitizenData data) {
        this.colony = colony;
        this.data = data;
    }

    /** Why the worker with job AI {@code ai} should stop now, in MC's order: rain, nothing to do, break; else empty. */
    public Optional<WorkExit> exit(JobAI ai) {
        if (rainStopsWork()) {
            return RAIN;
        }
        if (ai.canGoIdle()) {
            return IDLE;
        }
        return onBreak(ai) ? BREAK : Optional.empty();
    }

    /**
     * MC calculateNextState: a citizen on leisure ({@link CitizenData#leisureTime}) idles, unless its job AI cannot be
     * interrupted right now.
     */
    public boolean onBreak(JobAI ai) {
        return data.leisureTime() > 0 && ai.canBeInterrupted();
    }

    /**
     * MC calculateNextState: while it rains a worker idles, even mid-task, unless shouldWorkWhileRaining (the config
     * workersAlwaysWorkInRain, or its hut's {@link WorkerModule#canWorkDuringTheRain}). Deviation from MC: rain or
     * snow at the work hut (Hytale weather is per zone), not a world-wide flag; no WORKING_IN_RAIN research nor
     * BAD_WEATHER status line; a worker without a work hut is left to its job AI (MC idles it).
     */
    public boolean rainStopsWork() {
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
}
