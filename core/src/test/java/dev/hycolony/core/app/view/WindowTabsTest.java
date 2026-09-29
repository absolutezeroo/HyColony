package dev.hycolony.core.app.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.app.ui.CitizenView;
import dev.hycolony.core.app.ui.TownHallView;
import dev.hycolony.core.app.ui.TownHallView.JobCount;
import dev.hycolony.core.app.ui.TownHallView.Stats;
import dev.hycolony.core.app.ui.WorkOrdersView;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.Skill;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.construction.blueprint.Blueprint;
import dev.hycolony.core.construction.blueprint.BlueprintEntry;
import dev.hycolony.core.construction.blueprint.BlueprintSource;
import dev.hycolony.core.construction.hut.ConstructionBuildingTypes;
import dev.hycolony.core.construction.workorder.WorkOrderType;
import dev.hycolony.core.job.WorkerModule;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.BlockKey;
import dev.hycolony.core.kernel.item.BlockState;
import dev.hycolony.core.testing.TestContexts;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WindowTabsTest {
    private final TestContexts t = new TestContexts();
    private final ColonyManager manager;
    private final UUID alice = UUID.randomUUID();
    private final BlockPos hall = new BlockPos(0, 64, 0);
    private final Colony colony;

    WindowTabsTest() {
        t.blueprints = new BlueprintSource() {
            @Override
            public Optional<Blueprint> load(String style, String buildingTypeId, int level, int rotation) {
                BlockPos o = new BlockPos(1, 0, 0);
                return Optional.of(new Blueprint(
                        "bp", List.of(new BlueprintEntry(o, new BlockState(new BlockKey("stone"), 0), false)), o, o));
            }

            @Override
            public List<String> styles() {
                return List.of("medieval");
            }
        };
        manager = t.manager();
        manager.foundation().begin(alice, "Alice", hall, 0);
        colony = manager.foundation().confirm(alice, "A").orElseThrow();
    }

    private CitizenData citizen(int id, String name) {
        CitizenData c = new CitizenData(id);
        c.setName(name);
        colony.citizens().restore(c);
        return c;
    }

    private Building hut(BlockPos pos, int level) {
        manager.huts().place(colony, ConstructionBuildingTypes.BUILDER.id(), pos, 0);
        Building b = colony.buildings().at(pos).orElseThrow();
        b.setLevel(level);
        return b;
    }

    private TownHallView townHall() {
        manager.windows().openTownHall(alice, hall);
        return (TownHallView) t.ui.shown.get(alice);
    }

    @Test
    void statisticsCountPopulationWorkersPerJobChildrenAndUnemployed() {
        Building builder = hut(new BlockPos(10, 64, 0), 1);
        hut(new BlockPos(20, 64, 0), 1);
        CitizenData bob = citizen(1, "Bob");
        citizen(2, "Idle");
        citizen(3, "Jobless");
        citizen(4, "Kid").setChild(true);
        assertTrue(builder.module(WorkerModule.class).orElseThrow().hire(colony, builder, bob));

        assertEquals(
                new Stats(4, List.of(new JobCount("hycolony:builder", 1, 2)), 1, 2),
                townHall().stats());
    }

    @Test
    void informationTabListsTheWorkOrdersAndMovingOneShowsTheTownHallAgain() {
        Building a = hut(new BlockPos(10, 64, 0), 2);
        Building b = hut(new BlockPos(20, 64, 0), 2);
        assertTrue(a.module(WorkerModule.class).orElseThrow().hire(colony, a, citizen(1, "Bob")));
        assertEquals(Optional.empty(), manager.workOrders().order(alice, a.position(), WorkOrderType.REPAIR, ""));
        assertEquals(Optional.empty(), manager.workOrders().order(alice, b.position(), WorkOrderType.REPAIR, ""));

        WorkOrdersView orders = townHall().workOrders();
        assertTrue(orders.canManage());
        assertEquals(2, orders.orders().size());
        WorkOrdersView.OrderLine last = orders.orders().get(1);
        assertEquals(
                new WorkOrdersView.OrderLine(last.id(), WorkOrderType.REPAIR, b.displayName(), 2, Optional.empty()),
                last);

        t.ui.shown.clear();
        assertTrue(manager.workOrders().move(alice, colony.id(), last.id(), 1));
        TownHallView after = (TownHallView) t.ui.shown.get(alice);
        assertEquals(last.id(), after.workOrders().orders().get(0).id());

        assertTrue(manager.workOrders().delete(alice, colony.id(), last.id()));
        assertEquals(
                1, ((TownHallView) t.ui.shown.get(alice)).workOrders().orders().size());
    }

    @Test
    void citizenWindowCarriesTheJobSkillSharesOnlyForAWorker() {
        Building builder = hut(new BlockPos(10, 64, 0), 1);
        CitizenData bob = citizen(1, "Bob");
        CitizenData idle = citizen(2, "Idle");
        assertTrue(builder.module(WorkerModule.class).orElseThrow().hire(colony, builder, bob));

        manager.windows().openCitizen(alice, colony.id(), bob.id());
        CitizenView worker = (CitizenView) t.ui.shown.get(alice);
        assertEquals(
                Skill.Adaptability,
                worker.jobSkills().orElseThrow().primary().get(0).skill());
        assertEquals(
                Skill.Athletics,
                worker.jobSkills().orElseThrow().secondary().get(0).skill());

        manager.windows().openCitizen(alice, colony.id(), idle.id());
        assertEquals(Optional.empty(), ((CitizenView) t.ui.shown.get(alice)).jobSkills());
    }
}
