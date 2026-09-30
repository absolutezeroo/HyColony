package dev.hycolony.core.construction;

import static dev.hycolony.core.testing.FakeBlueprints.CHEST_I;
import static dev.hycolony.core.testing.FakeBlueprints.DIRT;
import static dev.hycolony.core.testing.FakeBlueprints.DIRT_I;
import static dev.hycolony.core.testing.FakeBlueprints.GLASS;
import static dev.hycolony.core.testing.FakeBlueprints.GLASS_I;
import static dev.hycolony.core.testing.FakeBlueprints.HUT_BLOCK;
import static dev.hycolony.core.testing.FakeBlueprints.PLANKS;
import static dev.hycolony.core.testing.FakeBlueprints.PLANKS_I;
import static dev.hycolony.core.testing.FakeBlueprints.TORCH;
import static dev.hycolony.core.testing.FakeBlueprints.TORCH_I;
import static dev.hycolony.core.testing.FakeBlueprints.state;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.app.HutPlacement;
import dev.hycolony.core.app.ui.BuildingView;
import dev.hycolony.core.app.ui.RequestsView;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.BuildingType;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.Skill;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.EventLog;
import dev.hycolony.core.construction.blueprint.Blueprint;
import dev.hycolony.core.construction.blueprint.BlueprintEntry;
import dev.hycolony.core.construction.blueprint.StructurePlan;
import dev.hycolony.core.construction.hut.ConstructionBuildingTypes;
import dev.hycolony.core.construction.resources.BuilderResourcesView;
import dev.hycolony.core.construction.workorder.Stage;
import dev.hycolony.core.construction.workorder.WorkOrder;
import dev.hycolony.core.construction.workorder.WorkOrderType;
import dev.hycolony.core.job.WorkerModule;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.BlockState;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.persist.FileColonyStorage;
import dev.hycolony.core.kernel.persist.MigrationChain;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.model.RequestState;
import dev.hycolony.core.request.model.RequestToken;
import dev.hycolony.core.request.model.StackRequest;
import dev.hycolony.core.request.resolver.RetryingResolver;
import dev.hycolony.core.testing.FakeBlueprints;
import dev.hycolony.core.testing.TestContexts;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End to end, the way a player plays: found a colony, place a hut, let the colony hire, order work, supply the
 * clipboard's requests, save and restart. Only the fakes stand in for Hytale; every tick goes through
 * {@link ColonyManager#tick()}.
 */
class ConstructionSimulationTest {
    private static final BlockPos TOWN_HALL = new BlockPos(0, 64, 0);
    /** Claim cell 4: the edge of the initial territory, so a finished level 1 claims cell 5. */
    private static final BlockPos HUT = new BlockPos(70, 64, 0);

    private static final BlockPos BEYOND = new BlockPos(85, 64, 0);
    private static final int MAX_TICKS = 200_000;

    @TempDir
    Path dir;

    private final TestContexts t = new TestContexts();
    private final FakeBlueprints blueprints = new FakeBlueprints();
    /** Also the owner saved in the mid-build fixture. */
    private final UUID alice = UUID.fromString("00000000-0000-0000-0000-00000000a11c");

    private ColonyManager manager;
    private Colony colony;
    /**
     * Every request seen, by token, from its creation (even one closed within a tick): a cleaned request keeps its
     * final state (RECEIVED...).
     */
    private final Map<RequestToken, Request> seen = new LinkedHashMap<>();
    /** Every resolver each request was seen with. */
    private final Map<RequestToken, Set<String>> resolversSeen = new HashMap<>();
    /** The player supplies the clipboard's requests every second. */
    private boolean autoFulfil = true;

    private int fulfils;
    /** Held strongly: LogManager only keeps loggers weakly, and a collected one would drop the spy. */
    private final Logger hycolonyLog = Logger.getLogger("dev.hycolony");
    /** Any warning or error the core logs (a caught AI exception, a failed save, a lost request...). */
    private final List<LogRecord> warnings = new ArrayList<>();

    private final Handler logSpy = new Handler() {
        @Override
        public void publish(LogRecord r) {
            if (r.getLevel().intValue() >= Level.WARNING.intValue()) {
                warnings.add(r);
            }
        }

        @Override
        public void flush() {}

        @Override
        public void close() {}
    };

    @BeforeEach
    void foundColony() {
        hycolonyLog.addHandler(logSpy);
        t.bodies.instant = true;
        t.blueprints = blueprints;
        FakeBlueprints.registerBlocks(t.catalog);
        blueprints
                .put(ConstructionBuildingTypes.BUILDER.id(), 1, FakeBlueprints.hut(false))
                .put(ConstructionBuildingTypes.BUILDER.id(), 2, FakeBlueprints.hut(true))
                .put(ConstructionBuildingTypes.RESIDENCE.id(), 1, FakeBlueprints.hut(false));
        t.players.online.put(alice, TOWN_HALL.offset(3, 0, 3)); // inside: the colony is ACTIVE
        manager = newManager();
        manager.foundation().begin(alice, "Alice", TOWN_HALL, 0);
        colony = manager.foundation().confirm(alice, "Simulation").orElseThrow();
        watch(colony);
        t.blocks.blocks.put(TOWN_HALL, state(HUT_BLOCK));
    }

    @AfterEach
    void noWarnings() {
        hycolonyLog.removeHandler(logSpy);
        assertTrue(
                warnings.isEmpty(),
                () -> "logged: "
                        + warnings.stream()
                                .map(r -> r.getLevel() + " " + r.getMessage() + " " + r.getThrown())
                                .toList());
    }

    private ColonyManager newManager() {
        ColonyManager m = t.manager();
        m.persistence().setStorage(new FileColonyStorage(dir), MigrationChain.sp3b());
        return m;
    }

    private void stockPlayer(ItemKey item, int n) {
        t.playerInventory.give(alice, new ItemAmount(item, n));
    }

    private int playerHas(ItemKey item) {
        return t.playerInventory.count(alice, item);
    }

    private Building placeHut(BuildingType type, BlockPos pos) {
        assertInstanceOf(HutPlacement.Allowed.class, manager.huts().checkPlacement(alice, pos, type.id()));
        t.blocks.blocks.put(pos, state(HUT_BLOCK));
        manager.huts().place(colony, type.id(), pos, 0, UUID.randomUUID());
        return colony.buildings().at(pos).orElseThrow();
    }

    private void order(BlockPos pos, WorkOrderType type) {
        assertEquals(Optional.empty(), manager.workOrders().order(alice, pos, type, ""));
    }

    /** The clipboard, supplied from the player's inventory. */
    private void fulfilAll() {
        manager.windows().openRequests(alice, colony.id());
        RequestsView view = (RequestsView) t.ui.shown.get(alice);
        for (RequestsView.RequestRow row : view.rows()) {
            if (row.playerHas() > 0 && manager.requestActions().fulfil(alice, colony.id(), row.token())) {
                fulfils++;
            }
        }
    }

    private void watch(Colony c) {
        c.requests().setCreationListener(r -> seen.put(r.token(), r));
    }

    private void tick() {
        t.clock.tick++;
        manager.tick();
        for (Request r : colony.requests().all()) {
            seen.put(r.token(), r);
            colony.requests()
                    .resolverOf(r.token())
                    .ifPresent(res -> resolversSeen
                            .computeIfAbsent(r.token(), k -> new HashSet<>())
                            .add(res.resolverId()));
        }
        if (autoFulfil && t.clock.tick % 20 == 0) {
            fulfilAll();
        }
    }

    private void runUntil(BooleanSupplier done, int max) {
        for (int i = 0; i < max && !done.getAsBoolean(); i++) {
            tick();
        }
        assertFalse(colony.isSuspended(), "the colony threw");
        assertTrue(done.getAsBoolean(), () -> "not reached in " + max + " ticks; " + describe());
    }

    private String describe() {
        return "builders "
                + colony.buildings().all().stream()
                        .filter(b -> b.type().equals(ConstructionBuildingTypes.BUILDER))
                        .map(b -> b.position() + " L" + b.level() + " worker "
                                + worker(b)
                                        .map(c -> c.id() + " "
                                                + colony.citizens()
                                                        .aiState(c.id())
                                                        .orElse(null) + " inv "
                                                + c.inventory().contents())
                                        .orElse("none"))
                        .toList()
                + ", orders "
                + colony.work().ordered().stream()
                        .map(o -> o.id() + " " + o.type() + " " + o.claimedBy().orElse(null) + " " + o.stage() + "@"
                                + o.progressIndex())
                        .toList()
                + ", requests " + colony.requests().all() + ", player " + t.playerInventory.contents(alice)
                + ", tick " + t.clock.tick;
    }

    private Optional<CitizenData> worker(Building b) {
        return b.module(WorkerModule.class)
                .flatMap(w -> w.workers().stream().findFirst())
                .flatMap(colony.citizens()::get);
    }

    private Building hired(Building b) {
        runUntil(() -> worker(b).isPresent(), 10_000);
        return b;
    }

    private Building at(BlockPos pos) {
        return colony.buildings().at(pos).orElseThrow();
    }

    /** Every position of the plan's box is exactly as planned; the hut block stays; the rest is empty. */
    private void assertWorldIs(Blueprint bp, BlockPos hut) {
        Map<BlockPos, BlockState> planned = new HashMap<>();
        bp.entries()
                .forEach(e -> planned.put(
                        hut.offset(e.offset().x(), e.offset().y(), e.offset().z()), e.state()));
        planned.put(hut, state(HUT_BLOCK));
        for (int x = bp.min().x(); x <= bp.max().x(); x++) {
            for (int y = bp.min().y(); y <= bp.max().y(); y++) {
                for (int z = bp.min().z(); z <= bp.max().z(); z++) {
                    BlockPos p = hut.offset(x, y, z);
                    assertEquals(planned.get(p), t.blocks.blocks.get(p), p::toString);
                }
            }
        }
    }

    /** The builder's primary skill (the adverse skills lose some, as in MC). */
    private static double xpScore(CitizenData c) {
        return c.skills().level(Skill.Adaptability) * 1_000_000.0 + c.skills().experience(Skill.Adaptability);
    }

    private boolean logged(String type) {
        return colony.log().entries().stream().map(EventLog.Entry::type).anyMatch(type::equals);
    }

    private List<Request> liveStackRequests(ItemKey item) {
        return colony.requests().all().stream()
                .filter(r ->
                        r.requestable() instanceof StackRequest s && s.item().equals(item))
                .toList();
    }

    private static int count(Request r) {
        return r.deliverable().orElseThrow().count();
    }

    @Test
    void fullBuilderHutBuild() {
        stockPlayer(PLANKS_I, 64);
        stockPlayer(TORCH_I, 4);
        stockPlayer(CHEST_I, 2);
        Building hut = hired(placeHut(ConstructionBuildingTypes.BUILDER, HUT));
        CitizenData builder = worker(hut).orElseThrow();
        double xpBefore = xpScore(builder);
        // Something to clear: dirt inside the box, a torch where the floor goes.
        t.blocks.blocks.put(HUT.offset(1, 1, 1), state(DIRT));
        t.blocks.drops.put(HUT.offset(1, 1, 1), List.of(new ItemAmount(DIRT_I, 1)));
        t.blocks.blocks.put(HUT.offset(-1, 0, 0), state(TORCH));
        t.blocks.drops.put(HUT.offset(-1, 0, 0), List.of(new ItemAmount(TORCH_I, 1)));
        assertFalse(colony.contains(BEYOND));

        order(HUT, WorkOrderType.BUILD);
        WorkOrder o = colony.work().byBuilding(HUT).orElseThrow();
        runUntil(() -> o.stage() == Stage.SOLID && o.progressIndex() == 10, MAX_TICKS);

        // Mid-build, the hut's windows: 10 of the 26 items placed.
        manager.windows().openBuilding(alice, HUT);
        BuildingView view = (BuildingView) t.ui.shown.get(alice);
        BuilderResourcesView resources = view.tab(BuilderResourcesView.class).orElseThrow();
        BuilderResourcesView.Header header = resources.header().orElseThrow();
        assertEquals(1, header.step(), "CLEAR done, SOLID under way");
        assertEquals(100 - (int) (16 * 100.0 / 26), header.percent());
        Map<ItemKey, Integer> needed = new HashMap<>();
        resources.rows().forEach(r -> needed.put(r.item(), r.needed()));
        assertEquals(Map.of(PLANKS_I, 14, TORCH_I, 1, CHEST_I, 1), needed);
        BuildingView.OrderRow row = view.order().orElseThrow();
        assertEquals(Optional.of(builder.name()), row.builderName());
        assertEquals(header.percent(), row.percent());
        assertTrue(view.allowed().isEmpty());

        runUntil(() -> hut.level() == 1, MAX_TICKS);

        assertWorldIs(FakeBlueprints.hut(false), HUT);
        manager.windows().openBuilding(alice, HUT);
        BuildingView done = (BuildingView) t.ui.shown.get(alice);
        assertEquals(1, done.level());
        assertTrue(done.order().isEmpty());
        assertEquals(Set.of(WorkOrderType.UPGRADE, WorkOrderType.REPAIR, WorkOrderType.REMOVE), done.allowed());
        assertTrue(hut.isBuilt());
        assertFalse(hut.isDeconstructed());
        assertTrue(xpScore(builder) > xpBefore);
        assertTrue(logged("buildingBuilt"));
        assertTrue(colony.contains(BEYOND), "claims extended");
        assertTrue(
                seen.values().stream().anyMatch(r -> r.state() == RequestState.RECEIVED),
                () -> "no request received: " + seen.values());
        assertTrue(fulfils > 0);
        assertTrue(colony.work().byBuilding(HUT).isEmpty());
        assertTrue(hut.registeredBlocks().containers().contains(HUT.offset(0, 2, 0)), "the chest became a container");
        runUntil(() -> colony.requests().all().isEmpty(), 2000);

        manager.persistence().saveAll(); // the claims outlive a restart
        manager = newManager();
        manager.persistence().loadAll();
        colony = manager.byId(colony.id()).orElseThrow();
        watch(colony);
        assertTrue(colony.contains(BEYOND), "claims kept over a restart");
    }

    @Test
    void restartMidBuildResumesExactly() {
        stockPlayer(PLANKS_I, 12); // half: the rest is asked for again and stays open over the restart
        stockPlayer(TORCH_I, 1);
        stockPlayer(CHEST_I, 1);
        Building hut = hired(placeHut(ConstructionBuildingTypes.BUILDER, HUT));
        order(HUT, WorkOrderType.BUILD);
        WorkOrder o = colony.work().byBuilding(HUT).orElseThrow();
        runUntil(
                () -> o.stage() == Stage.SOLID
                        && playerHas(PLANKS_I) == 0
                        && o.progressIndex() == 12
                        && !liveStackRequests(PLANKS_I).isEmpty(),
                MAX_TICKS);
        tick(); // settle
        assertEquals(Stage.SOLID, o.stage());
        Set<RequestToken> openAtSave = new HashSet<>(
                colony.requests().all().stream().map(Request::token).toList());
        Set<ItemKey> itemsAtSave = new HashSet<>();
        for (Request r : colony.requests().all()) {
            if (r.requestable() instanceof StackRequest s) {
                itemsAtSave.add(s.item());
            }
        }
        assertTrue(itemsAtSave.contains(PLANKS_I));
        int colonyId = colony.id();
        CitizenData builder = worker(hut).orElseThrow();
        BodyId body = colony.citizens().bodyOf(builder.id()).orElseThrow();
        manager.persistence().saveAll();

        manager = newManager(); // the server restarts, same world
        manager.persistence().loadAll();
        colony = manager.byId(colonyId).orElseThrow();
        watch(colony);
        manager.onBodyLoaded(body, colonyId, builder.id()); // the builder's entity comes back with its chunk
        assertEquals(
                openAtSave,
                new HashSet<>(
                        colony.requests().all().stream().map(Request::token).toList()));
        Set<RequestToken> before = new HashSet<>(seen.keySet());
        stockPlayer(PLANKS_I, 64);
        Building reloadedHut = at(HUT);
        runUntil(() -> reloadedHut.level() == 1, MAX_TICKS);

        assertWorldIs(FakeBlueprints.hut(false), HUT);
        Map<BlockPos, Integer> placements = new HashMap<>();
        t.blocks.placed.forEach(p -> placements.merge(p, 1, Integer::sum));
        placements.forEach((p, n) -> assertEquals(1, n, () -> "placed twice: " + p));
        for (Map.Entry<RequestToken, Request> e : seen.entrySet()) {
            if (!before.contains(e.getKey()) && e.getValue().requestable() instanceof StackRequest s) {
                assertFalse(itemsAtSave.contains(s.item()), () -> "asked again after the restart: " + e.getValue());
            }
        }
        assertEquals(64 - 12, playerHas(PLANKS_I));
    }

    @Test
    void playerBreaksPlacedBlockItIsRebuilt() {
        stockPlayer(PLANKS_I, 64);
        stockPlayer(TORCH_I, 1);
        stockPlayer(CHEST_I, 1);
        Building hut = hired(placeHut(ConstructionBuildingTypes.BUILDER, HUT));
        order(HUT, WorkOrderType.BUILD);
        WorkOrder o = colony.work().byBuilding(HUT).orElseThrow();
        BlockPos first = HUT.offset(-1, 0, -1); // the first SOLID placement
        runUntil(() -> o.stage() == Stage.SOLID && o.progressIndex() > 3, MAX_TICKS);
        assertEquals(state(PLANKS), t.blocks.blocks.get(first));

        t.blocks.blocks.remove(first); // the owner breaks it
        runUntil(() -> hut.level() == 1, MAX_TICKS);

        assertWorldIs(FakeBlueprints.hut(false), HUT);
        assertEquals(2, t.blocks.placed.stream().filter(first::equals).count());
    }

    @Test
    void partialFulfilClosesAndBuilderRerequests() {
        stockPlayer(TORCH_I, 1);
        stockPlayer(CHEST_I, 1);
        Building hut = hired(placeHut(ConstructionBuildingTypes.BUILDER, HUT));
        order(HUT, WorkOrderType.BUILD);
        runUntil(() -> !liveStackRequests(PLANKS_I).isEmpty(), MAX_TICKS);
        Request first = liveStackRequests(PLANKS_I).get(0);
        int needed = count(first);
        assertEquals(24, needed); // every plank of the plan
        runUntil(
                () -> first.state() == RequestState.IN_PROGRESS
                        && colony.requests().resolverOf(first.token()).isPresent(),
                2000);

        autoFulfil = false;
        stockPlayer(PLANKS_I, needed / 2);
        assertTrue(manager.requestActions().fulfil(alice, colony.id(), first.token()));

        assertTrue(first.state().ordinal() >= RequestState.COMPLETED.ordinal());
        runUntil(() -> liveStackRequests(PLANKS_I).stream().anyMatch(r -> r != first), 20_000);
        assertTrue(colony.requests().get(first.token()).isEmpty(), "closed");
        assertEquals(RequestState.RECEIVED, first.state());
        List<Request> again = liveStackRequests(PLANKS_I);
        assertEquals(1, again.size());
        assertEquals(needed - needed / 2, count(again.get(0)));
        assertEquals(0, playerHas(PLANKS_I));

        autoFulfil = true;
        stockPlayer(PLANKS_I, 64);
        runUntil(() -> hut.level() == 1, MAX_TICKS);
        assertWorldIs(FakeBlueprints.hut(false), HUT);
    }

    /**
     * Controller note. Buckets [planks x18], [glass x10], 4 glass in the hut: the next bucket's glass is asked of the
     * building for what the hut lacks (min 1), which the hut serves; the player is only ever asked for the 6 missing
     * glass, once the builder took the hut's 4.
     */
    @Test
    void hutStockedNextBucketItemIsResolvedByBuildingNotPlayer() {
        t.catalog.maxStacks.put(PLANKS_I, 1);
        t.catalog.maxStacks.put(GLASS_I, 1);
        List<BlueprintEntry> entries = new ArrayList<>();
        for (int x = -28; x <= -1; x++) {
            entries.add(FakeBlueprints.entry(x, 0, 0, x <= -11 ? PLANKS : GLASS));
        }
        Blueprint bp = new Blueprint("line", entries, new BlockPos(-28, 0, 0), new BlockPos(0, 0, 0));
        blueprints.put(ConstructionBuildingTypes.BUILDER.id(), 1, bp);
        Building hut = hired(placeHut(ConstructionBuildingTypes.BUILDER, HUT));
        t.containers.containers.put(HUT, new LinkedHashMap<>(Map.of(GLASS_I, 4)));
        stockPlayer(PLANKS_I, 18);
        stockPlayer(GLASS_I, 6);
        order(HUT, WorkOrderType.BUILD);
        runUntil(() -> !glassRequests().isEmpty(), MAX_TICKS);
        Request first = glassRequests().get(0);
        assertEquals(new StackRequest(GLASS_I, 6, 1, true), first.requestable()); // 10 needed - 4 in the hut
        assertEquals(Set.of(hut.requesterId().value()), resolversSeen.get(first.token()));
        assertEquals(List.of(new ItemAmount(GLASS_I, 4)), first.deliveries());
        assertEquals(4, t.containers.count(List.of(HUT), GLASS_I)); // served in place: it stays in the hut

        runUntil(() -> hut.level() == 1, MAX_TICKS);

        List<Request> glass = glassRequests();
        assertTrue(glass.stream().allMatch(r -> count(r) == 6), glass::toString); // never the full 10
        List<Request> fromPlayer = glass.stream()
                .filter(r ->
                        !resolversSeen.get(r.token()).contains(hut.requesterId().value()))
                .toList();
        assertEquals(1, fromPlayer.size(), glass::toString); // once the builder took the hut's 4
        assertTrue(resolversSeen.get(fromPlayer.get(0).token()).contains(RetryingResolver.ID));
        assertEquals(0, playerHas(GLASS_I));
        assertWorldIs(bp, HUT);
    }

    private List<Request> glassRequests() {
        return seen.values().stream()
                .filter(r ->
                        r.requestable() instanceof StackRequest s && s.item().equals(GLASS_I))
                .toList();
    }

    @Test
    void upgradeThenRemove() {
        stockPlayer(PLANKS_I, 64);
        stockPlayer(GLASS_I, 2);
        stockPlayer(TORCH_I, 1);
        stockPlayer(CHEST_I, 1);
        Building hut = hired(placeHut(ConstructionBuildingTypes.BUILDER, HUT));
        order(HUT, WorkOrderType.BUILD);
        runUntil(() -> hut.level() == 1, MAX_TICKS);

        order(HUT, WorkOrderType.UPGRADE);
        runUntil(() -> hut.level() == 2, MAX_TICKS);
        assertWorldIs(FakeBlueprints.hut(true), HUT);
        assertTrue(logged("buildingUpgraded"));
        assertEquals(0, playerHas(GLASS_I));

        order(HUT, WorkOrderType.REMOVE);
        runUntil(hut::isDeconstructed, MAX_TICKS);
        assertEquals(2, hut.level());
        assertTrue(logged("buildingDeconstructed"));
        for (BlueprintEntry e : FakeBlueprints.hut(true).entries()) {
            BlockPos p = HUT.offset(e.offset().x(), e.offset().y(), e.offset().z());
            assertFalse(t.blocks.blocks.containsKey(p), p::toString);
        }
        assertEquals(state(HUT_BLOCK), t.blocks.blocks.get(HUT));
        assertFalse(
                hut.registeredBlocks().containers().contains(HUT.offset(0, 2, 0)), "the removed chest is no container");
        assertTrue(colony.work().byBuilding(HUT).isEmpty());

        CitizenData builder = worker(hut).orElseThrow();
        assertTrue(manager.huts().pickUp(alice, HUT, () -> true)); // the deconstructed hut goes back to the player
        assertTrue(colony.buildings().at(HUT).isEmpty());
        assertTrue(builder.job().isEmpty());
        assertEquals(null, builder.workBuilding());
        runUntil(
                () -> colony.citizens()
                        .aiState(builder.id())
                        .map(s -> s.name().equals("IDLE"))
                        .orElse(false),
                1000);
        assertTrue(colony.requests().all().isEmpty());
        assertTrue(colony.work().ordered().isEmpty());
    }

    @Test
    void twoBuildersTwoOrders() {
        stockPlayer(PLANKS_I, 200);
        stockPlayer(TORCH_I, 4);
        stockPlayer(CHEST_I, 4);
        BlockPos hutA = new BlockPos(40, 64, 0), hutB = new BlockPos(-40, 64, 0);
        BlockPos resA = new BlockPos(20, 64, 30), resB = new BlockPos(-20, 64, 30);
        Building a = hired(placeHut(ConstructionBuildingTypes.BUILDER, hutA));
        Building b = hired(placeHut(ConstructionBuildingTypes.BUILDER, hutB));
        assertNotEquals(worker(a).orElseThrow().id(), worker(b).orElseThrow().id());
        order(hutA, WorkOrderType.BUILD);
        order(hutB, WorkOrderType.BUILD);
        runUntil(() -> a.level() == 1 && b.level() == 1, MAX_TICKS);

        Building ra = placeHut(ConstructionBuildingTypes.RESIDENCE, resA);
        Building rb = placeHut(ConstructionBuildingTypes.RESIDENCE, resB);
        order(resA, WorkOrderType.BUILD);
        order(resB, WorkOrderType.BUILD);
        WorkOrder oa = colony.work().byBuilding(resA).orElseThrow();
        WorkOrder ob = colony.work().byBuilding(resB).orElseThrow();
        runUntil(() -> oa.claimedBy().isPresent() && ob.claimedBy().isPresent(), 1000);
        assertNotEquals(oa.claimedBy(), ob.claimedBy());
        runUntil(() -> ra.level() == 1 && rb.level() == 1, MAX_TICKS);
        assertWorldIs(FakeBlueprints.hut(false), resA);
        assertWorldIs(FakeBlueprints.hut(false), resB);
    }

    /**
     * 20 000 ticks from the order on: the plan's load and needs (20 000 entries), CLEAR over its 20 400 positions,
     * then placing from the hut's stock. Warm-up: the colony ticked through founding and hiring.
     */
    @Tag("perf")
    @Test
    void perfTwentyThousandBlockPlan() {
        blueprints.put(ConstructionBuildingTypes.BUILDER.id(), 1, FakeBlueprints.large());
        autoFulfil = false;
        BlockPos hutPos = new BlockPos(20, 64, 0);
        hired(placeHut(ConstructionBuildingTypes.BUILDER, hutPos));
        for (int i = 0; i < 2000; i++) {
            tick();
        }
        t.containers.containers.put(hutPos, new LinkedHashMap<>(Map.of(PLANKS_I, 20_000)));
        order(hutPos, WorkOrderType.BUILD);
        WorkOrder o = colony.work().byBuilding(hutPos).orElseThrow();

        int ticks = 20_000;
        long worst = 0;
        int slow = 0; // ticks over 5 ms
        long start = System.nanoTime();
        for (int i = 0; i < ticks; i++) {
            long s = System.nanoTime();
            t.clock.tick++;
            manager.tick();
            long took = System.nanoTime() - s;
            worst = Math.max(worst, took);
            if (took > 5_000_000L) {
                slow++;
            }
        }
        double avgMs = (System.nanoTime() - start) / 1e6 / ticks;
        int placed = t.blocks.placed.size();
        System.out.printf(
                "perf: %d ticks, avg %.4f ms, worst %.3f ms, %d over 5 ms, %d blocks placed, order at %s@%d%n",
                ticks, avgMs, worst / 1e6, slow, placed, o.stage(), o.progressIndex());
        assertFalse(colony.isSuspended());
        assertEquals(Stage.SOLID, o.stage());
        assertTrue(placed > 500, "the builder kept building: " + placed);
        assertTrue(avgMs < 2.0, "avg colony tick " + avgMs + " ms");
        assertTrue(slow <= 3, slow + " ticks over 5 ms"); // the plan's load, not a steady cost
    }

    @Test
    void midbuildFixtureLoadsAndCompletes() throws Exception {
        try (var in = getClass().getResourceAsStream("/fixtures/colony-v2-midbuild.json")) {
            Files.write(dir.resolve("colony-1.json"), in.readAllBytes());
        }
        // The world as the fixture left it: the site cleared, the first 12 SOLID blocks of the hut placed.
        Blueprint bp = FakeBlueprints.hut(false);
        StructurePlan plan = StructurePlan.build(bp, HUT, t.catalog);
        t.blocks.blocks.put(HUT, state(HUT_BLOCK));
        for (int i = 0; i < 12; i++) {
            t.blocks.blocks.put(
                    plan.solidPositions().get(i), plan.solidList().get(i).state());
        }
        manager = newManager();
        manager.persistence().loadAll();
        colony = manager.byId(1).orElseThrow();
        watch(colony);
        Building hut = at(HUT);
        assertEquals(0, hut.level());
        WorkOrder o = colony.work().byBuilding(HUT).orElseThrow();
        assertEquals(Stage.SOLID, o.stage());
        assertEquals(12, o.progressIndex());
        Request rest = colony.requests().all().iterator().next(); // the rest of the planks, with the retrying resolver
        assertEquals(1, colony.requests().all().size());
        assertEquals(new StackRequest(PLANKS_I, 12, 1, true), rest.requestable());
        assertEquals(1, worker(hut).orElseThrow().inventory().count(TORCH_I));
        stockPlayer(PLANKS_I, 12);

        runUntil(() -> hut.level() == 1, MAX_TICKS); // the builder's body respawns at its last position

        assertWorldIs(bp, HUT);
        assertEquals(0, playerHas(PLANKS_I));
        assertEquals(12 + 1 + 1, t.blocks.placed.size()); // the planks left, the chest, the torch
        assertEquals(RequestState.RECEIVED, rest.state());
        assertTrue(colony.contains(BEYOND));
    }
}
