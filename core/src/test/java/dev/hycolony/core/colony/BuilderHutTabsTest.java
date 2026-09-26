package dev.hycolony.core.colony;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.BuildingType;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.permission.Permissions;
import dev.hycolony.core.colony.persistence.ColonySerializer;
import dev.hycolony.core.colony.territory.ClaimCell;
import dev.hycolony.core.colony.territory.TerritoryIndex;
import dev.hycolony.core.colony.ui.BuilderResourcesView;
import dev.hycolony.core.colony.ui.BuilderResourcesView.ResourceRow;
import dev.hycolony.core.colony.ui.BuilderResourcesView.Status;
import dev.hycolony.core.colony.ui.BuilderTabs;
import dev.hycolony.core.colony.ui.BuildingView;
import dev.hycolony.core.construction.blueprint.Blueprint;
import dev.hycolony.core.construction.blueprint.BlueprintEntry;
import dev.hycolony.core.construction.blueprint.BlueprintSource;
import dev.hycolony.core.construction.blueprint.StructurePlan;
import dev.hycolony.core.construction.hut.ConstructionBuildingTypes;
import dev.hycolony.core.construction.resources.BuildingResourcesModule;
import dev.hycolony.core.construction.resources.NeededResources;
import dev.hycolony.core.construction.shared.BuilderSettingsModule;
import dev.hycolony.core.construction.shared.BuilderSettingsModule.Mode;
import dev.hycolony.core.construction.workorder.ManualSelection;
import dev.hycolony.core.construction.workorder.Stage;
import dev.hycolony.core.construction.workorder.WorkOrder;
import dev.hycolony.core.construction.workorder.WorkOrderType;
import dev.hycolony.core.job.WorkerModule;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.BlockKey;
import dev.hycolony.core.kernel.item.BlockState;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.testing.TestContexts;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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

    @Test
    void selectingAnOrderWithALowerIdQueuesItBehindTheOrderUnderWay() {
        assertTrue(manager.huts().setBuilderMode(alice, builder.position(), Mode.MANUAL));
        WorkOrder older = order(new BlockPos(20, 64, 0), 0, WorkOrderType.BUILD);
        WorkOrder current = order(new BlockPos(30, 64, 0), 0, WorkOrderType.BUILD);
        assertTrue(manager.workOrders().select(alice, builder.position(), current.id()));
        assertEquals(Optional.of(current), colony.work().claimedBy(builder.position()));

        assertTrue(manager.workOrders().select(alice, builder.position(), older.id()));

        assertEquals(Optional.of(builder.position()), older.claimedBy(), "queued: MC setWorkOrder with hasWorkOrder");
        assertEquals(Optional.of(current), colony.work().claimedBy(builder.position()), "the current one stays");
        TerritoryIndex territory = new TerritoryIndex();
        territory.claimSquare(colony.id(), ClaimCell.of(colony.center()), t.config.initialColonySize());
        Colony loaded = ColonySerializer.read(ColonySerializer.write(colony), t.context(), territory);
        assertEquals(
                current.id(),
                loaded.work().claimedBy(builder.position()).orElseThrow().id(),
                "still current after a restart");

        colony.work().cancel(current.id());
        assertEquals(Optional.of(older), colony.work().claimedBy(builder.position()), "the queued one comes next");
    }

    @Test
    void selectingAnUnknownOrderOrFromAnotherHutIsNotForTheBuilder() {
        WorkOrder order = order(new BlockPos(20, 64, 0), 0, WorkOrderType.BUILD);
        Building residence = hut(ConstructionBuildingTypes.RESIDENCE, new BlockPos(40, 64, 0), 1);

        assertFalse(manager.workOrders().select(alice, builder.position(), order.id() + 100));
        assertEquals(
                "hycolony.workorder.select.notForBuilder",
                t.notifier.sent.getLast().msg().key());
        assertFalse(manager.workOrders().select(alice, residence.position(), order.id()));
        assertEquals(
                "hycolony.workorder.select.notForBuilder",
                t.notifier.sent.getLast().msg().key());
        assertTrue(order.claimedBy().isEmpty());
    }

    @Test
    void cancellingAnUnknownOrderFromTheBuilderHutDoesNothing() {
        WorkOrder order = order(new BlockPos(20, 64, 0), 0, WorkOrderType.BUILD);

        assertFalse(manager.workOrders().cancelFromBuilder(alice, builder.position(), order.id() + 100));

        assertTrue(colony.work().byId(order.id()).isPresent());
    }

    private BuilderTabs tabs(UUID player) {
        manager.windows().openBuilding(player, builder.position());
        return ((BuildingView) t.ui.shown.get(player)).builder().orElseThrow();
    }

    @Test
    void onlyTheBuilderHutHasTheExtraTabs() {
        Building residence = hut(ConstructionBuildingTypes.RESIDENCE, new BlockPos(20, 64, 0), 1);
        manager.windows().openBuilding(alice, residence.position());

        assertTrue(((BuildingView) t.ui.shown.get(alice)).builder().isEmpty());
        assertEquals(Mode.AUTO, tabs(alice).mode());
    }

    /** Blocks a, b, c, d of the plan need items A (x2), B, C, D; the builder already placed nothing. */
    private void startBuildWithFourItems(WorkOrder order) {
        List<BlueprintEntry> entries = new ArrayList<>();
        String[] blocks = {"a", "a", "b", "c", "d"};
        for (int i = 0; i < blocks.length; i++) {
            BlockKey block = new BlockKey(blocks[i]);
            t.catalog.itemForBlock.put(block, new ItemKey(blocks[i].toUpperCase(Locale.ROOT)));
            entries.add(new BlueprintEntry(new BlockPos(i + 1, 0, 0), new BlockState(block, 0), false));
        }
        Blueprint bp = new Blueprint("bp", entries, new BlockPos(0, 0, 0), new BlockPos(5, 0, 0));
        StructurePlan plan = StructurePlan.build(bp, order.buildingPos(), t.catalog);
        builder.module(BuildingResourcesModule.class)
                .orElseThrow()
                .start(order, NeededResources.compute(plan, t.blocks, t.catalog));
    }

    @Test
    void resourcesAreSortedLikeMineColoniesAndCarryTheOrderHeader() {
        WorkOrder order = order(new BlockPos(20, 64, 0), 0, WorkOrderType.BUILD);
        colony.work().tick();
        startBuildWithFourItems(order);
        order.progress(Stage.SOLID, 0);
        bob.inventory().insert(new ItemAmount(new ItemKey("A"), 2), k -> 64); // A: enough, NOT_NEEDED
        t.playerInventory.give(alice, new ItemAmount(new ItemKey("D"), 1)); // D: HAVE_ENOUGH
        t.playerInventory.give(alice, new ItemAmount(new ItemKey("B"), 1)); // B: HAVE_ENOUGH
        // C: DONT_HAVE

        BuilderResourcesView v = tabs(alice).resources();

        assertEquals(
                List.of(
                        new ResourceRow(new ItemKey("B"), 1, 0, 1, Status.HAVE_ENOUGH),
                        new ResourceRow(new ItemKey("D"), 1, 0, 1, Status.HAVE_ENOUGH),
                        new ResourceRow(new ItemKey("C"), 1, 0, 0, Status.DONT_HAVE),
                        new ResourceRow(new ItemKey("A"), 2, 2, 0, Status.NOT_NEEDED)),
                v.rows(),
                "ResourceComparator: HAVE_ENOUGH, NEED_MORE, DONT_HAVE, NOT_NEEDED, then by name");
        assertEquals(-1, v.rows().get(2).missingFromPlayer(), "getMissingFromPlayer: player + available - needed");
        BuilderResourcesView.Header h = v.header().orElseThrow();
        assertEquals(WorkOrderType.BUILD, h.type());
        assertEquals(1, h.targetLevel());
        assertEquals(1, h.step(), "CLEAR finished, SOLID under way");
        assertEquals(4, h.totalSteps(), "CLEAR, SOLID, DECORATE, CLEAR_LEFTOVERS");
        assertEquals(40, h.suppliedPercent(), "2 of 5 items supplied");
        assertEquals(0, h.percent(), "nothing placed yet");
    }

    @Test
    void workOrdersTabListsClaimedOrdersAndInManualModeTheUnclaimedOnes() {
        WorkOrder mine = order(new BlockPos(20, 64, 0), 0, WorkOrderType.BUILD);
        colony.work().tick(); // AUTO: the builder claims it
        startBuildWithFourItems(mine);
        WorkOrder free = order(new BlockPos(10, 64, 30), 0, WorkOrderType.BUILD);
        builder.setLevel(3);
        WorkOrder tooHigh = order(new BlockPos(40, 64, 0), 2, WorkOrderType.UPGRADE); // target 3
        builder.setLevel(1);

        assertEquals(List.of(mine.id()), ids(tabs(alice).orders()), "AUTO: only its own orders");

        assertTrue(manager.huts().setBuilderMode(alice, builder.position(), Mode.MANUAL));
        List<BuilderTabs.OrderLine> lines = tabs(alice).orders();

        assertEquals(
                List.of(mine.id(), free.id()), ids(lines), "MANUAL: the unclaimed ones it can build, current first");
        assertTrue(lines.get(0).current());
        assertTrue(lines.get(0).claimedHere());
        assertEquals(10, lines.get(0).distance(), "BlockPosUtil.getDistance2D: |dx| + |dz|");
        assertFalse(lines.get(1).current());
        assertFalse(lines.get(1).claimedHere());
        assertEquals(30, lines.get(1).distance());
        assertEquals(Optional.empty(), lines.get(1).selectRefusal());
        assertTrue(tooHigh.claimedBy().isEmpty());

        builder.module(WorkerModule.class).orElseThrow().fire(colony, builder, bob.id());
        assertEquals(
                Optional.of(ManualSelection.Refusal.NO_WORKER),
                tabs(alice).orders().getLast().selectRefusal());
    }

    private static List<Integer> ids(List<BuilderTabs.OrderLine> lines) {
        return lines.stream().map(BuilderTabs.OrderLine::id).toList();
    }
}
