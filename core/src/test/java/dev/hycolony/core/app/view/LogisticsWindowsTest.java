package dev.hycolony.core.app.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.app.hut.HutStock;
import dev.hycolony.core.app.ui.BuildingView;
import dev.hycolony.core.app.ui.CitizenView;
import dev.hycolony.core.app.ui.RequestsView;
import dev.hycolony.core.app.ui.RequestsView.RequestRow;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.BuildingType;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.permission.Permissions;
import dev.hycolony.core.construction.hut.ConstructionBuildingTypes;
import dev.hycolony.core.job.WorkerModule;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.logistics.courier.CourierTasksView;
import dev.hycolony.core.logistics.courier.DeliverymanHut;
import dev.hycolony.core.logistics.courier.DeliverymanJob;
import dev.hycolony.core.logistics.warehouse.CourierAssignmentView;
import dev.hycolony.core.logistics.warehouse.TaskRow;
import dev.hycolony.core.logistics.warehouse.WarehouseBuilding;
import dev.hycolony.core.logistics.warehouse.WarehouseTasksView;
import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.model.Delivery;
import dev.hycolony.core.request.model.Pickup;
import dev.hycolony.core.request.model.RequestToken;
import dev.hycolony.core.request.model.StackRequest;
import dev.hycolony.core.testing.TestContexts;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** The logistics windows (MC warehouse modules, courier task list, pickup priority) and courier requests in views. */
class LogisticsWindowsTest {
    private static final ItemKey STONE = new ItemKey("Rock_Stone");
    private static final ItemKey LOG = new ItemKey("Wood_Oak_Trunk");

    private final TestContexts t = new TestContexts();
    private final ColonyManager manager;
    private final UUID alice = UUID.randomUUID();
    private final UUID carol = UUID.randomUUID(); // friend: may look, not manage
    private final Colony colony;
    private final Building builder;
    private final Building warehouse;

    LogisticsWindowsTest() {
        manager = t.manager();
        manager.foundation().begin(alice, "Alice", new BlockPos(0, 64, 0), 0);
        colony = manager.foundation().confirm(alice, "A").orElseThrow();
        assertTrue(manager.administration().setRank(alice, colony.id(), carol, "Carol", Permissions.FRIEND));
        builder = hut(ConstructionBuildingTypes.BUILDER, new BlockPos(40, 64, 0));
        warehouse = hut(WarehouseBuilding.TYPE, new BlockPos(20, 64, 0));
        assertTrue(builder.module(WorkerModule.class).orElseThrow().hire(colony, builder, citizen(1, "Bob")));
        t.notifier.sent.clear();
        t.ui.shown.clear();
    }

    private CitizenData citizen(int id, String name) {
        CitizenData c = new CitizenData(id);
        c.setName(name);
        colony.citizens().restore(c);
        return c;
    }

    private Building hut(BuildingType type, BlockPos pos) {
        manager.huts().place(colony, type.id(), pos, 0, UUID.randomUUID());
        Building b = colony.buildings().at(pos).orElseThrow();
        b.setLevel(1);
        b.setBuilt(true);
        return b;
    }

    /** A courier hired at its hut, attached to the warehouse on the next colony tick. */
    private DeliverymanJob courier() {
        Building hut = hut(DeliverymanHut.TYPE, new BlockPos(-20, 64, 0));
        CitizenData cora = citizen(2, "Cora");
        assertTrue(hut.module(WorkerModule.class).orElseThrow().hire(colony, hut, cora));
        colony.buildings().onColonyTick(colony);
        return (DeliverymanJob) cora.job().orElseThrow();
    }

    private BuildingView view(UUID player, Building b) {
        manager.windows().openBuilding(player, b.position());
        return (BuildingView) t.ui.shown.get(player);
    }

    private RequestsView clipboard() {
        manager.windows().openRequests(alice, colony.id());
        return (RequestsView) t.ui.shown.get(alice);
    }

    @Test
    void onlyWorkerHutsShowThePickupPriority() {
        assertEquals(OptionalInt.of(5), view(alice, builder).pickupPriority());
        assertEquals(OptionalInt.empty(), view(alice, warehouse).pickupPriority(), "MC: worker huts only");
        assertEquals(Optional.empty(), view(alice, builder).tab(WarehouseTasksView.class));
        assertEquals(Optional.empty(), view(alice, builder).tab(CourierTasksView.class));
    }

