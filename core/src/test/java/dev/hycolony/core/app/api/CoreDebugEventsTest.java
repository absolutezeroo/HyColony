package dev.hycolony.core.app.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import dev.hycolony.api.CitizenRef;
import dev.hycolony.api.ColonyRef;
import dev.hycolony.api.Pos;
import dev.hycolony.api.Vec;
import dev.hycolony.api.debug.CitizenStateChanged;
import dev.hycolony.api.debug.JobStateChanged;
import dev.hycolony.api.debug.RequestStateChanged;
import dev.hycolony.api.debug.StuckAction;
import dev.hycolony.api.debug.WalkEnded;
import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.CitizenState;
import dev.hycolony.core.citizen.vitals.AiWatch;
import dev.hycolony.core.citizen.vitals.CitizenWalkReports;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.job.JobAI;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.nav.StuckHandler;
import dev.hycolony.core.kernel.nav.WalkEnd;
import dev.hycolony.core.kernel.port.NavStatus;
import dev.hycolony.core.request.model.RequestToken;
import dev.hycolony.core.request.model.StackRequest;
import dev.hycolony.core.testing.TestContexts;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The api's experimental debug events: the core's, in references and names (spec 2026-09-30, § 4.2). */
class CoreDebugEventsTest {
    private static final BlockPos HALL = new BlockPos(0, 64, 0);
    private static final BlockPos HUT = new BlockPos(8, 64, 0);

    private final TestContexts t = new TestContexts();
    private final ColonyManager manager = t.manager();
    private final CoreColonyWorld world = new CoreColonyWorld(manager, () -> true);
    private final CitizenData citizen = new CitizenData(1);
    private final Colony colony;
    private final CitizenRef ref;
    private final List<Object> heard = new ArrayList<>();

    CoreDebugEventsTest() {
        UUID alice = UUID.randomUUID();
        manager.foundation().begin(alice, "Alice", HALL, 0);
        colony = manager.foundation().confirm(alice, "Rivendell").orElseThrow();
        colony.citizens().restore(citizen);
        ref = new CitizenRef(new ColonyRef("world", colony.id()), 1);
    }

    private static JobAI doing(String step) {
        return new JobAI() {
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
    }

    @Test
    void aiStateChangeIsHeardByItsNames() {
        world.subscribe(CitizenStateChanged.class, heard::add);
        AiWatch watch = new AiWatch(colony, citizen);

        watch.afterTick(CitizenState.IDLE, null, 0);
        watch.afterTick(CitizenState.WORKING, null, 0);

        assertEquals(List.of(new CitizenStateChanged(ref, "IDLE", "WORKING")), heard);
    }

    @Test
    void jobStepChangeIsHeardByItsNames() {
        world.subscribe(JobStateChanged.class, heard::add);

        new AiWatch(colony, citizen).afterTick(CitizenState.WORKING, doing("DELIVERY"), 0);

        assertEquals(List.of(new JobStateChanged(ref, "", "DELIVERY")), heard);
    }

    @Test
    void walkEndIsHeardInReferences() {
        world.subscribe(WalkEnded.class, heard::add);

        new CitizenWalkReports(colony, citizen)
                .walkEnded(HUT, new Vec3(8.5, 69, 0.5), WalkEnd.NAV_ENDED, 5.0, NavStatus.ARRIVED);

        assertEquals(
                List.of(new WalkEnded(ref, new Pos(8, 64, 0), new Vec(8.5, 69, 0.5), "NAV_ENDED", 5.0, "ARRIVED")),
                heard);
    }

    @Test
    void stuckActionIsHeardInReferences() {
        world.subscribe(StuckAction.class, heard::add);

        new CitizenWalkReports(colony, citizen).stuck(HUT, new Vec3(3.5, 64, 0.5), StuckHandler.Action.TELEPORT);

        assertEquals(List.of(new StuckAction(ref, new Pos(8, 64, 0), new Vec(3.5, 64, 0.5), "TELEPORT")), heard);
    }

    @Test
    void requestStateChangeIsHeardWithItsId() {
        List<RequestStateChanged> changes = new ArrayList<>();
        world.subscribe(RequestStateChanged.class, changes::add);

        RequestToken token = colony.requests()
                .createAndAssign(
                        colony.buildings().at(HALL).orElseThrow(),
                        new StackRequest(new ItemKey("Wood_Planks"), 1, 1, true),
                        1);

        assertFalse(changes.isEmpty());
        RequestStateChanged first = changes.getFirst();
        assertEquals(new ColonyRef("world", colony.id()), first.colony());
        assertEquals(token.id().toString(), first.request());
        assertEquals("CREATED", first.from());
        assertEquals(
                colony.requests().get(token).orElseThrow().state().name(),
                changes.getLast().to());
    }
}
