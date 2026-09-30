package dev.hycolony.core.citizen.vitals;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import dev.hycolony.core.citizen.CitizenAI;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.CitizenState;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.permission.Permissions;
import dev.hycolony.core.colony.territory.TerritoryIndex;
import dev.hycolony.core.job.Job;
import dev.hycolony.core.job.JobAI;
import dev.hycolony.core.job.JobType;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.nav.StuckHandler;
import dev.hycolony.core.kernel.nav.WalkEnd;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.kernel.port.NavStatus;
import dev.hycolony.core.testing.TestContexts;
import dev.hycolony.core.testing.TestJobs;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** A citizen's vital signs: kept in place for the invariants, events only for who listens (spec 2026-09-30, § 5). */
class CitizenVitalsTest {
    private static final BlockPos HUT = new BlockPos(8, 64, 0);
    private static final Vec3 ROOF = new Vec3(8.5, 69, 0.5);

    private final TestContexts t = new TestContexts();
    private final Colony colony = new Colony(
            t.context(),
            new TerritoryIndex(),
            new Colony.Founding(1, "T", new BlockPos(0, 64, 0), Permissions.createDefault(UUID.randomUUID(), "A")));
    private final CitizenData citizen = new CitizenData(1);
    private final CitizenWalkReports walks = new CitizenWalkReports(colony, citizen);
    /** What the test job's AI says it is doing. */
    private String step = "START_WORKING";
    /** Whether the test job's AI throws when it ticks. */
    private boolean throwing;
    /** The failures of each job AI created, in order: a test sets the last one's. */
    private final List<int[]> failures = new ArrayList<>();

    CitizenVitalsTest() {
        colony.citizens().restore(citizen);
    }

    /** A new test job whose AIs name their step from {@link #step} and count their own failures. */
    private Job newJob() {
        return new JobType("test:steps", c -> new Job(TestJobs.TYPE, c) {
                    @Override
                    public JobAI createAI(Colony colony, BodyId body) {
                        int[] failed = {0};
                        failures.add(failed);
                        return new JobAI() {
                            @Override
                            public void tick() {
                                if (throwing) {
                                    throw new IllegalStateException("boom");
                                }
                            }

                            @Override
                            public String stateName() {
                                return step;
                            }

                            @Override
                            public boolean canBeInterrupted() {
                                return true;
                            }

                            @Override
                            public int failures() {
                                return failed[0];
                            }
                        };
                    }
                })
                .factory()
                .apply(citizen);
    }

    private CitizenAI workingAi() {
        citizen.setJob(newJob());
        CitizenAI ai = new CitizenAI(colony, citizen, t.bodies.existing(1, 1, new Vec3(0, 64, 0)));
        for (int i = 0; i < 30 && ai.state() != CitizenState.WORKING; i++) {
            t.clock.tick++;
            ai.tick();
        }
        return ai;
    }

    @Test
    void walkEndIsKeptWithoutAnyListener() {
        t.clock.tick = 100;
        walks.walkStarted(HUT, new Vec3(0, 64, 0));
        t.clock.tick = 160;

        walks.walkEnded(HUT, ROOF, WalkEnd.NAV_ENDED, 5.0, NavStatus.ARRIVED);

        CitizenVitals v = citizen.vitals();
        assertEquals(Optional.of(HUT), v.walkTarget());
        assertEquals(
                Optional.of(new EndedWalk(HUT, WalkEnd.NAV_ENDED, ROOF, 5.0, NavStatus.ARRIVED, 160)),
                v.lastWalkEnd(),
                "the nav said it arrived, on the roof");
        assertFalse(t.bus.hasListeners(CitizenDebugEvents.WalkEnded.class), "diagnostics never listen to the bus");
    }

    @Test
    void walkEndIsPostedToWhoListens() {
        List<CitizenDebugEvents.WalkEnded> heard = t.heard(CitizenDebugEvents.WalkEnded.class);

        walks.walkEnded(HUT, ROOF, WalkEnd.NAV_ENDED, 5.0, NavStatus.ARRIVED);

        assertEquals(
                List.of(new CitizenDebugEvents.WalkEnded(
                        colony, citizen, HUT, ROOF, WalkEnd.NAV_ENDED, 5.0, NavStatus.ARRIVED)),
                heard);
    }

    @Test
    void stuckActionIsKeptAndPosted() {
        List<CitizenDebugEvents.StuckActed> heard = t.heard(CitizenDebugEvents.StuckActed.class);
        t.clock.tick = 42;

        walks.stuck(HUT, ROOF, StuckHandler.Action.TELEPORT);

        assertEquals(Optional.of(StuckHandler.Action.TELEPORT), citizen.vitals().lastStuck());
        assertEquals(42, citizen.vitals().lastStuckTick());
        assertEquals(1, heard.size());
    }

