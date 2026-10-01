package dev.hycolony.core.app.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.app.hut.HireView;
import dev.hycolony.core.app.ui.BuildingView;
import dev.hycolony.core.app.ui.CitizenView;
import dev.hycolony.core.app.ui.RequestsView;
import dev.hycolony.core.app.ui.TownHallView;
import dev.hycolony.core.app.ui.WorkOrdersView;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.BuildingType;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.Skill;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ColonyEvents;
import dev.hycolony.core.colony.permission.Permissions;
import dev.hycolony.core.construction.blueprint.Blueprint;
import dev.hycolony.core.construction.blueprint.BlueprintEntry;
import dev.hycolony.core.construction.blueprint.BlueprintSource;
import dev.hycolony.core.construction.blueprint.StructurePlan;
import dev.hycolony.core.construction.hut.ConstructionBuildingTypes;
import dev.hycolony.core.construction.resources.BuilderResourcesView;
import dev.hycolony.core.construction.resources.BuilderResourcesView.ResourceRow;
import dev.hycolony.core.construction.resources.BuilderResourcesView.Status;
import dev.hycolony.core.construction.resources.BuildingResourcesModule;
import dev.hycolony.core.construction.resources.NeededResources;
import dev.hycolony.core.construction.workorder.WorkManager;
import dev.hycolony.core.construction.workorder.WorkOrder;
import dev.hycolony.core.construction.workorder.WorkOrderRefusal;
import dev.hycolony.core.construction.workorder.WorkOrderType;
import dev.hycolony.core.job.HiringMode;
import dev.hycolony.core.job.WorkerModule;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.item.BlockKey;
import dev.hycolony.core.kernel.item.BlockState;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.kernel.port.NavStatus;
import dev.hycolony.core.logistics.courier.DeliverymanHut;
import dev.hycolony.core.request.model.RequestToken;
import dev.hycolony.core.request.model.StackRequest;
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
                return Optional.of(
                        new Blueprint("bp", List.of(new BlueprintEntry(o, new BlockState(STONE, 0), false)), o, o));
            }

            @Override
            public List<String> styles() {
                return List.of("medieval", "desert");
            }
        };
        manager = t.manager();
        manager.foundation().begin(alice, "Alice", hall, 0);
        colony = manager.foundation().confirm(alice, "A").orElseThrow();
        assertTrue(manager.administration().setRank(alice, colony.id(), carol, "Carol", Permissions.FRIEND));
        assertTrue(manager.administration().setRank(alice, colony.id(), dave, "Dave", Permissions.HOSTILE));
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
        manager.huts().place(colony, type.id(), pos, 0, UUID.randomUUID());
        Building b = colony.buildings().at(pos).orElseThrow();
        b.setLevel(level);
        return b;
    }

    private Building residence(int level) {
        return hut(ConstructionBuildingTypes.RESIDENCE, new BlockPos(20, 64, 0), level);
    }

    private BuildingView view(UUID player, Building b) {
        manager.windows().openBuilding(player, b.position());
        return (BuildingView) t.ui.shown.get(player);
    }

    @Test
    void allowedActionsByLevelAndOrder() {
        Building res = residence(0);
        assertEquals(EnumSet.of(WorkOrderType.BUILD), view(alice, res).allowed());
        res.setLevel(2);
        assertEquals(
                EnumSet.of(WorkOrderType.UPGRADE, WorkOrderType.REPAIR, WorkOrderType.REMOVE),
                view(alice, res).allowed());
        res.setLevel(5);
        BuildingView max = view(alice, res);
        assertEquals(EnumSet.of(WorkOrderType.REPAIR, WorkOrderType.REMOVE), max.allowed());
        assertEquals(5, max.maxLevel());
        res.setLevel(2);
        res.setDeconstructed(true);
        BuildingView decon = view(alice, res);
        assertEquals(EnumSet.of(WorkOrderType.UPGRADE, WorkOrderType.REPAIR), decon.allowed());
        res.setLevel(0);
        assertEquals(
                EnumSet.of(WorkOrderType.REPAIR),
                view(alice, res).allowed(),
                "deconstructed at level 0: Build = REPAIR");
        res.setLevel(2);
        res.setDeconstructed(false);
        for (WorkOrderType type : WorkOrderType.values()) { // the server's predicate, not a copy of it
            assertEquals(
                    WorkManager.isAllowed(res, type), view(alice, res).allowed().contains(type), type.name());
        }

        assertEquals(
                Optional.empty(), manager.workOrders().order(alice, res.position(), WorkOrderType.UPGRADE, "desert"));
        BuildingView ordered = (BuildingView) t.ui.shown.get(alice); // re-shown by the action
        assertEquals(Set.of(), ordered.allowed(), "an order exists: the button becomes Cancel");
        BuildingView.OrderRow row = ordered.order().orElseThrow();
        assertEquals(WorkOrderType.UPGRADE, row.type());
        assertEquals(3, row.targetLevel());
        assertEquals(Optional.empty(), row.builderName());
        assertEquals(0, row.percent());
        assertEquals(List.of("medieval", "desert"), ordered.styles());
        assertTrue(ordered.canManage());

        colony.work().tick();
        assertEquals(Optional.of("Bob"), view(alice, res).order().orElseThrow().builderName());

        assertTrue(manager.workOrders().cancel(alice, res.position()));
        BuildingView cancelled = (BuildingView) t.ui.shown.get(alice);
        assertTrue(cancelled.order().isEmpty());
        assertEquals(
                EnumSet.of(WorkOrderType.UPGRADE, WorkOrderType.REPAIR, WorkOrderType.REMOVE), cancelled.allowed());
        assertFalse(manager.workOrders().cancel(alice, res.position()), "nothing left to cancel");
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
        assertEquals(Optional.empty(), manager.workOrders().order(alice, res.position(), WorkOrderType.BUILD, ""));
        colony.work().tick();
        WorkOrder order = colony.work().claimedBy(builder.position()).orElseThrow();
        List<BlueprintEntry> entries = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            entries.add(new BlueprintEntry(new BlockPos(i + 1, 0, 0), new BlockState(i < 4 ? STONE : PLANK, 0), false));
        }
        Blueprint bp = new Blueprint("bp", entries, new BlockPos(0, 0, 0), new BlockPos(6, 0, 0));
        StructurePlan plan = StructurePlan.build(bp, res.position(), t.catalog);
        BuildingResourcesModule module =
                builder.module(BuildingResourcesModule.class).orElseThrow();
        module.start(order, NeededResources.compute(plan, t.blocks, t.catalog, t.recipes));
        module.onPlaced(STONE_I);
        bobTheBuilder.inventory().insert(new ItemAmount(STONE_I, 1), k -> 64);
        t.containers.insert(builder.containers(), new ItemAmount(STONE_I, 1));
        t.playerInventory.give(alice, new ItemAmount(PLANK_I, 5));

        BuilderResourcesView v =
                view(alice, builder).tab(BuilderResourcesView.class).orElseThrow();
        assertEquals(
                List.of(
                        new ResourceRow(PLANK_I, 2, 0, 5, Status.HAVE_ENOUGH),
                        new ResourceRow(STONE_I, 3, 2, 0, Status.DONT_HAVE)),
                v.rows(),
                "MC ResourceComparator: HAVE_ENOUGH before DONT_HAVE");
        BuilderResourcesView.Header header = v.header().orElseThrow();
        assertEquals(17, header.percent(), "1 of 6 placed: 100 - (int) (5 / 6 * 100)");
        assertEquals(0, header.step(), "CLEAR under way");
        assertEquals(40, header.suppliedPercent(), "2 of the 5 still needed are in the hut or on the builder");
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

        manager.windows().openRequests(alice, colony.id());

        RequestsView v = (RequestsView) t.ui.shown.get(alice);
        assertEquals(colony.id(), v.colonyId());
        // WindowClipBoard order: requester's distance to the player, then token.
        List<RequestsView.RequestRow> hallRows = new ArrayList<>(List.of(
                new RequestsView.RequestRow(retried, new StackRequest(planks, 10, 10, true), "Bob", 7, 0),
                new RequestsView.RequestRow(
                        atPlayer, new StackRequest(planks, 3, 3, true), hallHut.displayName(), 7, 0)));
        hallRows.sort(java.util.Comparator.comparing(r -> r.token().id()));
        List<RequestsView.RequestRow> expected = new ArrayList<>();
        expected.add(new RequestsView.RequestRow(far, new StackRequest(planks, 1, 1, true), res.displayName(), 7, 0));
        expected.addAll(hallRows);
        assertEquals(expected, v.rows());
    }

    @Test
    void actionsCheckPermissions() {
        Building res = residence(2);
        CitizenData idle = citizen(nextCitizen++, "Idle");
        assertEquals(Optional.empty(), manager.workOrders().order(alice, res.position(), WorkOrderType.REPAIR, ""));
        int orderId = colony.work().byBuilding(res.position()).orElseThrow().id();
        t.ui.shown.clear();

        for (UUID player : List.of(bob, carol, dave)) {
            assertEquals(
                    Optional.of(WorkOrderRefusal.NO_PERMISSION),
                    manager.workOrders().order(player, builder.position(), WorkOrderType.UPGRADE, ""));
            assertFalse(manager.workOrders().cancel(player, res.position()));
            assertFalse(manager.huts().hire(player, builder.position(), idle.id()));
            assertFalse(manager.huts().fire(player, builder.position(), bobTheBuilder.id()));
            assertFalse(manager.hutWindows().cycleHiring(player, builder.position()));
            assertFalse(manager.workOrders().move(player, colony.id(), orderId, 1));
            assertFalse(manager.workOrders().delete(player, colony.id(), orderId));
            res.setDeconstructed(true);
            assertFalse(manager.huts().pickUp(player, res.position(), () -> fail("no item for " + player)));
            res.setDeconstructed(false);
        }
        assertTrue(colony.work().byId(orderId).isPresent());
        assertEquals(
                List.of(bobTheBuilder.id()),
                builder.module(WorkerModule.class).orElseThrow().workers());
        assertEquals(
                HiringMode.DEFAULT,
                builder.module(WorkerModule.class).orElseThrow().hiringMode());
        assertTrue(colony.buildings().at(res.position()).isPresent());

        for (UUID player : List.of(bob, dave)) {
            manager.windows().openTownHall(player, hall);
            manager.windows().openCitizen(player, colony.id(), bobTheBuilder.id());
            manager.windows().openBuilding(player, res.position());
            manager.windows().openBuildOptions(player, res.position());
            manager.windows().openBuildingGui(player, hall);
            manager.windows().openRequests(player, colony.id());
            assertFalse(t.ui.shown.containsKey(player), "neutral and hostile see nothing");
            assertTrue(t.notifier.sent.stream()
                    .anyMatch(s -> s.player().equals(player) && s.msg().key().equals("hycolony.permission.denied")));
        }

        BuildingView friendView = view(carol, res);
        assertFalse(friendView.canManage());
        manager.windows().openTownHall(carol, hall);
        WorkOrdersView orders = ((TownHallView) t.ui.shown.get(carol)).workOrders();
        assertFalse(orders.canManage());
        assertEquals(
                List.of(new WorkOrdersView.OrderLine(
                        orderId, WorkOrderType.REPAIR, res.displayName(), 2, Optional.empty())),
                orders.orders());
    }

    @Test
    void orderWorkReturnsRefusal() {
        Building res = residence(0);
        assertEquals(
                Optional.of(WorkOrderRefusal.INVALID_TYPE),
                manager.workOrders().order(alice, res.position(), WorkOrderType.REMOVE, ""));
        assertEquals(
                "hycolony.workorder.refused.invalid_type",
                t.notifier.sent.getLast().msg().key());
        res.setLevel(5);
        assertEquals(
                Optional.of(WorkOrderRefusal.MAX_LEVEL),
                manager.workOrders().order(alice, res.position(), WorkOrderType.UPGRADE, ""));
        res.setLevel(0);

        assertEquals(
                Optional.empty(), manager.workOrders().order(alice, res.position(), WorkOrderType.BUILD, "desert"));
        assertEquals(
                "desert", colony.work().byBuilding(res.position()).orElseThrow().style());
        assertEquals(
                Optional.of(WorkOrderRefusal.ALREADY_EXISTS),
                manager.workOrders().order(alice, res.position(), WorkOrderType.BUILD, ""));
    }

    @Test
    void buildOfAHutPlacedWithAStyleUsesThatStyleByDefault() {
        Building res = residence(0);
        res.setStyle("kweebec");
        assertEquals(Optional.empty(), manager.workOrders().order(alice, res.position(), WorkOrderType.BUILD, ""));
        assertEquals(
                "kweebec",
                colony.work().byBuilding(res.position()).orElseThrow().style());
    }

    @Test
    void hireFireFromView() {
        Building hut = hut(ConstructionBuildingTypes.BUILDER, new BlockPos(30, 64, 0), 1);
        CitizenData ann = citizen(nextCitizen++, "Ann");
        CitizenData ben = citizen(nextCitizen++, "Ben");
        citizen(nextCitizen++, "Kid").setChild(true);

        BuildingView v = view(alice, hut);
        assertEquals(List.of(), v.workers());
        assertEquals(List.of(ann.id(), ben.id()), listed(v));
        assertEquals(HiringMode.DEFAULT, v.hire().orElseThrow().mode());

        assertTrue(manager.huts().hire(alice, hut.position(), ann.id()));
        v = (BuildingView) t.ui.shown.get(alice);
        assertEquals(List.of(new BuildingView.WorkerLine(ann.id(), "Ann", "hycolony:builder")), v.workers());
        assertEquals(List.of(ann.id(), ben.id()), listed(v), "Ann now works here: listed first");
        assertFalse(manager.huts().hire(alice, hut.position(), ben.id()), "a builder hut employs one worker");
        assertFalse(manager.huts().hire(alice, hut.position(), bobTheBuilder.id()), "the hut is full");

        assertTrue(manager.huts().fire(alice, hut.position(), ann.id()));
        assertTrue(((BuildingView) t.ui.shown.get(alice)).workers().isEmpty());
        assertTrue(ann.job().isEmpty());
        assertFalse(manager.huts().fire(alice, hut.position(), ann.id()), "not a worker any more");

        assertTrue(manager.hutWindows().cycleHiring(alice, hut.position())); // DEFAULT, then AUTO
        assertTrue(manager.hutWindows().cycleHiring(alice, hut.position()));
        assertEquals(
                HiringMode.MANUAL,
                ((BuildingView) t.ui.shown.get(alice)).hire().orElseThrow().mode());
        assertEquals(
                Optional.empty(),
                view(alice, colony.buildings().at(hall).orElseThrow()).hire(),
                "the town hall employs no one");
        assertFalse(manager.huts().hire(alice, hall, ann.id()), "the town hall employs no one");
    }

    /** The ids the hire window lists without "Show employed?". */
    private static List<Integer> listed(BuildingView v) {
        return v.hire().orElseThrow().listed(false).stream()
                .map(HireView.Candidate::citizenId)
                .toList();
    }

    @Test
    void hiringAnUnbuiltHutSendsANotBuiltMessageInsteadOfSilentlyFailing() {
        Building courierHut = hut(DeliverymanHut.TYPE, new BlockPos(40, 64, 0), 0);
        CitizenData idle = citizen(nextCitizen++, "Idle");

        assertFalse(manager.huts().hire(alice, courierHut.position(), idle.id()));
        assertTrue(idle.job().isEmpty());
        assertEquals("hycolony.hut.notBuiltYet", t.notifier.sent.getLast().msg().key());

        courierHut.setLevel(1);
        courierHut.setBuilt(true);
        t.notifier.sent.clear();
        assertTrue(manager.huts().hire(alice, courierHut.position(), idle.id()));
        assertTrue(t.notifier.sent.isEmpty(), "no message once the hut is built");
    }

    @Test
    void pickUpDeconstructedBuilding() {
        Building res = residence(2);

        res.setDeconstructed(true);
        assertEquals(Optional.empty(), manager.workOrders().order(alice, res.position(), WorkOrderType.REPAIR, ""));
        int orderId = colony.work().byBuilding(res.position()).orElseThrow().id();
        assertFalse(manager.huts().pickUp(alice, res.position(), () -> false), "inventory full: refused");
        assertTrue(colony.buildings().at(res.position()).isPresent(), "the building is kept");
        assertTrue(colony.work().byId(orderId).isPresent());
        assertEquals(
                "hycolony.hut.pickupInventoryFull",
                t.notifier.sent.getLast().msg().key());
        int[] given = {0};
        List<ColonyEvents.BuildingRemoved> removed = t.heard(ColonyEvents.BuildingRemoved.class);
        assertTrue(manager.huts().pickUp(alice, res.position(), () -> ++given[0] > 0));
        assertEquals(Optional.of(alice), removed.getFirst().player(), "picked up by alice");
        assertEquals(1, given[0]);
        assertTrue(colony.buildings().at(res.position()).isEmpty());
        assertTrue(colony.work().byId(orderId).isEmpty(), "removed through the normal path: its order is cancelled");

        Building townHall = colony.buildings().at(hall).orElseThrow();
        townHall.setDeconstructed(true);
        assertFalse(manager.huts().pickUp(alice, hall, () -> fail("never given")), "the town hall is never picked up");
        assertTrue(colony.buildings().at(hall).isPresent());
    }

    @Test
    void citizenViewIgnoresInventoryChangesWhichItsInventoryTabShowsLive() {
        manager.windows().openCitizen(alice, colony.id(), bobTheBuilder.id());
        CitizenView before = (CitizenView) t.ui.shown.get(alice);
        bobTheBuilder.inventory().insert(new ItemAmount(STONE_I, 3), t.catalog::maxStack);
        manager.windows().openCitizen(alice, colony.id(), bobTheBuilder.id());
        assertEquals(before, t.ui.shown.get(alice), "no live refresh of the whole window for an inventory change");
    }

    @Test
    void citizenWindowShowsJobHutActivitySkillsAndOpenRequests() {
        bobTheBuilder.skills().set(Skill.Knowledge, 7, 0);
        bobTheBuilder.inventory().insert(new ItemAmount(STONE_I, 3), t.catalog::maxStack);
        RequestToken token =
                colony.requests().createAndAssign(builder, new StackRequest(PLANK_I, 4, 4, true), bobTheBuilder.id());
        t.playerInventory.give(alice, new ItemAmount(PLANK_I, 2));

        manager.windows().openCitizen(alice, colony.id(), bobTheBuilder.id());
        CitizenView v = (CitizenView) t.ui.shown.get(alice);

        assertEquals("Bob", v.name());
        assertEquals(Optional.of("hycolony:builder"), v.jobId());
        assertEquals(Optional.of(builder.displayName()), v.workBuilding());
        assertEquals("waitingFor", v.activity());
        assertEquals(Optional.of(new StackRequest(PLANK_I, 4, 4, true)), v.waitingFor());
        assertEquals(Skill.values().length, v.skills().size());
        assertEquals(Skill.Adaptability, v.skills().get(0).skill(), "the builder's primary skill first");
        assertEquals(Skill.Athletics, v.skills().get(1).skill(), "then its secondary");
        assertTrue(v.skills().get(0).jobSkill());
        assertEquals(
                7,
                v.skills().stream()
                        .filter(r -> r.skill() == Skill.Knowledge)
                        .findFirst()
                        .orElseThrow()
                        .level());
        assertEquals(
                List.of(new RequestsView.RequestRow(token, new StackRequest(PLANK_I, 4, 4, true), "Bob", 2, 0)),
                v.requests());

        assertTrue(manager.requestActions().fulfil(alice, colony.id(), token)); // the window's "Supply"
        manager.windows().openCitizen(alice, colony.id(), bobTheBuilder.id());
        CitizenView after = (CitizenView) t.ui.shown.get(alice);
        assertEquals(List.of(), after.requests());
        assertEquals(Optional.empty(), after.waitingFor());
        assertEquals("absent", after.activity(), "no body in the world");
    }

    /** MC Permissions.hasPermission(Player, Action): a creative operator opens and manages a foreign colony's hut. */
    @Test
    void aCreativeOperatorManagesAForeignHutFromItsWindow() {
        Building res = residence(1);
        t.players.creativeOperators.add(bob); // neutral in this colony

        BuildingView v = view(bob, res);

        assertTrue(v.canManage());
    }

    /** MC keeps a wandering citizen IDLE; the town hall still shows one walking about as wandering. */
    @Test
    void anIdleCitizenWalkingAboutShowsAsWandering() {
        citizen(9, "Walker");
        BodyId body = t.bodies.existing(colony.id(), 9, new Vec3(0, 64, 0));
        colony.citizens().onBodyLoaded(body, 9);

        t.bodies.bodies.get(body).status = NavStatus.MOVING;
        manager.windows().openTownHall(alice, hall);
        assertEquals("wandering", statusOf("Walker"));
        t.bodies.bodies.get(body).status = NavStatus.ARRIVED;
        manager.windows().openTownHall(alice, hall);
        assertEquals("idle", statusOf("Walker"));
    }

    private String statusOf(String name) {
        return ((TownHallView) t.ui.shown.get(alice))
                .citizens().stream()
                        .filter(r -> r.name().equals(name))
                        .findFirst()
                        .orElseThrow()
                        .status();
    }

    @Test
    void citizenWindowNeedsAccessHuts() {
        CitizenData idle = citizen(9, "Idle");
        manager.windows().openCitizen(bob, colony.id(), idle.id());
        assertFalse(t.ui.shown.containsKey(bob));
        assertEquals("hycolony.permission.denied", t.notifier.sent.get(0).msg().key());

        manager.windows().openCitizen(carol, colony.id(), idle.id());
        CitizenView v = (CitizenView) t.ui.shown.get(carol);
        assertEquals(Optional.empty(), v.jobId());
        assertEquals(Optional.empty(), v.workBuilding());
        assertEquals(List.of(), v.requests());
        assertEquals(Optional.empty(), v.jobActivity(), "no job AI running");
        assertFalse(v.skills().get(0).jobSkill(), "no workplace: no highlighted skill");
    }
}
