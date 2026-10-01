package dev.hycolony.core.app.hut;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.app.ui.BuildingView;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.job.HiringMode;
import dev.hycolony.core.job.WorkerModule;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.logistics.courier.DeliverymanHut;
import dev.hycolony.core.logistics.warehouse.CourierAssignmentModule;
import dev.hycolony.core.logistics.warehouse.CourierAssignmentView;
import dev.hycolony.core.logistics.warehouse.WarehouseBuilding;
import dev.hycolony.core.testing.TestContexts;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** MC SpecialAssignmentModuleWindow and WindowHireWorker on the warehouse's courier module. */
class CourierHireTest {
    private static final BlockPos STORE = new BlockPos(30, 64, 0);
    private static final BlockPos OTHER_STORE = new BlockPos(0, 64, 30);
    private final TestContexts t = new TestContexts();
    private final UUID alice = UUID.randomUUID();
    private final ColonyManager manager = t.manager();
    private final Colony colony;
    private final Building store;

    CourierHireTest() {
        manager.foundation().begin(alice, "Alice", new BlockPos(0, 64, 0), 0);
        colony = manager.foundation().confirm(alice, "A").orElseThrow();
        manager.huts().place(colony, WarehouseBuilding.TYPE_ID, STORE, 0, alice);
        store = colony.buildings().at(STORE).orElseThrow();
        store.setLevel(1);
        store.setBuilt(true);
        couriers().setHiringMode(HiringMode.MANUAL); // no auto attachment behind the test
    }

    private CourierAssignmentModule couriers() {
        return store.module(CourierAssignmentModule.class).orElseThrow();
    }

    /** A courier hired by its own hut at {@code hutPos}. */
    private CitizenData courier(int id, String name, BlockPos hutPos) {
        manager.huts().place(colony, DeliverymanHut.TYPE.id(), hutPos, 0, alice);
        Building hut = colony.buildings().at(hutPos).orElseThrow();
        hut.setLevel(1);
        hut.setBuilt(true);
        CitizenData d = new CitizenData(id);
        d.setName(name);
        colony.citizens().restore(d);
        assertTrue(hut.module(WorkerModule.class).orElseThrow().hire(colony, hut, d));
        return d;
    }

    private HireView view() {
        manager.windows().openBuilding(alice, STORE);
        return ((BuildingView) t.ui.shown.get(alice)).hire().orElseThrow();
    }

    @Test
    void theWarehouseHireWindowListsCouriersFreeOrAttachedHere() {
        courier(1, "Cora", new BlockPos(-20, 64, 0));
        CitizenData elsewhere = courier(2, "Dan", new BlockPos(-20, 64, 20));
        manager.huts().place(colony, WarehouseBuilding.TYPE_ID, OTHER_STORE, 0, alice);
        Building other = colony.buildings().at(OTHER_STORE).orElseThrow();
        other.setLevel(1);
        other.module(CourierAssignmentModule.class).orElseThrow().attach(elsewhere.id());
        CitizenData idle = new CitizenData(3);
        colony.citizens().restore(idle);
        CitizenData here = courier(4, "Zed", new BlockPos(-20, 64, -20));
        couriers().attach(here.id());

        HireView v = view();

        assertEquals("hycolony:deliveryman", v.jobId());
        assertEquals(Optional.empty(), v.primary());
        List<HireView.Candidate> listed = v.listed(false);
        assertEquals(
                List.of(1, 4),
                listed.stream().map(HireView.Candidate::citizenId).toList(),
                "same priority (MC compares the courier's own hut, not the warehouse), then by name");
        assertEquals(HireView.Button.HIRE, v.button(listed.getFirst(), false));
        assertEquals(HireView.Button.FIRE, v.button(listed.get(1), false), "an attached courier can be detached");
    }

    @Test
    void theWarehouseRefusesChildrenNonCouriersAndCouriersOfAnotherWarehouse() {
        CitizenData elsewhere = courier(1, "Dan", new BlockPos(-20, 64, 20));
        manager.huts().place(colony, WarehouseBuilding.TYPE_ID, OTHER_STORE, 0, alice);
        Building other = colony.buildings().at(OTHER_STORE).orElseThrow();
        other.setLevel(1);
        other.module(CourierAssignmentModule.class).orElseThrow().attach(elsewhere.id());
        CitizenData idle = new CitizenData(2);
        colony.citizens().restore(idle);
        CitizenData kid = courier(3, "Kid", new BlockPos(-20, 64, -20));
        kid.setChild(true);

        assertFalse(manager.huts().hire(alice, STORE, elsewhere.id()), "one warehouse per courier");
        assertFalse(manager.huts().hire(alice, STORE, idle.id()), "couriers only");
        assertFalse(manager.huts().hire(alice, STORE, kid.id()), "adults only");
        assertTrue(couriers().couriers().isEmpty());
    }

