package dev.hycolony.core.colony;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.BuildingType;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.permission.Permissions;
import dev.hycolony.core.colony.ui.BuildingView;
import dev.hycolony.core.construction.blueprint.Blueprint;
import dev.hycolony.core.construction.blueprint.BlueprintEntry;
import dev.hycolony.core.construction.blueprint.BlueprintSource;
import dev.hycolony.core.construction.hut.ConstructionBuildingTypes;
import dev.hycolony.core.construction.shared.BuilderSettingsModule;
import dev.hycolony.core.construction.shared.BuilderSettingsModule.Mode;
import dev.hycolony.core.construction.workorder.WorkOrder;
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

/** The builder hut's Settings and Work orders tabs: MC BuilderSettingsModule and WorkOrderModuleWindow. */
class BuilderHutTabsTest {
    private final TestContexts t = new TestContexts();
    private final ColonyManager manager;
    private final UUID alice = UUID.randomUUID();
    private final UUID carol = UUID.randomUUID(); // friend: may look, not manage
    private final Colony colony;
    private final Building builder;
    private final CitizenData bob;

    BuilderHutTabsTest() {
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
        manager = new ColonyManager(t.context());
        manager.foundation().begin(alice, "Alice", new BlockPos(0, 64, 0), 0);
        colony = manager.foundation().confirm(alice, "A").orElseThrow();
        assertTrue(manager.administration().setRank(alice, colony.id(), carol, "Carol", Permissions.FRIEND));
        bob = new CitizenData(1);
        bob.setName("Bob");
        colony.citizens().restore(bob);
        builder = hut(ConstructionBuildingTypes.BUILDER, new BlockPos(10, 64, 0), 1);
        assertTrue(builder.module(WorkerModule.class).orElseThrow().hire(colony, builder, bob));
        t.notifier.sent.clear();
        t.ui.shown.clear();
    }

    private Building hut(BuildingType type, BlockPos pos, int level) {
        manager.huts().place(colony, type.id(), pos, 0);
        Building b = colony.buildings().at(pos).orElseThrow();
        b.setLevel(level);
        return b;
    }

    private Mode mode() {
        return builder.module(BuilderSettingsModule.class).orElseThrow().mode();
    }

    private WorkOrder order(BlockPos residencePos, int level, WorkOrderType type) {
        Building res = hut(ConstructionBuildingTypes.RESIDENCE, residencePos, level);
        assertEquals(Optional.empty(), manager.workOrders().order(alice, res.position(), type, ""));
        return colony.work().byBuilding(res.position()).orElseThrow();
    }

    @Test
    void settingTheBuilderModeNeedsManageHutsAndReShowsTheHut() {
        assertFalse(manager.huts().setBuilderMode(carol, builder.position(), Mode.MANUAL));
        assertEquals(Mode.AUTO, mode());

        assertTrue(manager.huts().setBuilderMode(alice, builder.position(), Mode.MANUAL));

        assertEquals(Mode.MANUAL, mode());
        assertTrue(t.ui.shown.get(alice) instanceof BuildingView);
        Building residence = hut(ConstructionBuildingTypes.RESIDENCE, new BlockPos(20, 64, 0), 0);
        assertFalse(
                manager.huts().setBuilderMode(alice, residence.position(), Mode.AUTO), "only a builder hut has a mode");
    }

    @Test
    void manualSelectionClaimsAnUnclaimedOrderTheBuilderCanBuild() {
        WorkOrder order = order(new BlockPos(20, 64, 0), 0, WorkOrderType.BUILD);

        assertFalse(manager.workOrders().select(carol, builder.position(), order.id()), "MANAGE_HUTS");
        assertTrue(order.claimedBy().isEmpty());

        assertTrue(manager.workOrders().select(alice, builder.position(), order.id()));

        assertEquals(Optional.of(builder.position()), order.claimedBy());
        assertTrue(t.ui.shown.get(alice) instanceof BuildingView, "the hut window is shown again");
        assertFalse(manager.workOrders().select(alice, builder.position(), order.id()), "already claimed");
        assertEquals(
                "hycolony.workorder.select.alreadyClaimed",
                t.notifier.sent.getLast().msg().key());
    }

    @Test
    void manualSelectionRefusesAnOrderAboveTheBuildersLevel() {
        builder.setLevel(2); // the order needs a builder of its level to exist
        WorkOrder upgrade = order(new BlockPos(20, 64, 0), 1, WorkOrderType.UPGRADE);
        builder.setLevel(1); // target level 2 > builder 1

        assertFalse(manager.workOrders().select(alice, builder.position(), upgrade.id()));

        assertTrue(upgrade.claimedBy().isEmpty());
        assertEquals(
                "hycolony.workorder.select.cannotBuild",
                t.notifier.sent.getLast().msg().key());
    }

    @Test
    void manualSelectionNeedsAWorker() {
        WorkOrder order = order(new BlockPos(20, 64, 0), 0, WorkOrderType.BUILD);
        builder.module(WorkerModule.class).orElseThrow().fire(colony, builder, bob.id());

        assertFalse(manager.workOrders().select(alice, builder.position(), order.id()));

        assertTrue(order.claimedBy().isEmpty());
        assertEquals(
                "hycolony.workorder.select.noWorker",
                t.notifier.sent.getLast().msg().key());
    }

    @Test
    void cancellingFromTheBuilderHutRemovesTheOrderAndReShowsTheHut() {
        WorkOrder order = order(new BlockPos(20, 64, 0), 0, WorkOrderType.BUILD);
        colony.work().tick();
        assertEquals(Optional.of(builder.position()), order.claimedBy());

        assertFalse(manager.workOrders().cancelFromBuilder(carol, builder.position(), order.id()));
        assertTrue(manager.workOrders().cancelFromBuilder(alice, builder.position(), order.id()));

        assertTrue(colony.work().byId(order.id()).isEmpty());
        assertTrue(t.ui.shown.get(alice) instanceof BuildingView);
    }
}
