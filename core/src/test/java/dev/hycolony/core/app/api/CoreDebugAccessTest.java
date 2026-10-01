package dev.hycolony.core.app.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.hycolony.api.ApiText;
import dev.hycolony.api.CitizenRef;
import dev.hycolony.api.ColonyRef;
import dev.hycolony.api.Pos;
import dev.hycolony.api.Subscription;
import dev.hycolony.api.Vec;
import dev.hycolony.api.debug.CitizenDebugSnapshot;
import dev.hycolony.api.debug.DebugAccess;
import dev.hycolony.api.debug.HistoryEntry;
import dev.hycolony.api.debug.Violation;
import dev.hycolony.api.debug.WalkEnded;
import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.CitizenState;
import dev.hycolony.core.citizen.vitals.AiWatch;
import dev.hycolony.core.citizen.vitals.CitizenWalkReports;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.job.Job;
import dev.hycolony.core.job.JobAI;
import dev.hycolony.core.job.JobType;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.nav.StuckHandler;
import dev.hycolony.core.kernel.nav.WalkEnd;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.kernel.port.Msg;
import dev.hycolony.core.kernel.port.NavStatus;
import dev.hycolony.core.request.BrokenRequests;
import dev.hycolony.core.request.model.RequestToken;
import dev.hycolony.core.request.model.StackRequest;
import dev.hycolony.core.testing.TestContexts;
import dev.hycolony.core.testing.TestJobs;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** What a debugging tool reads: a citizen's inner state, its history while tracked, the invariants (spec § 4.2). */
class CoreDebugAccessTest {
    private static final BlockPos HALL = new BlockPos(0, 64, 0);
    private static final BlockPos HUT = new BlockPos(8, 64, 0);

    private final TestContexts t = new TestContexts();
    private final ColonyManager manager = t.manager();
    private boolean onWorldThread = true;
    private final CoreColonyWorld world = new CoreColonyWorld(manager, () -> onWorldThread);
    private final DebugAccess debug = world.debug();
    private final CitizenData citizen = new CitizenData(1);
    private final Colony colony;
    private final ColonyRef colonyRef;
    private final CitizenRef ref;
    private final CitizenWalkReports walks;
    private final BodyId body;

    CoreDebugAccessTest() {
        UUID alice = UUID.randomUUID();
        manager.foundation().begin(alice, "Alice", HALL, 0);
        colony = manager.foundation().confirm(alice, "Rivendell").orElseThrow();
        colony.citizens().restore(citizen);
        body = t.bodies.existing(colony.id(), 1, new Vec3(0.5, 64, 0.5));
        colony.citizens().onBodyLoaded(body, 1);
        colonyRef = new ColonyRef("world", colony.id());
        ref = new CitizenRef(colonyRef, 1);
        walks = new CitizenWalkReports(colony, citizen);
    }

    /** Hires the citizen to a job whose AI queues {@code queue} and says it fetches planks, and works. */
    private void workingOn(List<RequestToken> queue) {
        citizen.setJob(new JobType("test:debug", c -> new Job(TestJobs.TYPE, c) {
                    @Override
                    public JobAI createAI(Colony colony, BodyId body) {
                        return new JobAI() {
                            @Override
                            public void tick() {}

                            @Override
                            public String stateName() {
                                return "PICKUP";
                            }

                            @Override
                            public boolean canBeInterrupted() {
                                return true;
                            }

                            @Override
                            public List<RequestToken> queue() {
                                return queue;
                            }

                            @Override
                            public Optional<Msg> describe() {
                                return Optional.of(Msg.of("hycolony.test.fetching", "%hycolony.test.planks", "3"));
                            }
                        };
                    }
                })
                .factory()
                .apply(citizen));
        for (int i = 0; i < 30 && colony.citizens().aiState(1).orElse(null) != CitizenState.WORKING; i++) {
            t.clock.tick++;
            colony.citizens().tickAi();
        }
    }

    private RequestToken request() {
        return colony.requests()
                .createAndAssign(
                        colony.buildings().at(HALL).orElseThrow(),
                        new StackRequest(new ItemKey("Wood_Planks"), 1, 1, true),
                        1);
    }

    @Test
    void inspectReadsWhatItsAiAndWalksDidLast() {
        t.clock.tick = 50;
        new AiWatch(colony, citizen).afterTick(CitizenState.WORKING, null, 0);
        t.clock.tick = 60;
        walks.walkStarted(HUT, new Vec3(0.5, 64, 0.5));
        t.clock.tick = 65;
        walks.stuck(HUT, new Vec3(3.5, 64, 0.5), StuckHandler.Action.TELEPORT);
        t.clock.tick = 70;
        walks.walkEnded(HUT, new Vec3(8.5, 64, 0.5), WalkEnd.TELEPORTED, 0.0, NavStatus.MOVING);
        citizen.setLeisureTime(40);

        assertEquals(
                Optional.of(new CitizenDebugSnapshot(
                        ref,
                        70,
                        "WORKING",
                        50,
                        "",
                        0,
                        Optional.empty(),
                        Optional.of(new Pos(8, 64, 0)),
                        List.of(),
                        Optional.of(new WalkEnded(
                                ref, new Pos(8, 64, 0), new Vec(8.5, 64, 0.5), "TELEPORTED", 0.0, "MOVING")),
                        "TELEPORT",
                        65,
                        List.of(),
                        40,
                        List.of())),
                debug.inspect(ref));
    }