    @Test
    void attachingAndDetachingMarkTheColonyToSave() {
        CitizenData cora = courier(1, "Cora", new BlockPos(-20, 64, 0));
        colony.clearDirty();
        assertTrue(manager.huts().hire(alice, STORE, cora.id()));
        assertTrue(colony.isDirty());
        colony.clearDirty();
        assertTrue(manager.huts().fire(alice, STORE, cora.id()));
        assertTrue(colony.isDirty());
    }

    @Test
    void aCourierIsAttachedOnce() {
        assertTrue(couriers().attach(1));
        assertFalse(couriers().attach(1));
        assertEquals(List.of(1), couriers().couriers());
    }

    @Test
    void showEmployedIsOffForTheWarehouseAsMc() {
        courier(1, "Cora", new BlockPos(-20, 64, 0));
        CitizenData idle = new CitizenData(2);
        colony.citizens().restore(idle);

        HireView v = view();

        assertFalse(v.showEmployedEnabled(), "MC setupShowEmployed: disabled for a non-worker module");
        assertEquals(
                List.of(1),
                v.listed(true).stream().map(HireView.Candidate::citizenId).toList());
        assertEquals(
                HireView.Button.NONE,
                v.button(
                        v.all().stream()
                                .filter(c -> c.citizenId() == 2)
                                .findFirst()
                                .orElseThrow(),
                        true));
    }

    @Test
    void hiringAttachesAndFiringDetachesUpToLevelTimesTwo() {
        CitizenData cora = courier(1, "Cora", new BlockPos(-20, 64, 0));
        CitizenData dan = courier(2, "Dan", new BlockPos(-20, 64, 20));
        CitizenData eve = courier(3, "Eve", new BlockPos(-20, 64, -20));

        assertTrue(manager.huts().hire(alice, STORE, cora.id()));
        assertTrue(manager.huts().hire(alice, STORE, dan.id()));
        assertFalse(manager.huts().hire(alice, STORE, eve.id()), "level 1 x 2");
        assertEquals(List.of(1, 2), couriers().couriers());
        assertTrue(view().full());

        assertTrue(manager.huts().fire(alice, STORE, cora.id()));
        assertEquals(List.of(2), couriers().couriers());
        assertEquals(new BlockPos(-20, 64, 0), cora.workBuilding(), "detaching keeps the courier's own job");
    }

    @Test
    void theCouriersTabListsThemByIdAsMcHashSet() {
        CitizenData dan = courier(2, "Dan", new BlockPos(-20, 64, 20));
        CitizenData cora = courier(1, "Cora", new BlockPos(-20, 64, 0));
        couriers().attach(dan.id());
        couriers().attach(cora.id());

        manager.windows().openBuilding(alice, STORE);
        BuildingView v = (BuildingView) t.ui.shown.get(alice);

        assertEquals(
                List.of("Cora", "Dan"),
                v.tab(CourierAssignmentView.class).orElseThrow().couriers());
    }

    @Test
    void courierButtonsNeedManageHuts() {
        CitizenData cora = courier(1, "Cora", new BlockPos(-20, 64, 0));
        assertFalse(manager.huts().hire(UUID.randomUUID(), STORE, cora.id()));
        assertTrue(couriers().couriers().isEmpty());
        assertFalse(manager.hutWindows().cycleHiring(UUID.randomUUID(), STORE));
    }

    @Test
    void theCourierModeCyclesWithoutLocked() {
        assertTrue(manager.hutWindows().cycleHiring(alice, STORE));
        assertEquals(HiringMode.DEFAULT, couriers().hiringMode(), "MANUAL then DEFAULT: LOCKED skipped");
    }

    @Test
    void recallSaysOnceForEachCourierThatCannotAppearAsMc() {
        couriers().attach(courier(1, "Cora", new BlockPos(-20, 64, 0)).id());
        couriers().attach(courier(2, "Dan", new BlockPos(-20, 64, 20)).id());
        t.bodies.refuseSpawn = true;
        int before = t.notifier.sent.size();
        manager.hutWindows().recallWorkers(alice, STORE);
        assertEquals(
                2,
                t.notifier.sent.subList(before, t.notifier.sent.size()).stream()
                        .filter(s -> s.msg().key().equals("hycolony.hut.recallFail"))
                        .count());
    }

    @Test
    void recallBringsTheAttachedCouriersToTheWarehouse() {
        CitizenData cora = courier(1, "Cora", new BlockPos(-20, 64, 0));
        couriers().attach(cora.id());
        colony.citizens().respawnBody(1);
        assertTrue(manager.hutWindows().recallWorkers(alice, STORE));
        assertEquals(List.of(Vec3.center(STORE)), t.bodies.teleports);
    }
}
