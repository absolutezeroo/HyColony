package dev.hycolony.core.construction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.BuildingType;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.ClaimCell;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ColonyEvents;
import dev.hycolony.core.colony.ColonyManager;
import dev.hycolony.core.colony.ColonySerializer;
import dev.hycolony.core.colony.TerritoryIndex;
import dev.hycolony.core.job.WorkerModule;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Either;
import dev.hycolony.core.kernel.item.BlockKey;
import dev.hycolony.core.kernel.item.BlockState;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.request.model.StackRequest;
import dev.hycolony.core.testing.TestContexts;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorkManagerTest {
    private final TestContexts t = new TestContexts();
    /** What the fake blueprint source returns; null = no blueprint. */
    private Blueprint blueprint = blueprintAt(new BlockPos(1, 0, 0));

    private final List<String> loads = new ArrayList<>();
    private final ColonyManager manager;
    private final UUID alice = UUID.randomUUID();
    private final Colony colony;
    private int nextCitizen = 1;

    WorkManagerTest() {
        t.blueprints = new BlueprintSource() {
            @Override
            public Optional<Blueprint> load(String style, String buildingTypeId, int level, int rotation) {
                loads.add(style + "/" + buildingTypeId + "/" + level + "/" + rotation);
                return Optional.ofNullable(blueprint);
            }

            @Override
            public List<String> styles() {
                return List.of("medieval", "desert");
            }
        };
        manager = new ColonyManager(t.context());
        manager.beginFoundation(alice, "Alice", new BlockPos(0, 64, 0), 0);
        colony = manager.confirmFoundation(alice, "A").orElseThrow();
        t.notifier.sent.clear();
    }

    private static Blueprint blueprintAt(BlockPos offset) {
        return new Blueprint(
                "bp",
                List.of(new BlueprintEntry(offset, new BlockState(new BlockKey("Stone"), 0), false)),
                offset,
                offset);
    }

    private Building hut(BuildingType type, BlockPos pos, int level) {
        manager.placeHut(colony, type.id(), pos, 0);
        Building b = colony.buildings().at(pos).orElseThrow();
        b.setLevel(level);
        return b;
    }

    private Building builder(BlockPos pos, int level) {
        Building b = hut(ConstructionBuildingTypes.BUILDER, pos, level);
        CitizenData citizen = new CitizenData(nextCitizen++);
        colony.citizens().restore(citizen);
        assertTrue(b.module(WorkerModule.class).orElseThrow().hire(colony, b, citizen));
        return b;
    }

    private Building residence(BlockPos pos, int level) {
        return hut(ConstructionBuildingTypes.RESIDENCE, pos, level);
    }

    private Either<WorkOrder, WorkOrderRefusal> request(BlockPos pos, WorkOrderType type) {
        return colony.work().request(alice, pos, type, "", Optional.empty());
    }

    private WorkOrder created(BlockPos pos, WorkOrderType type) {
        Either<WorkOrder, WorkOrderRefusal> r = request(pos, type);
        assertTrue(r instanceof Either.Left, () -> "refused: " + r);
        return ((Either.Left<WorkOrder, WorkOrderRefusal>) r).value();
    }

    private void assertRefused(WorkOrderRefusal expected, Either<WorkOrder, WorkOrderRefusal> r) {
        assertEquals(new Either.Right<WorkOrder, WorkOrderRefusal>(expected), r);
    }

    // ---- creation ----

    @Test
    void createsOrderNotifiesMembersAndPostsEvent() {
        List<ColonyEvents.WorkOrderCreated> events = new ArrayList<>();
        t.bus.subscribe(ColonyEvents.WorkOrderCreated.class, events::add);
        builder(new BlockPos(10, 64, 0), 1);
        Building res = residence(new BlockPos(20, 64, 0), 0);

        WorkOrder o = created(res.position(), WorkOrderType.BUILD);

        assertEquals(1, o.id());
        assertEquals(WorkOrderType.BUILD, o.type());
        assertEquals(1, o.targetLevel());
        assertEquals("medieval", o.style()); // no style given nor on the building: first style
        assertEquals(res.rotation(), o.rotation());
        assertEquals(Stage.CLEAR, o.stage());
        assertTrue(o.claimedBy().isEmpty());
        assertEquals(List.of("medieval/hycolony:residence/1/0"), loads);
        assertEquals(Optional.of(o), colony.work().byId(1));
        assertEquals(1, t.notifier.sent.size());
        assertEquals(alice, t.notifier.sent.get(0).player());
        assertEquals("hycolony.workorder.created", t.notifier.sent.get(0).msg().key());
        assertEquals(1, events.size());
        assertEquals(o, events.get(0).order());
    }

    @Test
    void targetsAndStyleFollowTheRules() {
        builder(new BlockPos(10, 64, 0), 5);
        Building up = residence(new BlockPos(20, 64, 0), 2);
        up.setStyle("desert");
        Building rep = residence(new BlockPos(30, 64, 0), 3);
        Building rem = residence(new BlockPos(40, 64, 0), 4);

        WorkOrder upgrade = created(up.position(), WorkOrderType.UPGRADE);
        assertEquals(3, upgrade.targetLevel());
        assertEquals("desert", upgrade.style());
        assertEquals(Stage.SOLID, upgrade.stage());
        assertEquals(3, created(rep.position(), WorkOrderType.REPAIR).targetLevel());
        colony.work().request(alice, rem.position(), WorkOrderType.REMOVE, "custom", Optional.empty());
        WorkOrder remove = colony.work().byBuilding(rem.position()).orElseThrow();
        assertEquals(0, remove.targetLevel());
        assertEquals(4, remove.blueprintLevel());
        assertEquals(3, upgrade.blueprintLevel());
        assertEquals("custom", remove.style());
        assertEquals(Stage.REMOVE, remove.stage());
    }

    @Test
    void refusesWithoutManageHutsPermission() {
        builder(new BlockPos(10, 64, 0), 1);
        Building res = residence(new BlockPos(20, 64, 0), 0);
        assertRefused(
                WorkOrderRefusal.NO_PERMISSION,
                colony.work().request(UUID.randomUUID(), res.position(), WorkOrderType.BUILD, "", Optional.empty()));
    }

    @Test
    void refusesWhenAnOrderAlreadyExists() {
        builder(new BlockPos(10, 64, 0), 1);
        Building res = residence(new BlockPos(20, 64, 0), 0);
        created(res.position(), WorkOrderType.BUILD);
        assertRefused(WorkOrderRefusal.ALREADY_EXISTS, request(res.position(), WorkOrderType.UPGRADE));
    }

    @Test
    void refusesUpgradeAtMaxLevel() {
        builder(new BlockPos(10, 64, 0), 5);
        Building res = residence(new BlockPos(20, 64, 0), 5);
        assertRefused(WorkOrderRefusal.MAX_LEVEL, request(res.position(), WorkOrderType.UPGRADE));
    }

    @Test
    void refusesRepairOfUnbuiltBuilding() {
        builder(new BlockPos(10, 64, 0), 1);
        Building res = residence(new BlockPos(20, 64, 0), 0);
        assertRefused(WorkOrderRefusal.NOT_BUILT, request(res.position(), WorkOrderType.REPAIR));
    }

    @Test
    void refusesWhenNoEmployedBuilderHasTheLevel() {
        builder(new BlockPos(10, 64, 0), 1);
        hut(ConstructionBuildingTypes.BUILDER, new BlockPos(12, 64, 0), 5); // level 5 but nobody works there
        Building res = residence(new BlockPos(20, 64, 0), 1);
        assertRefused(WorkOrderRefusal.BUILDER_NECESSARY, request(res.position(), WorkOrderType.UPGRADE));
    }

    @Test
    void refusesWhenEveryBuilderIsTooFar() {
        builder(new BlockPos(10, 64, 0), 1);
        Building res = residence(new BlockPos(10, 64, 101), 0);
        assertRefused(WorkOrderRefusal.BUILDER_TOO_FAR_AWAY, request(res.position(), WorkOrderType.BUILD));
    }

    @Test
    void refusesWithoutBlueprint() {
        builder(new BlockPos(10, 64, 0), 1);
        Building res = residence(new BlockPos(20, 64, 0), 0);
        blueprint = null;
        assertRefused(WorkOrderRefusal.NO_BLUEPRINT, request(res.position(), WorkOrderType.BUILD));
    }

    @Test
    void refusesFootprintOutsideTheColony() {
        builder(new BlockPos(10, 64, 0), 1);
        Building res = residence(new BlockPos(20, 64, 0), 0);
        blueprint = blueprintAt(new BlockPos(5000, 0, 0));
        assertRefused(WorkOrderRefusal.OUT_OF_COLONY, request(res.position(), WorkOrderType.BUILD));
    }

    @Test
    void builderCanOrderOwnNextLevelAtLevel0() {
        Building own = builder(new BlockPos(10, 64, 0), 0);
        WorkOrder o = created(own.position(), WorkOrderType.BUILD);
        colony.work().tick();
        assertEquals(Optional.of(own.position()), o.claimedBy());
    }

    // ---- ordering ----

    @Test
    void sortedByPriorityThenId() {
        builder(new BlockPos(10, 64, 0), 1);
        WorkOrder a = created(residence(new BlockPos(20, 64, 0), 0).position(), WorkOrderType.BUILD);
        WorkOrder b = created(residence(new BlockPos(30, 64, 0), 0).position(), WorkOrderType.BUILD);
        WorkOrder c = created(residence(new BlockPos(40, 64, 0), 0).position(), WorkOrderType.BUILD);
        c.setPriority(2);
        assertEquals(List.of(c, a, b), colony.work().ordered());
    }

    @Test
    void moveSwapsPriorityWithNeighbour() {
        builder(new BlockPos(10, 64, 0), 1);
        WorkOrder a = created(residence(new BlockPos(20, 64, 0), 0).position(), WorkOrderType.BUILD);
        WorkOrder b = created(residence(new BlockPos(30, 64, 0), 0).position(), WorkOrderType.BUILD);
        WorkOrder c = created(residence(new BlockPos(40, 64, 0), 0).position(), WorkOrderType.BUILD);

        colony.work().move(c.id(), 1); // up: previous (b) priority + 1
        assertEquals(1, c.priority());
        assertEquals(List.of(c, a, b), colony.work().ordered());

        colony.work().move(c.id(), -1); // down: next (a) priority - 1
        assertEquals(-1, c.priority());
        assertEquals(List.of(a, b, c), colony.work().ordered());

        colony.work().move(a.id(), 1); // already first: no-op
        assertEquals(0, a.priority());
    }

    // ---- assignment ----

    @Test
    void assignsToFirstEligibleIdleBuilder() {
        Building first = builder(new BlockPos(10, 64, 0), 1);
        Building manual = builder(new BlockPos(11, 64, 0), 1);
        manual.module(BuilderSettingsModule.class).orElseThrow().setMode(BuilderSettingsModule.Mode.MANUAL);
        Building second = builder(new BlockPos(12, 64, 0), 1);
        WorkOrder a = created(residence(new BlockPos(20, 64, 0), 0).position(), WorkOrderType.BUILD);
        WorkOrder b = created(residence(new BlockPos(30, 64, 0), 0).position(), WorkOrderType.BUILD);
        WorkOrder c = created(residence(new BlockPos(40, 64, 0), 0).position(), WorkOrderType.BUILD);
        b.setPriority(1);

        colony.work().tick();

        assertEquals(Optional.of(first.position()), b.claimedBy()); // highest priority first
        assertEquals(Optional.of(second.position()), a.claimedBy()); // manual builder skipped
        assertTrue(c.claimedBy().isEmpty()); // nobody idle left
        assertEquals(Optional.of(b), colony.work().claimedBy(first.position()));
        assertTrue(colony.work().claimedBy(manual.position()).isEmpty());
    }

    @Test
    void level1BuilderCannotTakeLevel2Order() {
        builder(new BlockPos(10, 64, 0), 1);
        builder(new BlockPos(0, 64, 300), 2); // qualifies the order, but too far to take it
        Building res = residence(new BlockPos(20, 64, 0), 1);
        WorkOrder o = created(res.position(), WorkOrderType.UPGRADE);

        colony.work().tick();

        assertTrue(o.claimedBy().isEmpty());
    }

    @Test
    void builderAtLevel5TakesAnything() {
        Building b5 = builder(new BlockPos(10, 64, 0), 5);
        WorkOrder o = created(residence(new BlockPos(20, 64, 0), 4).position(), WorkOrderType.UPGRADE);
        assertEquals(5, o.targetLevel());
        assertTrue(WorkManager.canBuild(b5, o, 5));
        assertFalse(WorkManager.canBuild(b5, o, 4));
        colony.work().tick();
        assertEquals(Optional.of(b5.position()), o.claimedBy());
    }

    @Test
    void tooFarBuilderNotAssigned() {
        Building near = builder(new BlockPos(10, 64, 0), 3);
        near.module(BuilderSettingsModule.class).orElseThrow().setMode(BuilderSettingsModule.Mode.MANUAL);
        Building far = builder(new BlockPos(20, 64, 150), 3);
        WorkOrder o = created(residence(new BlockPos(20, 64, 0), 0).position(), WorkOrderType.BUILD);

        assertFalse(WorkManager.canBuild(far, o, 5)); // 150 blocks away
        colony.work().tick();

        assertTrue(o.claimedBy().isEmpty());
    }

    // ---- cancellation and removal ----

    @Test
    void cancelReleasesAndCancelsRequests() {
        Building b = builder(new BlockPos(10, 64, 0), 1);
        WorkOrder o = created(residence(new BlockPos(20, 64, 0), 0).position(), WorkOrderType.BUILD);
        colony.work().tick();
        colony.requests().createAndAssign(b, new StackRequest(new ItemKey("Stone"), 4, 4, true), 1);
        assertEquals(1, colony.requests().byRequester(b.requesterId()).size());

        assertTrue(manager.deleteWorkOrder(alice, colony.id(), o.id()));

        assertTrue(colony.work().byId(o.id()).isEmpty());
        assertTrue(colony.work().byBuilding(o.buildingPos()).isEmpty());
        assertTrue(o.claimedBy().isEmpty());
        assertTrue(colony.work().claimedBy(b.position()).isEmpty());
        assertTrue(colony.requests().byRequester(b.requesterId()).isEmpty());
    }

    @Test
    void builderHutRemovedReleasesOrderAndCancelsRequests() {
        Building b = builder(new BlockPos(10, 64, 0), 1);
        WorkOrder o = created(residence(new BlockPos(20, 64, 0), 0).position(), WorkOrderType.BUILD);
        colony.work().tick();
        o.setStage(Stage.SOLID);
        o.setProgressIndex(7);
        colony.requests().createAndAssign(b, new StackRequest(new ItemKey("Stone"), 4, 4, true), 1);

        manager.onHutRemoved(b.position());

        assertEquals(Optional.of(o), colony.work().byId(o.id()));
        assertTrue(o.claimedBy().isEmpty());
        assertEquals(Stage.CLEAR, o.stage());
        assertEquals(0, o.progressIndex());
        assertTrue(colony.requests().byRequester(b.requesterId()).isEmpty());
    }

    // ---- persistence ----

    @Test
    void workOrdersPersist() {
        Building b = builder(new BlockPos(10, 64, 0), 1);
        WorkOrder a = created(residence(new BlockPos(20, 64, 0), 0).position(), WorkOrderType.BUILD);
        WorkOrder c = created(residence(new BlockPos(30, 64, 0), 0).position(), WorkOrderType.BUILD);
        c.setPriority(3);
        colony.work().tick();
        c.setStage(Stage.SOLID);
        c.setProgressIndex(12);
        b.setDeconstructed(true);

        JsonObject json = ColonySerializer.write(colony);
        TerritoryIndex territory = new TerritoryIndex();
        territory.claimSquare(colony.id(), ClaimCell.of(colony.center()), t.config.initialColonySize());
        Colony loaded = ColonySerializer.read(json, t.context(), territory);

        assertEquals(json.getAsJsonArray("workOrders"), WorkOrderSerializer.write(loaded.work()));
        WorkOrder lc = loaded.work().byId(c.id()).orElseThrow();
        assertEquals(Optional.of(b.position()), lc.claimedBy());
        assertEquals(Stage.SOLID, lc.stage());
        assertEquals(12, lc.progressIndex());
        assertEquals(3, lc.priority());
        assertEquals(
                List.of(c.id(), a.id()),
                loaded.work().ordered().stream().map(WorkOrder::id).toList());
        assertTrue(loaded.buildings().at(b.position()).orElseThrow().isDeconstructed());
        // ids continue after the highest loaded one
        Building res = loaded.buildings().at(new BlockPos(20, 64, 0)).orElseThrow();
        loaded.work().complete(loaded.work().byId(a.id()).orElseThrow());
        Either<WorkOrder, WorkOrderRefusal> next =
                loaded.work().request(alice, res.position(), WorkOrderType.BUILD, "", Optional.empty());
        assertEquals(
                3, ((Either.Left<WorkOrder, WorkOrderRefusal>) next).value().id());
    }

    // ---- fix round 1 ----

    private WorkOrder createdFor(BlockPos pos, WorkOrderType type, BlockPos chosen) {
        Either<WorkOrder, WorkOrderRefusal> r = colony.work().request(alice, pos, type, "", Optional.of(chosen));
        assertTrue(r instanceof Either.Left, () -> "refused: " + r);
        return ((Either.Left<WorkOrder, WorkOrderRefusal>) r).value();
    }

    @Test
    void level1BuilderTakesRemoveOfLevel3Building() {
        Building b1 = builder(new BlockPos(10, 64, 0), 1);
        Building res = residence(new BlockPos(20, 64, 0), 3);
        WorkOrder o = created(res.position(), WorkOrderType.REMOVE);
        assertEquals(0, o.targetLevel());
        assertEquals(3, o.blueprintLevel());
        assertEquals(List.of("medieval/hycolony:residence/3/0"), loads); // plan of the current level

        colony.work().tick();

        assertEquals(Optional.of(b1.position()), o.claimedBy());
    }

    @Test
    void chosenBuilderMayTakeRemoveWhateverItsLevel() {
        Building b0 = builder(new BlockPos(10, 64, 0), 0);
        Building res = residence(new BlockPos(20, 64, 0), 3);
        assertEquals(
                Optional.of(b0.position()),
                createdFor(res.position(), WorkOrderType.REMOVE, b0.position()).claimedBy());
    }

    @Test
    void footprintMarginOutsideTerritoryIsRefused() {
        builder(new BlockPos(10, 64, 0), 1);
        Building res = residence(new BlockPos(20, 64, 0), 0);
        // the only entry is inside, but the plan's bounds reach 5000 blocks east
        blueprint = new Blueprint(
                "bp",
                List.of(new BlueprintEntry(new BlockPos(1, 0, 0), new BlockState(new BlockKey("Stone"), 0), false)),
                new BlockPos(0, 0, 0),
                new BlockPos(5000, 0, 0));
        assertRefused(WorkOrderRefusal.OUT_OF_COLONY, request(res.position(), WorkOrderType.BUILD));
    }

    @Test
    void footprintReachingIntoNegativeUnclaimedCellsIsRefused() {
        builder(new BlockPos(10, 64, 0), 1);
        Building res = residence(new BlockPos(20, 64, 0), 0);
        blueprint = new Blueprint(
                "bp",
                List.of(new BlueprintEntry(new BlockPos(0, 0, 0), new BlockState(new BlockKey("Stone"), 0), false)),
                new BlockPos(-5000, 0, -1),
                new BlockPos(0, 0, 0));
        assertRefused(WorkOrderRefusal.OUT_OF_COLONY, request(res.position(), WorkOrderType.BUILD));
    }

    @Test
    void removeOrderLevelsPersist() {
        builder(new BlockPos(10, 64, 0), 1);
        Building res = residence(new BlockPos(20, 64, 0), 3);
        WorkOrder o = created(res.position(), WorkOrderType.REMOVE);
        TerritoryIndex territory = new TerritoryIndex();
        territory.claimSquare(colony.id(), ClaimCell.of(colony.center()), t.config.initialColonySize());
        WorkOrder loaded = ColonySerializer.read(ColonySerializer.write(colony), t.context(), territory)
                .work()
                .byId(o.id())
                .orElseThrow();
        assertEquals(0, loaded.targetLevel());
        assertEquals(3, loaded.blueprintLevel());
    }

    @Test
    void orderForMissingBuildingIsDropped() {
        builder(new BlockPos(10, 64, 0), 1);
        Building res = residence(new BlockPos(20, 64, 0), 0);
        WorkOrder o = created(res.position(), WorkOrderType.BUILD);
        JsonObject json = ColonySerializer.write(colony);
        JsonArray kept = new JsonArray();
        for (JsonElement el : json.getAsJsonArray("buildings")) {
            if (el.getAsJsonObject().getAsJsonObject("pos").get("x").getAsInt() != 20) {
                kept.add(el);
            }
        }
        json.add("buildings", kept); // the residence is gone, its order is still saved
        TerritoryIndex territory = new TerritoryIndex();
        territory.claimSquare(colony.id(), ClaimCell.of(colony.center()), t.config.initialColonySize());
        Colony loaded = ColonySerializer.read(json, t.context(), territory);
        assertTrue(loaded.work().byId(o.id()).isPresent());

        loaded.work().tick();

        assertTrue(loaded.work().byId(o.id()).isEmpty());
        assertTrue(loaded.work().byBuilding(res.position()).isEmpty());
    }

    @Test
    void cancelOnlyCancelsRequestsOfTheBuildersActiveOrder() {
        Building b = builder(new BlockPos(10, 64, 0), 1);
        WorkOrder active =
                createdFor(residence(new BlockPos(20, 64, 0), 0).position(), WorkOrderType.BUILD, b.position());
        WorkOrder queued =
                createdFor(residence(new BlockPos(30, 64, 0), 0).position(), WorkOrderType.BUILD, b.position());
        assertEquals(Optional.of(active), colony.work().claimedBy(b.position()));
        colony.requests().createAndAssign(b, new StackRequest(new ItemKey("Stone"), 4, 4, true), 1);

        colony.work().cancel(queued.id());
        assertEquals(1, colony.requests().byRequester(b.requesterId()).size());

        colony.work().cancel(active.id());
        assertTrue(colony.requests().byRequester(b.requesterId()).isEmpty());
    }

    @Test
    void buildOnBuiltBuildingIsRefused() {
        builder(new BlockPos(10, 64, 0), 5);
        Building built = residence(new BlockPos(20, 64, 0), 1);
        assertRefused(WorkOrderRefusal.INVALID_TYPE, request(built.position(), WorkOrderType.BUILD));
        Building unbuilt = residence(new BlockPos(30, 64, 0), 0);
        assertRefused(WorkOrderRefusal.INVALID_TYPE, request(unbuilt.position(), WorkOrderType.UPGRADE));
        Building gone = residence(new BlockPos(40, 64, 0), 2);
        gone.setDeconstructed(true);
        assertRefused(WorkOrderRefusal.INVALID_TYPE, request(gone.position(), WorkOrderType.BUILD));
        assertRefused(WorkOrderRefusal.INVALID_TYPE, request(gone.position(), WorkOrderType.REMOVE)); // MC picks it up
        assertRefused(WorkOrderRefusal.INVALID_TYPE, request(unbuilt.position(), WorkOrderType.REMOVE));
        assertEquals(2, created(gone.position(), WorkOrderType.REPAIR).targetLevel());
    }

    @Test
    void builderSettingsFallBackToAutoOnUnknownMode() {
        BuilderSettingsModule m = new BuilderSettingsModule();
        m.setMode(BuilderSettingsModule.Mode.MANUAL);
        JsonObject in = new JsonObject();
        in.addProperty("mode", "SOMETHING_ELSE");
        m.read(in);
        assertEquals(BuilderSettingsModule.Mode.AUTO, m.mode());
    }

    @Test
    void chosenBuilderIsClaimedAtCreation() {
        builder(new BlockPos(10, 64, 0), 1);
        Building chosen = builder(new BlockPos(12, 64, 0), 1);
        Building res = residence(new BlockPos(20, 64, 0), 0);
        assertEquals(
                Optional.of(chosen.position()),
                createdFor(res.position(), WorkOrderType.BUILD, chosen.position())
                        .claimedBy());
    }

    @Test
    void chosenBuilderBypassesTooFar() {
        Building far = builder(new BlockPos(20, 64, 150), 1);
        Building res = residence(new BlockPos(20, 64, 0), 0);
        assertRefused(WorkOrderRefusal.BUILDER_TOO_FAR_AWAY, request(res.position(), WorkOrderType.BUILD));
        assertEquals(
                Optional.of(far.position()),
                createdFor(res.position(), WorkOrderType.BUILD, far.position()).claimedBy());
    }

    @Test
    void chosenBuilderBelowTargetIsRefused() {
        builder(new BlockPos(10, 64, 0), 3);
        Building low = builder(new BlockPos(12, 64, 0), 1);
        Building res = residence(new BlockPos(20, 64, 0), 1);
        assertRefused(
                WorkOrderRefusal.BUILDER_NECESSARY,
                colony.work().request(alice, res.position(), WorkOrderType.UPGRADE, "", Optional.of(low.position())));
    }

    @Test
    void earlierRefusalWins() {
        // max level AND no builder at all: MAX_LEVEL is checked first
        Building res = residence(new BlockPos(20, 64, 0), 5);
        assertRefused(WorkOrderRefusal.MAX_LEVEL, request(res.position(), WorkOrderType.UPGRADE));
        // no permission AND already exists: NO_PERMISSION first
        builder(new BlockPos(10, 64, 0), 1);
        Building other = residence(new BlockPos(30, 64, 0), 0);
        created(other.position(), WorkOrderType.BUILD);
        assertRefused(
                WorkOrderRefusal.NO_PERMISSION,
                colony.work().request(UUID.randomUUID(), other.position(), WorkOrderType.BUILD, "", Optional.empty()));
    }

    // ---- final fix wave ----

    @Test
    void buildingKeepsTheStyleItWasBuiltIn() {
        builder(new BlockPos(10, 64, 0), 5);
        Building res = residence(new BlockPos(20, 64, 0), 0);
        Either<WorkOrder, WorkOrderRefusal> r =
                colony.work().request(alice, res.position(), WorkOrderType.BUILD, "desert", Optional.empty());
        WorkOrder build = ((Either.Left<WorkOrder, WorkOrderRefusal>) r).value();
        assertEquals("desert", res.style());
        colony.work().complete(build);
        res.setLevel(1);
        loads.clear();

        WorkOrder remove = created(res.position(), WorkOrderType.REMOVE);

        assertEquals("desert", remove.style());
        assertEquals(List.of("desert/hycolony:residence/1/0"), loads);
        colony.work().cancel(remove.id());
        colony.work().request(alice, res.position(), WorkOrderType.REPAIR, "medieval", Optional.empty());
        assertEquals(
                "desert", colony.work().byBuilding(res.position()).orElseThrow().style());
    }

    @Test
    void orderIdsAreNeverReusedAfterReload() {
        builder(new BlockPos(10, 64, 0), 1);
        Building res = residence(new BlockPos(20, 64, 0), 0);
        colony.work().complete(created(res.position(), WorkOrderType.BUILD)); // id 1, gone

        TerritoryIndex territory = new TerritoryIndex();
        territory.claimSquare(colony.id(), ClaimCell.of(colony.center()), t.config.initialColonySize());
        Colony loaded = ColonySerializer.read(ColonySerializer.write(colony), t.context(), territory);
        Either<WorkOrder, WorkOrderRefusal> next =
                loaded.work().request(alice, res.position(), WorkOrderType.BUILD, "", Optional.empty());

        assertEquals(
                2, ((Either.Left<WorkOrder, WorkOrderRefusal>) next).value().id());
    }

    @Test
    void oldRequestedFlagIsReadAndDropped() {
        builder(new BlockPos(10, 64, 0), 1);
        JsonObject json = created(residence(new BlockPos(20, 64, 0), 0).position(), WorkOrderType.BUILD)
                .write();
        json.addProperty("requested", true); // saved by an earlier version

        JsonObject rewritten = WorkOrder.read(json).write();

        assertFalse(rewritten.has("requested"));
        json.remove("requested");
        assertEquals(json, rewritten);
    }
}