    @Test
    void pickupPriorityButtonsNeedManageHutsAndStayWithinZeroToTen() {
        assertTrue(manager.hutWindows().pickup().alterPickupPriority(alice, builder.position(), true));
        assertEquals(OptionalInt.of(6), ((BuildingView) t.ui.shown.get(alice)).pickupPriority(), "re-shown");

        assertFalse(manager.hutWindows().pickup().alterPickupPriority(carol, builder.position(), false));
        assertFalse(manager.hutWindows().pickup().alterPickupPriority(alice, warehouse.position(), true), "no worker");
        assertEquals(6, builder.pickupPriority().value());
        assertEquals(5, warehouse.pickupPriority().value());

        for (int i = 0; i < 12; i++) {
            manager.hutWindows().pickup().alterPickupPriority(alice, builder.position(), false);
        }
        assertEquals(0, builder.pickupPriority().value());
        for (int i = 0; i < 12; i++) {
            manager.hutWindows().pickup().alterPickupPriority(alice, builder.position(), true);
        }
        assertEquals(10, builder.pickupPriority().value());
    }

    @Test
    void forcePickupCreatesOneForcedPickupAndTellsThePlayer() {
        assertFalse(manager.hutWindows().pickup().forcePickup(carol, builder.position()));
        assertEquals(List.of(), pickups());
        assertEquals(
                "hycolony.permission.toolDenied",
                t.notifier.sent.getLast().msg().key());

        assertTrue(manager.hutWindows().pickup().forcePickup(alice, builder.position()));
        assertEquals("hycolony.pickup.forced", t.notifier.sent.getLast().msg().key());
        assertEquals(1, pickups().size());
        assertEquals(Pickup.MAX_BUILDING_PRIORITY, ((Pickup) pickups().get(0).requestable()).priority());

        assertFalse(manager.hutWindows().pickup().forcePickup(alice, builder.position()), "one open pickup per hut");
        assertEquals(
                "hycolony.pickup.forceFailed", t.notifier.sent.getLast().msg().key());
        assertEquals(1, pickups().size());
    }

    @Test
    void forcePickupIsRefusedOnAHutWithoutWorkers() {
        assertFalse(manager.hutWindows().pickup().forcePickup(alice, warehouse.position()), "MC: worker huts only");
        assertEquals(List.of(), pickups());
        assertEquals(List.of(), t.notifier.sent);
    }

    private List<Request> pickups() {
        return colony.requests().all().stream()
                .filter(r -> r.requestable() instanceof Pickup)
                .toList();
    }

    @Test
    void warehouseWindowListsAttachedCouriersAndStockMostFirst() {
        courier();
        t.containers.insert(List.of(warehouse.position()), new ItemAmount(LOG, 3));
        t.containers.insert(List.of(warehouse.position()), new ItemAmount(STONE, 40));

        BuildingView v = view(alice, warehouse);
        CourierAssignmentView w = v.tab(CourierAssignmentView.class).orElseThrow();

        assertEquals(List.of("Cora"), w.couriers());
        assertEquals(2, w.maxCouriers(), "level 1 x 2");
        assertEquals(
                Map.of(STONE, 40, LOG, 3),
                v.stock().stream().collect(Collectors.toMap(HutStock::item, HutStock::count)));
        assertEquals(List.of(), v.tab(WarehouseTasksView.class).orElseThrow().queue());
    }

    @Test
    void warehouseQueueShowsTheDeliveryFromTheWarehouseToTheBuilder() {
        courier();
        t.containers.insert(List.of(warehouse.position()), new ItemAmount(STONE, 40));
        colony.requests().createAndAssign(builder, new StackRequest(STONE, 10, 10, true), -1);

        List<TaskRow> queue = view(alice, warehouse)
                .tab(WarehouseTasksView.class)
                .orElseThrow()
                .queue();

        assertEquals(1, queue.size());
        TaskRow row = queue.get(0);
        Delivery d = assertInstanceOf(Delivery.class, row.requestable());
        assertEquals(new ItemAmount(STONE, 10), d.stack());
        assertEquals(warehouse.displayName(), row.requester());
        assertEquals(Optional.of(builder.displayName()), row.forRequester(), "MC: requester -> parent requester");
        assertEquals(Optional.of(warehouse.position()), row.requesterPos(), "MC's tooltip: requester position");
        assertEquals(Optional.of(builder.position()), row.forPos(), "-> the parent's position");
        assertEquals(Delivery.DEFAULT_DELIVERY_PRIORITY, row.priority());
    }

