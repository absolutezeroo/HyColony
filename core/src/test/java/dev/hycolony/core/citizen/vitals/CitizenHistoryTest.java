package dev.hycolony.core.citizen.vitals;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.CitizenState;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.permission.Permissions;
import dev.hycolony.core.colony.territory.TerritoryIndex;
import dev.hycolony.core.job.JobAI;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.nav.StuckHandler;
import dev.hycolony.core.kernel.nav.WalkEnd;
import dev.hycolony.core.kernel.port.Msg;
import dev.hycolony.core.kernel.port.NavStatus;
import dev.hycolony.core.testing.TestContexts;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** A tracked citizen's history: its last 20 transitions and walks, kept only while tracked (spec 2026-09-30, § 5). */
class CitizenHistoryTest {
    private static final BlockPos HUT = new BlockPos(8, 64, 0);
    private static final Vec3 ROOF = new Vec3(8.5, 69, 0.5);

    private final TestContexts t = new TestContexts();
    private final Colony colony = new Colony(
            t.context(),
            new TerritoryIndex(),
            new Colony.Founding(1, "T", new BlockPos(0, 64, 0), Permissions.createDefault(UUID.randomUUID(), "A")));
    private final CitizenData citizen = new CitizenData(1);
    private final AiWatch watch = new AiWatch(colony, citizen);
    private final CitizenWalkReports walks = new CitizenWalkReports(colony, citizen);
    /** What the test job's AI says it is doing. */
    private String step = "START_WORKING";

    private final JobAI job = new JobAI() {
        @Override
        public void tick() {}

        @Override
        public String stateName() {
            return step;
        }

        @Override
        public boolean canBeInterrupted() {
            return true;
        }
    };

    /** Why it left WORKING is said on that change, so HyLens shows it: here the rain. */
    @Test
    void leavingWorkSaysWhy() {
        citizen.vitals().track();
        watch.afterTick(CitizenState.WORKING, job, 0);
        t.clock.tick = 40;
        watch.leftWork(WorkExit.RAIN);
        watch.afterTick(CitizenState.IDLE, null, 0);
        t.clock.tick = 50;
        watch.afterTick(CitizenState.WORKING, job, 0); // a later change says no reason

        List<HistoryEntry> states = citizen.vitals().history().stream()
                .filter(e -> e.kind() == HistoryEntry.Kind.AI_STATE)
                .toList();
        assertEquals(
                Msg.of("hycolony.debug.history.leftWork.rain", "IDLE"),
                states.get(1).detail());
        assertEquals(
                Msg.of("hycolony.debug.history.aiState", "IDLE", "WORKING"),
                states.get(2).detail());
    }

    @Test
    void untrackedCitizenKeepsNoHistory() {
        watch.afterTick(CitizenState.IDLE, null, 0);
        watch.afterTick(CitizenState.WORKING, job, 0);
        walks.walkEnded(HUT, ROOF, WalkEnd.NAV_ENDED, 5.0, NavStatus.ARRIVED);

        assertFalse(citizen.vitals().keepsHistory(), "an untracked citizen allocates no history");
        assertEquals(List.of(), citizen.vitals().history());
    }

    @Test
    void trackedCitizenNotesItsTransitionsWithTheirTick() {
        citizen.vitals().track();
        t.clock.tick = 10;
        watch.afterTick(CitizenState.IDLE, null, 0);
        t.clock.tick = 20;
        watch.afterTick(CitizenState.WORKING, job, 0);
        t.clock.tick = 30;
        watch.afterTick(CitizenState.WORKING, job, 0); // nothing changed: nothing noted
        step = "DELIVERY";
        watch.afterTick(CitizenState.WORKING, job, 0);

        assertEquals(
                List.of(
                        new HistoryEntry(
                                10,
                                HistoryEntry.Kind.AI_STATE,
                                "",
                                "IDLE",
                                Msg.of("hycolony.debug.history.aiState", "-", "IDLE")),
                        new HistoryEntry(
                                20,
                                HistoryEntry.Kind.AI_STATE,
                                "IDLE",
                                "WORKING",
                                Msg.of("hycolony.debug.history.aiState", "IDLE", "WORKING")),
                        new HistoryEntry(
                                20,
                                HistoryEntry.Kind.JOB_STEP,
                                "",
                                "START_WORKING",
                                Msg.of("hycolony.debug.history.jobStep", "-", "START_WORKING")),
                        new HistoryEntry(
                                30,
                                HistoryEntry.Kind.JOB_STEP,
                                "START_WORKING",
                                "DELIVERY",
                                Msg.of("hycolony.debug.history.jobStep", "START_WORKING", "DELIVERY"))),
                citizen.vitals().history());
    }