    @Test
    void jobStepChangeIsTimedAndPosted() {
        CitizenAI ai = workingAi();
        List<CitizenDebugEvents.JobStepChanged> heard = t.heard(CitizenDebugEvents.JobStepChanged.class);
        step = "DELIVERY";
        t.clock.tick = 500;

        ai.tick();
        t.clock.tick = 600;
        ai.tick(); // the same step: its time and events stay

        assertEquals(Optional.of("DELIVERY"), citizen.vitals().jobStep());
        assertEquals(500, citizen.vitals().jobStepSince());
        assertEquals(
                List.of(new CitizenDebugEvents.JobStepChanged(colony, citizen, "START_WORKING", "DELIVERY")), heard);
    }

    @Test
    void aiStateChangeIsPostedToWhoListens() {
        List<CitizenDebugEvents.AiStateChanged> heard = t.heard(CitizenDebugEvents.AiStateChanged.class);

        workingAi();

        assertEquals(Optional.of(CitizenState.WORKING), citizen.vitals().aiState());
        assertEquals(
                List.of(new CitizenDebugEvents.AiStateChanged(
                        colony, citizen, CitizenState.IDLE, CitizenState.WORKING)),
                heard);
    }

    @Test
    void jobFailuresAreCountedOnceAndAddUpOverANewJobAi() {
        CitizenAI ai = workingAi();

        failures.getLast()[0] = 3;
        ai.tick();
        ai.tick();
        assertEquals(3, citizen.vitals().jobFailures(), "counted once, not at each tick");

        citizen.setJob(newJob()); // hired again: a new job AI, its failures from 0
        ai.tick();
        failures.getLast()[0] = 1;
        ai.tick();

        assertEquals(4, citizen.vitals().jobFailures());
    }

    @Test
    void aiExceptionIsCounted() {
        CitizenAI ai = workingAi();

        throwing = true;
        ai.tick();

        assertEquals(1, citizen.vitals().aiFailures());
    }

    @Test
    void droppedJobAiLeavesNoStep() {
        CitizenAI ai = workingAi();
        List<CitizenDebugEvents.JobStepChanged> heard = t.heard(CitizenDebugEvents.JobStepChanged.class);

        citizen.setJob(null);
        ai.tick();

        assertEquals(Optional.empty(), citizen.vitals().jobStep());
        assertEquals(List.of(new CitizenDebugEvents.JobStepChanged(colony, citizen, "START_WORKING", "")), heard);
    }

    @Test
    void newJobAiEndsTheOldStepThenStartsItsOwn() {
        CitizenAI ai = workingAi();
        List<CitizenDebugEvents.JobStepChanged> heard = t.heard(CitizenDebugEvents.JobStepChanged.class);

        citizen.setJob(newJob());
        ai.tick();

        assertEquals(
                List.of(
                        new CitizenDebugEvents.JobStepChanged(colony, citizen, "START_WORKING", ""),
                        new CitizenDebugEvents.JobStepChanged(colony, citizen, "", "START_WORKING")),
                heard);
    }

    @Test
    void newJobAiTimesItsStepAnewEvenUnderTheSameName() {
        CitizenAI ai = workingAi();
        t.clock.tick = 1_000;

        citizen.setJob(newJob());
        ai.tick();

        assertEquals(Optional.of("START_WORKING"), citizen.vitals().jobStep());
        assertEquals(1_000, citizen.vitals().jobStepSince(), "a new job AI starts its step now");
    }

    @Test
    void workingIsTimedFromWhenItStarted() {
        t.clock.tick = 300;

        CitizenAI ai = workingAi();
        long since = t.clock.tick;
        t.clock.tick += 50;
        ai.tick();

        assertEquals(Optional.of(CitizenState.WORKING), citizen.vitals().aiState());
        assertEquals(since, citizen.vitals().aiStateSince());
    }

    @Test
    void endedWalkKeepsItsTargetWhenTheNextOneStarts() {
        BlockPos next = new BlockPos(20, 64, 0);
        walks.walkStarted(HUT, new Vec3(0, 64, 0));
        walks.walkEnded(HUT, ROOF, WalkEnd.NAV_ENDED, 5.0, NavStatus.ARRIVED);

        walks.walkStarted(next, ROOF);

        assertEquals(Optional.of(HUT), citizen.vitals().lastWalkEnd().map(EndedWalk::target));
        assertEquals(Optional.of(next), citizen.vitals().walkTarget());
    }
}