    @Test
    void inspectShowsWhatItsJobSaysAndItsQueue() {
        RequestToken token = request();

        workingOn(List.of(token));

        CitizenDebugSnapshot s = debug.inspect(ref).orElseThrow();
        assertEquals("PICKUP", s.jobStep());
        assertEquals(List.of(token.id().toString()), s.queue());
        assertEquals(
                Optional.of(ApiText.of("hycolony.test.fetching", ApiText.of("hycolony.test.planks"), "3")),
                s.activity(),
                "a parameter naming a key is a text of its own");
    }

    @Test
    void inspectShowsThePathItsBodyPlansToWalk() {
        t.bodies.bodies.get(body).path = List.of(new Vec3(2.5, 64, 0.5), new Vec3(5.5, 65, 0.5));

        assertEquals(
                List.of(new Vec(2.5, 64, 0.5), new Vec(5.5, 65, 0.5)),
                debug.inspect(ref).orElseThrow().path());
    }

    @Test
    void citizenWithoutItsBodyHasNoPath() {
        t.bodies.bodies.get(body).path = List.of(new Vec3(2.5, 64, 0.5));

        colony.citizens().onBodyUnloaded(body);

        assertEquals(List.of(), debug.inspect(ref).orElseThrow().path());
    }

    @Test
    void citizenWithoutItsBodyShowsNoAiState() {
        workingOn(List.of());
        assertEquals("WORKING", debug.inspect(ref).orElseThrow().aiState());

        colony.citizens().onBodyUnloaded(body);

        CitizenDebugSnapshot s = debug.inspect(ref).orElseThrow();
        assertEquals(List.of("", ""), List.of(s.aiState(), s.jobStep()), "no AI runs without a body");
    }

    @Test
    void inspectOfAnUnknownCitizenIsEmpty() {
        assertEquals(Optional.empty(), debug.inspect(new CitizenRef(colonyRef, 9)));
        assertEquals(Optional.empty(), debug.inspect(new CitizenRef(new ColonyRef("elsewhere", colony.id()), 1)));
    }

    @Test
    void historyIsKeptWhileTracked() {
        Subscription tracking = debug.track(ref).orElseThrow();
        t.clock.tick = 30;

        new AiWatch(colony, citizen).afterTick(CitizenState.WORKING, null, 0);

        assertEquals(
                List.of(new HistoryEntry(
                        30,
                        "AI_STATE",
                        "IDLE",
                        "WORKING",
                        ApiText.of("hycolony.debug.history.aiState", "IDLE", "WORKING"))),
                debug.history(ref));
        assertEquals(debug.history(ref), debug.inspect(ref).orElseThrow().history());
        tracking.close();
        assertEquals(List.of(), debug.history(ref));
        assertEquals(List.of(), debug.inspect(ref).orElseThrow().history());
    }

    @Test
    void trackingAnUnknownCitizenIsEmpty() {
        assertEquals(Optional.empty(), debug.track(new CitizenRef(colonyRef, 9)));
    }

    @Test
    void checkReportsATraceAtOnce() {
        walks.walkEnded(HUT, new Vec3(8.5, 69, 0.5), WalkEnd.NAV_ENDED, 5.0, NavStatus.ARRIVED);

        assertEquals(
                List.of(new Violation(
                        "WALK_ENDED_AWAY",
                        ApiText.of("hycolony.debug.violation.walkEndedAway", "8 64 0", "5.0", "ARRIVED"),
                        Optional.of(ref),
                        Optional.of(new Pos(8, 69, 0)))),
                debug.check(colonyRef));
    }

    @Test
    void checkReportsAStateOnlyOnceItLasts() {
        RequestToken token = request();
        BrokenRequests.dropResolver(colony.requests(), token);
        t.clock.tick = 1000;
        assertEquals(List.of(), debug.check(colonyRef), "a worker may pass through it");

        t.clock.tick = 1100;

        List<Violation> found = debug.check(colonyRef);
        assertEquals(
                List.of("REQUEST_UNRESOLVED"),
                found.stream().map(Violation::code).toList());
        assertEquals(Optional.of(ref), found.getFirst().citizen());
    }

    @Test
    void checkOfAnotherWorldsColonyIsEmpty() {
        walks.walkEnded(HUT, new Vec3(8.5, 69, 0.5), WalkEnd.NAV_ENDED, 5.0, NavStatus.ARRIVED);

        assertEquals(List.of(), debug.check(new ColonyRef("elsewhere", colony.id())));
    }

    @Test
    void debugCallsOffTheWorldThreadThrow() {
        onWorldThread = false;

        assertThrows(IllegalStateException.class, () -> debug.inspect(ref));
        assertThrows(IllegalStateException.class, () -> debug.history(ref));
        assertThrows(IllegalStateException.class, () -> debug.check(colonyRef));
        assertThrows(IllegalStateException.class, () -> debug.track(ref));
    }
}