    @Test
    void newJobAiEndsTheOldStepInTheHistory() {
        citizen.vitals().track();
        t.clock.tick = 10;
        watch.afterTick(CitizenState.WORKING, job, 0);
        t.clock.tick = 50;

        watch.jobStarted();

        HistoryEntry last = citizen.vitals().history().getLast();
        assertEquals(HistoryEntry.Kind.JOB_STEP, last.kind());
        assertEquals("START_WORKING", last.from());
        assertEquals("", last.to());
        assertEquals(50, last.tick(), "the old step ends when the new job AI starts");
    }

    @Test
    void firstJobAiEndsNoStep() {
        citizen.vitals().track();

        watch.jobStarted();

        assertEquals(List.of(), citizen.vitals().history(), "no step had started");
    }

    @Test
    void trackedCitizenNotesItsWalkEndsAndStuckActions() {
        citizen.vitals().track();
        t.clock.tick = 160;

        walks.stuck(HUT, ROOF, StuckHandler.Action.TELEPORT);
        walks.walkEnded(HUT, ROOF, WalkEnd.TELEPORTED, 5.25, NavStatus.MOVING);

        assertEquals(
                List.of(
                        new HistoryEntry(
                                160,
                                HistoryEntry.Kind.STUCK,
                                "8 64 0",
                                "TELEPORT",
                                Msg.of("hycolony.debug.history.stuck", "8 64 0", "8.5 69.0 0.5", "TELEPORT")),
                        new HistoryEntry(
                                160,
                                HistoryEntry.Kind.WALK_ENDED,
                                "8 64 0",
                                "TELEPORTED",
                                Msg.of(
                                        "hycolony.debug.history.walkEnded",
                                        "8 64 0",
                                        "TELEPORTED",
                                        "8.5 69.0 0.5",
                                        "5.3"))),
                citizen.vitals().history());
    }

    @Test
    void historyKeepsOnlyTheLastTwentyEntries() {
        citizen.vitals().track();
        for (int i = 0; i < 25; i++) {
            step = "STEP_" + i;
            watch.afterTick(CitizenState.WORKING, job, 0);
        }

        List<HistoryEntry> history = citizen.vitals().history();
        assertEquals(20, history.size());
        assertEquals("STEP_5", history.getFirst().to(), "the oldest entries were dropped");
        assertEquals("STEP_24", history.getLast().to());
    }

    @Test
    void closingTheTrackingStopsAndDropsTheHistory() {
        CitizenHistory.Tracking tracking = citizen.vitals().track();
        watch.afterTick(CitizenState.IDLE, null, 0);

        tracking.close();
        tracking.close(); // idempotent
        watch.afterTick(CitizenState.WORKING, job, 0);

        assertEquals(List.of(), citizen.vitals().history());
        assertFalse(citizen.vitals().keepsHistory(), "the history is dropped once no tracking is open");
    }

    @Test
    void trackingClosedFromAnotherThreadStopsTheHistory() throws InterruptedException {
        CitizenHistory.Tracking tracking = citizen.vitals().track();
        watch.afterTick(CitizenState.IDLE, null, 0);

        Thread unloading = new Thread(tracking::close);
        unloading.start();
        unloading.join();

        assertEquals(List.of(), citizen.vitals().history());
    }

    @Test
    void historyLastsWhileTheFirstTrackingIsOpen() {
        CitizenHistory.Tracking first = citizen.vitals().track();
        watch.afterTick(CitizenState.IDLE, null, 0);
        CitizenHistory.Tracking second = citizen.vitals().track();

        assertEquals(1, citizen.vitals().history().size(), "a second tracker sees what was noted before it");
        second.close();
        watch.afterTick(CitizenState.WORKING, job, 0);

        assertEquals(3, citizen.vitals().history().size(), "the first tracker still keeps it");
        first.close();
        assertEquals(List.of(), citizen.vitals().history());
    }

    @Test
    void historyLastsWhileTheSecondTrackingIsOpen() {
        CitizenHistory.Tracking first = citizen.vitals().track();
        CitizenHistory.Tracking second = citizen.vitals().track();
        watch.afterTick(CitizenState.IDLE, null, 0);

        first.close();
        watch.afterTick(CitizenState.WORKING, job, 0);

        assertEquals(3, citizen.vitals().history().size(), "the second tracker still keeps it");
        second.close();
        assertEquals(List.of(), citizen.vitals().history());
    }

    @Test
    void newTrackingAfterAllClosedStartsEmpty() {
        CitizenHistory.Tracking old = citizen.vitals().track();
        watch.afterTick(CitizenState.IDLE, null, 0);
        old.close();

        citizen.vitals().track();

        assertEquals(List.of(), citizen.vitals().history(), "the tracking starts the history");
    }
}