    @Test
    void courierHutListsItsCourierTasksAndWarehouse() {
        DeliverymanJob job = courier();
        Building courierHut = colony.buildings().at(new BlockPos(-20, 64, 0)).orElseThrow();
        assertEquals(
                Optional.of(new CourierTasksView(Optional.of(warehouse.position()), List.of())),
                view(alice, courierHut).tab(CourierTasksView.class));
        assertEquals(OptionalInt.of(5), view(alice, courierHut).pickupPriority());

        RequestToken token = colony.requests()
                .createAndAssign(
                        warehouse,
                        new Delivery(warehouse.position(), builder.requesterId(), new ItemAmount(LOG, 2), 13),
                        -1);
        job.currentTask(colony);

        List<TaskRow> tasks = view(alice, courierHut)
                .tab(CourierTasksView.class)
                .orElseThrow()
                .tasks();
        assertEquals(1, tasks.size());
        assertEquals(token, tasks.get(0).token());
        assertEquals(13, tasks.get(0).priority());
        assertTrue(
                view(alice, warehouse)
                        .tab(WarehouseTasksView.class)
                        .orElseThrow()
                        .queue()
                        .isEmpty(),
                "taken by the courier");
    }

    @Test
    void courierHutWithoutWarehouseSaysSo() {
        Building courierHut = hut(DeliverymanHut.TYPE, new BlockPos(-20, 64, 0));
        assertEquals(
                Optional.of(new CourierTasksView(Optional.empty(), List.of())),
                view(alice, courierHut).tab(CourierTasksView.class));
    }

    @Test
    void clipboardShowsTheDeliveryUnderTheBuildersRequestWhenNoCourierExists() {
        t.containers.insert(List.of(warehouse.position()), new ItemAmount(STONE, 40));
        RequestToken root = colony.requests().createAndAssign(builder, new StackRequest(STONE, 10, 10, true), -1);
        t.playerInventory.give(alice, new ItemAmount(STONE, 4));

        List<RequestRow> rows = clipboard().rows();

        assertEquals(2, rows.size(), "MC RequestTreeWindowModule: the root, then its children one level deeper");
        assertEquals(root, rows.get(0).token());
        assertEquals(0, rows.get(0).depth());
        assertTrue(rows.get(0).canSupply());
        Delivery d = assertInstanceOf(Delivery.class, rows.get(1).requestable());
        assertEquals(new ItemAmount(STONE, 10), d.stack());
        assertEquals(1, rows.get(1).depth());
        assertEquals(warehouse.displayName(), rows.get(1).requesterName());
        assertEquals(0, rows.get(1).playerHas(), "not items the player can hand over");
        assertFalse(rows.get(1).canSupply());
    }

    @Test
    void clipboardShowsAPickupHeldByThePlayer() {
        assertTrue(manager.hutWindows().pickup().forcePickup(alice, builder.position()));

        List<RequestRow> rows = clipboard().rows();

        assertEquals(1, rows.size());
        assertInstanceOf(Pickup.class, rows.get(0).requestable());
        assertEquals(builder.displayName(), rows.get(0).requesterName());
        assertFalse(rows.get(0).canSupply());
    }

    @Test
    void citizenWindowWaitsForACourierRequest() {
        CitizenData bob = colony.citizens().get(1).orElseThrow();
        colony.requests().createAndAssign(builder, new Pickup(5, 0, 64), bob.id());

        manager.windows().openCitizen(alice, colony.id(), bob.id());
        CitizenView v = (CitizenView) t.ui.shown.get(alice);

        assertEquals("waitingFor", v.activity());
        assertInstanceOf(Pickup.class, v.waitingFor().orElseThrow());
        assertEquals(1, v.requests().size());
    }
}
