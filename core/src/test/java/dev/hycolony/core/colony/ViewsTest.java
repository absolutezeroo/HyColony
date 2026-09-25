package dev.hycolony.core.colony;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.BuildingType;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.ui.BuilderResourcesView;
import dev.hycolony.core.colony.ui.BuilderResourcesView.ResourceRow;
import dev.hycolony.core.colony.ui.BuilderResourcesView.Status;
import dev.hycolony.core.colony.ui.BuildingView;
import dev.hycolony.core.colony.ui.RequestsView;
import dev.hycolony.core.colony.ui.WorkOrdersView;
import dev.hycolony.core.construction.Blueprint;
import dev.hycolony.core.construction.BlueprintEntry;
import dev.hycolony.core.construction.BlueprintSource;
import dev.hycolony.core.construction.BuildingResourcesModule;
import dev.hycolony.core.construction.ConstructionBuildingTypes;
import dev.hycolony.core.construction.NeededResources;
import dev.hycolony.core.construction.StructurePlan;
import dev.hycolony.core.construction.WorkManager;
import dev.hycolony.core.construction.WorkOrder;
import dev.hycolony.core.construction.WorkOrderRefusal;
import dev.hycolony.core.construction.WorkOrderType;
import dev.hycolony.core.job.HiringMode;
import dev.hycolony.core.job.WorkerModule;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.BlockKey;
import dev.hycolony.core.kernel.item.BlockState;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.request.RequestToken;
import dev.hycolony.core.request.StackRequest;
import dev.hycolony.core.request.resolver.RetryingResolver;
import dev.hycolony.core.testing.TestContexts;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ViewsTest {
    private static final BlockKey STONE = new BlockKey("stone");
    private static final BlockKey PLANK = new BlockKey("plank");
    private static final ItemKey STONE_I = new ItemKey("stone_item");
    private static final ItemKey PLANK_I = new ItemKey("plank_item");

    private final TestContexts t = new TestContexts();
    private final ColonyManager manager;
    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID(); // neutral: never added
    private final UUID carol = UUID.randomUUID(); // friend: may look, not manage
    private final UUID dave = UUID.randomUUID(); // hostile
    private final BlockPos hall = new BlockPos(0, 64, 0);
    private final Colony colony;
    private final Building builder;
    private final CitizenData bobTheBuilder;
    private int nextCitizen = 2;

    ViewsTest() {
        t.blueprints = new BlueprintSource() {
            @Override
            public Optional<Blueprint> load(String style, String buildingTypeId, int level, int rotation) {
                BlockPos o = new BlockPos(1, 0, 0);
                return Optional.of(new Blueprint("bp", List.of(new BlueprintEntry(o, new BlockState(STONE, 0), false)), o, o));
            }

            @Override
            public List<String> styles() { return List.of("medieval", "desert"); }
        };
        manager = new ColonyManager(t.context());
        manager.beginFoundation(alice, "Alice", hall, 0);
        colony = manager.confirmFoundation(alice, "A").orElseThrow();
        assertTrue(manager.setRank(alice, colony.id(), carol, "Carol", Permissions.FRIEND));
        assertTrue(manager.setRank(alice, colony.id(), dave, "Dave", Permissions.HOSTILE));
        bobTheBuilder = citizen(1, "Bob");
        builder = hut(ConstructionBuildingTypes.BUILDER, new BlockPos(10, 64, 0), 5);
        assertTrue(builder.module(WorkerModule.class).orElseThrow().hire(colony, builder, bobTheBuilder));
        t.notifier.sent.clear();
        t.ui.shown.clear();
    }

    private CitizenData citizen(int id, String name) {
        CitizenData c = new CitizenData(id);
        c.setName(name);
        colony.citizens().restore(c);
        return c;
    }

    private Building hut(BuildingType type, BlockPos pos, int level) {
        manager.placeHut(colony, type.id(), pos, 0);
        Building b = colony.buildings().at(pos).orElseThrow();
        b.setLevel(level);
        return b;
    }

    private Building residence(int level) {
        return hut(ConstructionBuildingTypes.RESIDENCE, new BlockPos(20, 64, 0), level);
    }

    private BuildingView view(UUID player, Building b) {
        manager.openBuilding(player, b.position());
        return (BuildingView) t.ui.shown.get(player);
    }

    @Test
    void allowedActionsByLevelAndOrder() {
        Building res = residence(0);
        assertEquals(EnumSet.of(WorkOrderType.BUILD), view(alice, res).allowed());
        res.setLevel(2);
        assertEquals(EnumSet.of(WorkOrderType.UPGRADE, WorkOrderType.REPAIR, WorkOrderType.REMOVE), view(alice, res).allowed());
        res.setLevel(5);
        BuildingView max = view(alice, res);
        assertEquals(EnumSet.of(WorkOrderType.REPAIR, WorkOrderType.REMOVE), max.allowed());
        assertEquals(5, max.maxLevel());
        res.setLevel(2);
        res.setDeconstructed(true);
        BuildingView decon = view(alice, res);
        assertEquals(EnumSet.of(WorkOrderType.UPGRADE, WorkOrderType.REPAIR), decon.allowed());
        assertTrue(decon.canPickUp());
        res.setLevel(0);
        assertEquals(EnumSet.of(WorkOrderType.REPAIR), view(alice, res).allowed(), "deconstructed at level 0: Build = REPAIR");
        res.setLevel(2);
        res.setDeconstructed(false);
        for (WorkOrderType type : WorkOrderType.values()) { // the server's predicate, not a copy of it
            assertEquals(WorkManager.isAllowed(res, type), view(alice, res).allowed().contains(type), type.name());
        }

        assertEquals(Optional.empty(), manager.orderWork(alice, res.position(), WorkOrderType.UPGRADE, "desert"));
        BuildingView ordered = (BuildingView) t.ui.shown.get(alice); // re-shown by the action
        assertEquals(Set.of(), ordered.allowed(), "an order exists: the button becomes Cancel");
        BuildingView.OrderRow row = ordered.order().orElseThrow();
        assertEquals(WorkOrderType.UPGRADE, row.type());
        assertEquals(3, row.targetLevel());
        assertEquals(Optional.empty(), row.builderName());
        assertEquals(0, row.percent());
        assertEquals(List.of("medieval", "desert"), ordered.styles());
        assertTrue(ordered.canManage());
        assertFalse(ordered.canPickUp());

        colony.work().tick();
        assertEquals(Optional.of("Bob"), view(alice, res).order().orElseThrow().builderName());

        assertTrue(manager.cancelWork(alice, res.position()));
        BuildingView cancelled = (BuildingView) t.ui.shown.get(alice);
        assertTrue(cancelled.order().isEmpty());
        assertEquals(EnumSet.of(WorkOrderType.UPGRADE, WorkOrderType.REPAIR, WorkOrderType.REMOVE), cancelled.allowed());
        assertFalse(manager.cancelWork(alice, res.position()), "nothing left to cancel");
    }

    @Test
    void resourceStatusColours() {
        assertEquals(Status.DONT_HAVE, Status.of(10, 4, 0));
        assertEquals(Status.NEED_MORE, Status.of(10, 4, 5));
        assertEquals(Status.HAVE_ENOUGH, Status.of(10, 4, 6));
        assertEquals(Status.NOT_NEEDED, Status.of(10, 10, 0), "MC: nothing missing is NOT_NEEDED (black)");
        assertEquals(Status.NOT_NEEDED, Status.of(0, 0, 3));

        t.catalog.itemForBlock.put(STONE, STONE_I);
        t.catalog.itemForBlock.put(PLANK, PLANK_I);
        Building res = residence(0);
        assertEquals(Optional.empty(), manager.orderWork(alice, res.position(), WorkOrderType.BUILD, ""));
        colony.work().tick();
        WorkOrder order = colony.work().claimedBy(builder.position()).orElseThrow();
        List<BlueprintEntry> entries = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            entries.add(new BlueprintEntry(new BlockPos(i + 1, 0, 0), new BlockState(i < 4 ? STONE : PLANK, 0), false));
        }
        Blueprint bp = new Blueprint("bp", entries, new BlockPos(0, 0, 0), new BlockPos(6, 0, 0));
        StructurePlan plan = StructurePlan.build(bp, res.position(), t.catalog);
        BuildingResourcesModule module = builder.module(BuildingResourcesModule.class).orElseThrow();
        module.start(order, NeededResources.compute(plan, t.blocks, t.catalog));
        module.onPlaced(STONE_I);
        bobTheBuilder.inventory().insert(new ItemAmount(STONE_I, 1), k -> 64);
        t.containers.insert(builder.containers(), new ItemAmount(STONE_I, 1));
        t.playerInventory.give(alice, new ItemAmount(PLANK_I, 5));

        manager.openBuilderResources(alice, builder.position());

        BuilderResourcesView v = (BuilderResourcesView) t.ui.shown.get(alice);
        assertEquals(builder.position(), v.hut());
        assertEquals(List.of(new ResourceRow(STONE_I, 3, 2, 0, Status.DONT_HAVE),
                new ResourceRow(PLANK_I, 2, 0, 5, Status.HAVE_ENOUGH)), v.rows());
        assertEquals(17, v.percent(), "1 of 6 placed: 100 - (int) (5 / 6 * 100)");
        assertEquals("clear", v.stage());
        assertEquals(17, view(alice, res).order().orElseThrow().percent(), "same progress on the building's order");
    }

    @Test
    void requestsViewListsPlayerAndRetryingRoots() {
        Building hallHut = colony.buildings().at(hall).orElseThrow();
        ItemKey planks = new ItemKey("Wood_Planks");
        RequestToken retried = colony.requests().createAndAssign(hallHut, new StackRequest(planks, 10, 10, true), 1);
        RequestToken atPlayer = colony.requests().createAndAssign(hallHut, new StackRequest(planks, 3, 3, true), -1);
        colony.requests().reassign(atPlayer, Set.of(RetryingResolver.ID));
        t.containers.insert(hallHut.containers(), new ItemAmount(planks, 2));
        colony.requests().createAndAssign(hallHut, new StackRequest(planks, 2, 2, true), -1); // the building has it
        Building res = residence(1); // at (20, 64, 0)
        RequestToken far = colony.requests().createAndAssign(res, new StackRequest(planks, 1, 1, true), -1);
        t.playerInventory.give(alice, new ItemAmount(planks, 7));
        t.players.online.put(alice, new BlockPos(20, 64, 0));

        manager.openRequests(alice, colony.id());

        RequestsView v = (RequestsView) t.ui.shown.get(alice);
        assertEquals(colony.id(), v.colonyId());
        // WindowClipBoard order: requester's distance to the player, then token.
        List<RequestsView.RequestRow> hallRows = new ArrayList<>(List.of(
                new RequestsView.RequestRow(retried, "10 x Wood_Planks", "Bob", 7),
                new RequestsView.RequestRow(atPlayer, "3 x Wood_Planks", hallHut.displayName(), 7)));
        hallRows.sort(java.util.Comparator.comparing(r -> r.token().id()));
        List<RequestsView.RequestRow> expected = new ArrayList<>();
        expected.add(new RequestsView.RequestRow(far, "1 x Wood_Planks", res.displayName(), 7));
        expected.addAll(hallRows);
        assertEquals(expected, v.rows());
    }

    @Test
    void actionsCheckPermissions() {
        Building res = residence(2);
        CitizenData idle = citizen(nextCitizen++, "Idle");
        assertEquals(Optional.empty(), manager.orderWork(alice, res.position(), WorkOrderType.REPAIR, ""));
        int orderId = colony.work().byBuilding(res.position()).orElseThrow().id();
        t.ui.shown.clear();

        for (UUID player : List.of(bob, carol, dave)) {
            assertEquals(Optional.of(WorkOrderRefusal.NO_PERMISSION),
                    manager.orderWork(player, builder.position(), WorkOrderType.UPGRADE, ""));
            assertFalse(manager.cancelWork(player, res.position()));
            assertFalse(manager.hire(player, builder.position(), idle.id()));
            assertFalse(manager.fire(player, builder.position(), bobTheBuilder.id()));
            assertFalse(manager.setHiring(player, builder.position(), HiringMode.LOCKED));
            assertFalse(manager.moveWorkOrder(player, colony.id(), orderId, 1));
            assertFalse(manager.deleteWorkOrder(player, colony.id(), orderId));
            res.setDeconstructed(true);
            assertFalse(manager.pickUpBuilding(player, res.position(), () -> fail("no item for " + player)));
            res.setDeconstructed(false);
        }
        assertTrue(colony.work().byId(orderId).isPresent());
        assertEquals(List.of(bobTheBuilder.id()), builder.module(WorkerModule.class).orElseThrow().workers());
        assertEquals(HiringMode.DEFAULT, builder.module(WorkerModule.class).orElseThrow().hiringMode());
        assertTrue(colony.buildings().at(res.position()).isPresent());

        for (UUID player : List.of(bob, dave)) {
            manager.openBuilding(player, res.position());
            manager.openBuilderResources(player, builder.position());
            manager.openRequests(player, colony.id());
            manager.openWorkOrders(player, colony.id());
            assertFalse(t.ui.shown.containsKey(player), "neutral and hostile see nothing");
            assertTrue(t.notifier.sent.stream().anyMatch(s -> s.player().equals(player)
                    && s.msg().key().equals("hycolony.permission.denied")));
        }

        BuildingView friendView = view(carol, res);
        assertFalse(friendView.canManage());
        assertFalse(friendView.canPickUp());
        manager.openWorkOrders(carol, colony.id());
        WorkOrdersView orders = (WorkOrdersView) t.ui.shown.get(carol);
        assertFalse(orders.canManage());
        assertEquals(List.of(new WorkOrdersView.OrderLine(orderId, WorkOrderType.REPAIR, res.displayName(), 2, 0,
                Optional.empty())), orders.orders());
    }

    @Test
    void orderWorkReturnsRefusal() {
        Building res = residence(0);
        assertEquals(Optional.of(WorkOrderRefusal.INVALID_TYPE),
                manager.orderWork(alice, res.position(), WorkOrderType.REMOVE, ""));
        assertEquals("hycolony.workorder.refused.invalid_type", t.notifier.sent.getLast().msg().key());
        res.setLevel(5);
        assertEquals(Optional.of(WorkOrderRefusal.MAX_LEVEL), manager.orderWork(alice, res.position(), WorkOrderType.UPGRADE, ""));
        res.setLevel(0);

        assertEquals(Optional.empty(), manager.orderWork(alice, res.position(), WorkOrderType.BUILD, "desert"));
        assertEquals("desert", colony.work().byBuilding(res.position()).orElseThrow().style());
        assertEquals(Optional.of(WorkOrderRefusal.ALREADY_EXISTS),
                manager.orderWork(alice, res.position(), WorkOrderType.BUILD, ""));
    }

    @Test
    void hireFireFromView() {
        Building hut = hut(ConstructionBuildingTypes.BUILDER, new BlockPos(30, 64, 0), 1);
        CitizenData ann = citizen(nextCitizen++, "Ann");
        CitizenData ben = citizen(nextCitizen++, "Ben");
        citizen(nextCitizen++, "Kid").setChild(true);

        BuildingView v = view(alice, hut);
        assertEquals(List.of(), v.workers());
        assertEquals(List.of(new BuildingView.WorkerRow(ann.id(), "Ann"), new BuildingView.WorkerRow(ben.id(), "Ben")),
                v.hireable());
        assertEquals(Optional.of(HiringMode.DEFAULT), v.hiringMode());

        assertTrue(manager.hire(alice, hut.position(), ann.id()));
        v = (BuildingView) t.ui.shown.get(alice);
        assertEquals(List.of(new BuildingView.WorkerRow(ann.id(), "Ann")), v.workers());
        assertEquals(List.of(new BuildingView.WorkerRow(ben.id(), "Ben")), v.hireable());
        assertFalse(manager.hire(alice, hut.position(), ben.id()), "a builder hut employs one worker");
        assertFalse(manager.hire(alice, hut.position(), bobTheBuilder.id()), "already employed elsewhere");

        assertTrue(manager.fire(alice, hut.position(), ann.id()));
        assertTrue(((BuildingView) t.ui.shown.get(alice)).workers().isEmpty());
        assertTrue(ann.job().isEmpty());
        assertFalse(manager.fire(alice, hut.position(), ann.id()), "not a worker any more");

        assertTrue(manager.setHiring(alice, hut.position(), HiringMode.MANUAL));
        assertEquals(Optional.of(HiringMode.MANUAL), ((BuildingView) t.ui.shown.get(alice)).hiringMode());
        assertEquals(Optional.empty(), view(alice, colony.buildings().at(hall).orElseThrow()).hiringMode(),
                "the town hall employs no one");
        assertFalse(manager.hire(alice, hall, ann.id()), "the town hall employs no one");
    }

    @Test
    void pickUpDeconstructedBuilding() {
        Building res = residence(2);
        assertFalse(view(alice, res).canPickUp());
        assertFalse(manager.pickUpBuilding(alice, res.position(), () -> fail("checks run first")),
                "still standing: deconstruct it first");

        res.setDeconstructed(true);
        assertEquals(Optional.empty(), manager.orderWork(alice, res.position(), WorkOrderType.REPAIR, ""));
        int orderId = colony.work().byBuilding(res.position()).orElseThrow().id();
        assertFalse(manager.pickUpBuilding(alice, res.position(), () -> false), "inventory full: refused");
        assertTrue(colony.buildings().at(res.position()).isPresent(), "the building is kept");
        assertTrue(colony.work().byId(orderId).isPresent());
        assertEquals("hycolony.hut.pickupInventoryFull", t.notifier.sent.getLast().msg().key());
        int[] given = {0};
        assertTrue(manager.pickUpBuilding(alice, res.position(), () -> ++given[0] > 0));
        assertEquals(1, given[0]);
        assertTrue(colony.buildings().at(res.position()).isEmpty());
        assertTrue(colony.work().byId(orderId).isEmpty(), "removed through the normal path: its order is cancelled");

        Building townHall = colony.buildings().at(hall).orElseThrow();
        townHall.setDeconstructed(true);
        assertFalse(view(alice, townHall).canPickUp());
        assertFalse(manager.pickUpBuilding(alice, hall, () -> fail("never given")), "the town hall is never picked up");
        assertTrue(colony.buildings().at(hall).isPresent());
    }
}
