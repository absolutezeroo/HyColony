package dev.hycolony.core.app.diagnostics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.hycolony.core.citizen.CitizenState;
import dev.hycolony.core.citizen.vitals.AiWatch;
import dev.hycolony.core.job.JobAI;
import dev.hycolony.core.request.model.RequestState;
import dev.hycolony.core.request.model.RequestToken;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The invariants read from a working citizen's job AI: its step (2) and its queue (3). */
class JobInvariantsTest {
    private final DiagnosedColony c = new DiagnosedColony();

    @Test
    void jobStepUnchangedForFiveMinutesIsReported() {
        c.working();
        long since = c.t.clock.tick;

        c.t.clock.tick = since + Invariants.JOB_STEP_STALE_TICKS - 1;
        assertEquals(List.of(), c.codes());
        c.t.clock.tick = since + Invariants.JOB_STEP_STALE_TICKS;
        assertEquals(List.of(Violation.Code.JOB_STEP_STALE), c.codes());
    }

    @Test
    void waitingJobIsNeverStale() {
        c.working();
        c.waiting = true;

        c.t.clock.tick += Invariants.JOB_STEP_STALE_TICKS;

        assertEquals(List.of(), c.codes());
    }

    @Test
    void staleStepCountsFromWhenWorkLastStarted() {
        c.working();
        long stepSince = c.t.clock.tick;
        JobAI ai = c.jobAi();
        AiWatch watch = new AiWatch(c.colony, c.citizen);
        watch.afterTick(CitizenState.IDLE, ai, 0);
        c.t.clock.tick = stepSince + 100;
        watch.afterTick(CitizenState.WORKING, ai, 0); // back to work, same step

        c.t.clock.tick = stepSince + Invariants.JOB_STEP_STALE_TICKS;

        assertEquals(List.of(), c.codes(), "working again for less than the limit");
    }

    @Test
    void jobActionsKeepItsStepFresh() {
        c.working();
        long since = c.t.clock.tick;
        c.t.clock.tick = since + 3000;
        c.citizen.job().orElseThrow().incrementActions(); // a block placed, the step unchanged
        c.tickAi();
        long acted = c.t.clock.tick;

        c.t.clock.tick = since + Invariants.JOB_STEP_STALE_TICKS;
        assertEquals(List.of(), c.codes(), "it acted 3000 ticks ago");
        c.t.clock.tick = acted + Invariants.JOB_STEP_STALE_TICKS;
        assertEquals(List.of(Violation.Code.JOB_STEP_STALE), c.codes());
    }

    @Test
    void jobStepIsNotWatchedOutsideWork() {
        c.working();
        new AiWatch(c.colony, c.citizen).afterTick(CitizenState.IDLE, c.jobAi(), 0);
        c.servesHead = true;

        c.t.clock.tick += Invariants.JOB_STEP_STALE_TICKS;

        assertEquals(List.of(), c.codes(), "idle, its job AI kept but not ticked");
    }

    @Test
    void workerServingAnEmptyQueueIsReported() {
        c.working();
        c.servesHead = true;

        assertEquals(List.of("hycolony.debug.violation.queueEmpty"), c.keys());
    }

    @Test
    void workerServingAGoneRequestIsReported() {
        c.working();
        c.servesHead = true;
        c.queue.add(RequestToken.random());

        assertEquals(List.of("hycolony.debug.violation.queueHeadGone"), c.keys());
    }

    @Test
    void workerServingARequestNotInProgressIsReported() {
        RequestToken token = c.request();
        c.working();
        c.servesHead = true;
        c.queue.add(token);
        assertEquals(
                RequestState.IN_PROGRESS,
                c.colony.requests().get(token).orElseThrow().state());
        assertEquals(List.of(), c.codes());

        c.colony.requests().updateState(token, RequestState.RESOLVED);

        assertEquals(
                RequestState.COMPLETED,
                c.colony.requests().get(token).orElseThrow().state(),
                "resolved, still queued");
        assertEquals(List.of("hycolony.debug.violation.queueHeadState"), c.keys());
    }
}
