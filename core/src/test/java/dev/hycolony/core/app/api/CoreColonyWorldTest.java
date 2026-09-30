package dev.hycolony.core.app.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.api.CitizenRef;
import dev.hycolony.api.ColonyRef;
import dev.hycolony.api.Pos;
import dev.hycolony.api.Vec;
import dev.hycolony.api.read.BuildingSnapshot;
import dev.hycolony.api.read.CitizenSnapshot;
import dev.hycolony.api.read.ColonySummary;
import dev.hycolony.api.read.RequestSnapshot;
import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.RequestManager;
import dev.hycolony.core.request.model.Pickup;
import dev.hycolony.core.request.model.RequestToken;
import dev.hycolony.core.request.model.StackRequest;
import dev.hycolony.core.testing.TestContexts;
import dev.hycolony.core.testing.TestJobs;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/** The api's reads: snapshots of what the core holds, taken on the world's thread. */
class CoreColonyWorldTest {
    private static final BlockPos HALL = new BlockPos(0, 64, 0);
    private static final ItemKey PLANKS = new ItemKey("test:planks");

    private final UUID alice = UUID.randomUUID();
    private final TestContexts t = new TestContexts();
    private final ColonyManager manager = t.manager();
    private boolean onWorldThread = true;
    private final CoreColonyWorld world = new CoreColonyWorld(manager, () -> onWorldThread);
    private final Colony colony;
    private final ColonyRef ref;
    private final Building hall;

    CoreColonyWorldTest() {
        manager.foundation().begin(alice, "Alice", HALL, 0);
        colony = manager.foundation().confirm(alice, "Rivendell").orElseThrow();
        for (int i = 0; i < 20; i++) {
            colony.citizens().onColonyTick(); // the initial citizens, with their bodies
        }
        ref = new ColonyRef("world", colony.id());
        hall = colony.buildings().townHall().orElseThrow();
    }

    private CitizenData firstCitizen() {
        return colony.citizens().all().iterator().next();
    }

    private RequestSnapshot snapshotOf(RequestToken token) {
        return world.requests(ref).stream()
                .filter(r -> r.id().equals(token.id().toString()))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void colonyIsSummedUp() {
        assertEquals(List.of(new ColonySummary(ref, "Rivendell", new Pos(0, 64, 0), alice, 4)), world.colonies());
        assertEquals(Optional.of(world.colonies().getFirst()), world.colony(ref));
        assertEquals(Optional.empty(), world.colony(new ColonyRef("world", 99)));
    }

    @Test
    void colonyOfAnotherWorldIsNotHere() {
        ColonyRef elsewhere = new ColonyRef("nether", colony.id());

        assertEquals(Optional.empty(), world.colony(elsewhere));
        assertEquals(List.of(), world.citizens(elsewhere));
    }

    @Test
    void citizenCarriesItsJobHutsAndBodyPosition() {
        CitizenData d = firstCitizen();
        d.setJob(TestJobs.TYPE.factory().apply(d));
        d.setHomeBuilding(new BlockPos(5, 64, 1));
        d.setWorkBuilding(new BlockPos(-3, 64, 8));
        t.bodies.bodies.get(colony.citizens().bodyOf(d.id()).orElseThrow()).position = new Vec3(1.5, 64, 7.25);
        CitizenRef citizen = new CitizenRef(ref, d.id());

        assertEquals(
                Optional.of(new CitizenSnapshot(
                        citizen,
                        d.name(),
                        Optional.of("test:worker"),
                        Optional.of(new Pos(5, 64, 1)),
                        Optional.of(new Pos(-3, 64, 8)),
                        Optional.of(new Vec(1.5, 64, 7.25)))),
                world.citizen(citizen));
        assertEquals(4, world.citizens(ref).size());
        assertEquals(Optional.empty(), world.citizen(new CitizenRef(ref, 99)));
        assertEquals(List.of(), world.citizens(new ColonyRef("world", 99)));
    }

    @Test
    void townHallIsABuilding() {
        assertEquals(
                List.of(new BuildingSnapshot(
                        ref, hall.type().id(), new Pos(0, 64, 0), hall.level(), hall.isBuilt(), hall.style())),
                world.buildings(ref));
    }

    @Test
    void pickupIsAskedByItsHut() {
        RequestToken token = colony.requests().createAndAssign(hall, new Pickup(5, 0, 10), Request.NO_CITIZEN);

        RequestSnapshot r = snapshotOf(token);

        assertEquals("pickup", r.kind());
        assertEquals(Optional.empty(), r.item());
        assertEquals(0, r.count());
        assertEquals(Optional.of(new Pos(0, 64, 0)), r.building());
        assertEquals(Optional.empty(), r.citizen());
    }

    @Test
    void citizenStackRequestCarriesItsItemCountAndCitizen() {
        CitizenData d = firstCitizen();
        RequestManager m = colony.requests();
        RequestToken token = m.createAndAssign(hall, new StackRequest(PLANKS, 5, 2, false), d.id());

        RequestSnapshot r = snapshotOf(token);

        assertEquals("stack", r.kind());
        assertEquals(Optional.of("test:planks"), r.item());
        assertEquals(5, r.count(), "the count asked, not the minimum");
        assertEquals(Optional.of(new CitizenRef(ref, d.id())), r.citizen());
        assertEquals(m.get(token).orElseThrow().state().name(), r.state());
        assertEquals(m.resolverOf(token).map(res -> res.resolverId()), r.resolver());
        assertTrue(r.resolver().isPresent());
    }

    @Test
    void childAndParentNameEachOther() {
        RequestManager m = colony.requests();
        RequestToken parent = m.createAndAssign(hall, new StackRequest(PLANKS, 5, 5, false), Request.NO_CITIZEN);
        RequestToken child =
                m.createChild(m.resolverOf(parent).orElseThrow(), parent, new StackRequest(PLANKS, 3, 3, false));

        assertEquals(List.of(child.id().toString()), snapshotOf(parent).children());
        assertEquals(Optional.of(parent.id().toString()), snapshotOf(child).parent());
        assertEquals(Optional.empty(), snapshotOf(parent).parent());
    }

    @Test
    void snapshotsDoNotFollowTheColony() {
        List<BuildingSnapshot> before = world.buildings(ref);

        colony.buildings().add(Building.create(hall.type(), HALL.offset(9, 0, 9), 0));

        assertEquals(1, before.size());
        assertThrows(UnsupportedOperationException.class, () -> before.add(before.getFirst()));
        assertEquals(2, world.buildings(ref).size());
    }

    @Test
    void everyReadRefusesAnotherThread() {
        CitizenRef citizen = new CitizenRef(ref, firstCitizen().id());
        onWorldThread = false;

        List<Executable> reads = List.of(
                world::colonies,
                () -> world.colony(ref),
                () -> world.citizens(ref),
                () -> world.citizen(citizen),
                () -> world.buildings(ref),
                () -> world.requests(ref),
                world::debug);
        reads.forEach(read -> assertThrows(IllegalStateException.class, read));
    }
}
