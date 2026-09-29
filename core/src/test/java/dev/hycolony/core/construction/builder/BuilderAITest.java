package dev.hycolony.core.construction.builder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.BuildingType;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.Skill;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ColonyEvents;
import dev.hycolony.core.colony.ColonyManager;
import dev.hycolony.core.colony.EventLog;
import dev.hycolony.core.construction.blueprint.Blueprint;
import dev.hycolony.core.construction.blueprint.BlueprintEntry;
import dev.hycolony.core.construction.blueprint.BlueprintSource;
import dev.hycolony.core.construction.hut.ConstructionBuildingTypes;
import dev.hycolony.core.construction.resources.BuildingResourcesModule;
import dev.hycolony.core.construction.workorder.Stage;
import dev.hycolony.core.construction.workorder.WorkOrder;
import dev.hycolony.core.construction.workorder.WorkOrderRefusal;
import dev.hycolony.core.construction.workorder.WorkOrderType;
import dev.hycolony.core.job.JobAI;
import dev.hycolony.core.job.JobXp;
import dev.hycolony.core.job.WorkerModule;
import dev.hycolony.core.job.work.WorkerStock;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Either;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.item.BlockKey;
import dev.hycolony.core.kernel.item.BlockKind;
import dev.hycolony.core.kernel.item.BlockState;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.item.ToolInfo;
import dev.hycolony.core.kernel.item.ToolType;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.model.RequestState;
import dev.hycolony.core.request.model.RequestToken;
import dev.hycolony.core.request.model.StackRequest;
import dev.hycolony.core.request.model.ToolRequest;
import dev.hycolony.core.request.resolver.PlayerResolver;
import dev.hycolony.core.request.resolver.RetryingResolver;
import dev.hycolony.core.testing.TestContexts;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;

class BuilderAITest {
    private static final BlockPos HUT = new BlockPos(10, 64, 0);
    private static final BlockPos RES = new BlockPos(30, 64, 0);
    private static final BlockKey STONE = new BlockKey("stone");
    private static final BlockKey DIRT = new BlockKey("dirt");
    private static final BlockKey ORE = new BlockKey("ore");
    private static final BlockKey TORCH = new BlockKey("torch");
    private static final ItemKey STONE_I = new ItemKey("stone_item");
    private static final ItemKey DIRT_I = new ItemKey("dirt_item");
    private static final ItemKey ORE_I = new ItemKey("ore_item");
    private static final ItemKey TORCH_I = new ItemKey("torch_item");

    private final TestContexts t = new TestContexts();
    private final UUID alice = UUID.randomUUID();
    private final List<String> loads = new ArrayList<>();
    private Blueprint blueprint;
    private final ColonyManager manager;
    private final Colony colony;
    private final Building hut;
    private final CitizenData citizen;
    private final BodyId body;
    private final BuilderAI ai;

    BuilderAITest() {
        t.bodies.instant = true;
        t.blueprints = new BlueprintSource() {
            @Override
            public Optional<Blueprint> load(String style, String buildingTypeId, int level, int rotation) {
                loads.add(style + "/" + buildingTypeId + "/" + level);
                return Optional.ofNullable(blueprint);
            }

            @Override
            public List<String> styles() {
                return List.of("medieval");
            }
        };
        t.catalog.kinds.put(STONE, BlockKind.SOLID);
        t.catalog.kinds.put(DIRT, BlockKind.SOLID);
        t.catalog.kinds.put(ORE, BlockKind.SOLID);
        t.catalog.kinds.put(TORCH, BlockKind.NON_SOLID);
        t.catalog.itemForBlock.put(STONE, STONE_I);
        t.catalog.itemForBlock.put(TORCH, TORCH_I);
        t.catalog.ores.add(ORE);

        manager = new ColonyManager(t.context());
        manager.foundation().begin(alice, "Alice", new BlockPos(0, 64, 0), 0);
        colony = manager.foundation().confirm(alice, "A").orElseThrow();
        hut = hut(ConstructionBuildingTypes.BUILDER, HUT, 5);
        citizen = new CitizenData(1);
        colony.citizens().restore(citizen);
        assertTrue(hut.module(WorkerModule.class).orElseThrow().hire(colony, hut, citizen));
        body = t.bodies.existing(colony.id(), 1, Vec3.center(HUT));
        ai = new BuilderAI(BuilderContext.of(colony, citizen, body).orElseThrow());
        t.notifier.sent.clear();
    }

    @Test
    void aBuilderWithoutAHutIsIdle() {
        CitizenData homeless = new CitizenData(2);
        colony.citizens().restore(homeless);
        JobAI idle = new BuilderJob(homeless).createAI(colony, t.bodies.existing(colony.id(), 2, Vec3.center(HUT)));
        for (int i = 0; i < 20; i++) {
            idle.tick();
        }
        assertEquals("IDLE", idle.stateName());
        assertTrue(idle.canGoIdle());
    }

    private Building hut(BuildingType type, BlockPos pos, int level) {
        manager.huts().place(colony, type.id(), pos, 0);
        Building b = colony.buildings().at(pos).orElseThrow();
        b.setLevel(level);
        b.setBuilt(level > 0);
        return b;
    }

    private static BlueprintEntry entry(int x, int y, int z, BlockKey key) {
        return new BlueprintEntry(new BlockPos(x, y, z), new BlockState(key, 0), false);
    }

    /** Bounds = the entries' box, extended to the hut (0,0,0). */
    private static Blueprint bp(List<BlueprintEntry> entries) {
        int minX = 0, minY = 0, minZ = 0, maxX = 0, maxY = 0, maxZ = 0;
        for (BlueprintEntry e : entries) {
            minX = Math.min(minX, e.offset().x());
            maxX = Math.max(maxX, e.offset().x());
            minY = Math.min(minY, e.offset().y());
            maxY = Math.max(maxY, e.offset().y());
            minZ = Math.min(minZ, e.offset().z());
            maxZ = Math.max(maxZ, e.offset().z());
        }
        return new Blueprint("bp", entries, new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ));
    }

    private WorkOrder order(Building b, WorkOrderType type) {
        var r = colony.work().request(alice, b.position(), type, "", Optional.of(HUT));
        assertTrue(r instanceof Either.Left, () -> "refused: " + r);
        loads.clear();
        return ((Either.Left<WorkOrder, WorkOrderRefusal>) r).value();
    }

    /** One game tick: the clock advances with the AI (the walker's stuck handler measures time with it). */
    private void step() {
        t.clock.tick++;
        ai.tick();
    }

    private void tick(int n) {
        for (int i = 0; i < n; i++) {
            step();
        }
    }

    private void tickUntil(BooleanSupplier done, int max) {
        for (int i = 0; i < max && !done.getAsBoolean(); i++) {
            step();
        }
        assertTrue(done.getAsBoolean(), () -> "not reached; state " + ai.stateName());
        assertTrue(ai.lastError().isEmpty());
    }

    private boolean gone(WorkOrder o) {
        return colony.work().byId(o.id()).isEmpty();
    }

    private BuildingResourcesModule resources() {
        return hut.module(BuildingResourcesModule.class).orElseThrow();
    }

    private List<Request> builderRequests() {
        return colony.requests().byRequester(hut.requesterId());
    }

    private void give(ItemKey item, int n) {
        citizen.inventory().insert(new ItemAmount(item, n), t.catalog::maxStack);
    }

    private BlockPos at(int dx, int dy, int dz) {
        return RES.offset(dx, dy, dz);
    }

    @Test
    void idleWithoutOrderStaysIdle() {
        tick(400);
        assertEquals("IDLE", ai.stateName());
        assertEquals(0, resources().orderId());
        assertTrue(t.bodies.moves.isEmpty()); // already at its hut
        assertTrue(ai.lastError().isEmpty());
    }

    @Test
    void takesClaimedOrderAndLoadsStructure() {
        Building res = hut(ConstructionBuildingTypes.RESIDENCE, RES, 0);
        blueprint = bp(List.of(entry(1, 0, 0, STONE)));
        WorkOrder o = order(res, WorkOrderType.BUILD);

        tickUntil(() -> resources().orderId() == o.id(), 200);

        assertEquals(List.of("medieval/hycolony:residence/1"), loads);
        assertEquals(Stage.CLEAR, resources().stage());
        assertEquals(Map.of(STONE_I, 1), resources().currentBucket().orElseThrow());
        assertEquals("BUILDING_STEP", ai.stateName());
    }

    @Test
    void clearMinesNonAirKeepsDropsVoidsOres() {
        Building res = hut(ConstructionBuildingTypes.RESIDENCE, RES, 0);
        blueprint = bp(List.of(entry(1, 0, 0, STONE), entry(3, 1, 0, STONE)));
        t.blocks.blocks.put(at(1, 0, 0), new BlockState(STONE, 0)); // already as planned: kept
        t.blocks.blocks.put(at(2, 0, 0), new BlockState(DIRT, 0));
        t.blocks.drops.put(at(2, 0, 0), List.of(new ItemAmount(DIRT_I, 1)));
        t.blocks.blocks.put(at(1, 1, 0), new BlockState(ORE, 0));
        t.blocks.drops.put(at(1, 1, 0), List.of(new ItemAmount(ORE_I, 1)));
        WorkOrder o = order(res, WorkOrderType.BUILD);

        tickUntil(() -> o.stage() != Stage.CLEAR, 5000);

        assertFalse(t.blocks.blocks.containsKey(at(2, 0, 0)));
        assertFalse(t.blocks.blocks.containsKey(at(1, 1, 0)));
        assertEquals(new BlockState(STONE, 0), t.blocks.blocks.get(at(1, 0, 0)));
        assertEquals(1, citizen.inventory().count(DIRT_I));
        assertEquals(0, citizen.inventory().count(ORE_I));
        assertEquals(Stage.SOLID, o.stage());
    }

    @Test
    void clearRemovesUnwantedFluidsAndNeverTouchesUnbreakable() {
        BlockKey water = new BlockKey("~fluid:Water");
        BlockKey bedrock = new BlockKey("bedrock"); // also what a hut's filler cell reports
        t.catalog.kinds.put(water, BlockKind.FLUID);
        t.catalog.kinds.put(bedrock, BlockKind.UNBREAKABLE);
        Building res = hut(ConstructionBuildingTypes.RESIDENCE, RES, 0);
        blueprint = bp(List.of(entry(1, 0, 0, STONE), entry(2, 0, 0, water), entry(3, 1, 0, STONE)));
        t.blocks.blocks.put(at(1, 0, 0), new BlockState(water, 0)); // the plan wants stone: the water goes
        t.blocks.blocks.put(at(2, 0, 0), new BlockState(water, 0)); // as planned: kept
        t.blocks.blocks.put(at(3, 0, 0), new BlockState(bedrock, 0));
        t.blocks.blocks.put(at(0, 1, 0), new BlockState(water, 0)); // absent from the plan: goes too
        WorkOrder o = order(res, WorkOrderType.BUILD);

        tickUntil(() -> o.stage() != Stage.CLEAR, 5000);

        assertFalse(t.blocks.blocks.containsKey(at(1, 0, 0)));
        assertFalse(t.blocks.blocks.containsKey(at(0, 1, 0)));
        assertEquals(new BlockState(water, 0), t.blocks.blocks.get(at(2, 0, 0)));
        assertEquals(new BlockState(bedrock, 0), t.blocks.blocks.get(at(3, 0, 0)));
    }

    @Test
    void solidBeforeDecoBottomUp() {
        Building res = hut(ConstructionBuildingTypes.RESIDENCE, RES, 1);
        blueprint = bp(List.of(entry(1, 0, 0, TORCH), entry(2, 1, 0, STONE), entry(2, 0, 0, STONE)));
        give(STONE_I, 2);
        give(TORCH_I, 1);
        WorkOrder o = order(res, WorkOrderType.UPGRADE);

        tickUntil(() -> gone(o), 5000);

        assertEquals(List.of(at(2, 0, 0), at(2, 1, 0), at(1, 0, 0)), t.blocks.placed);
        assertEquals(0, citizen.inventory().count(STONE_I));
        assertEquals(0, citizen.inventory().count(TORCH_I));
    }

    @Test
    void skipsAlreadyCorrectBlocks() {
        Building res = hut(ConstructionBuildingTypes.RESIDENCE, RES, 1);
        blueprint = bp(List.of(entry(1, 0, 0, STONE), entry(2, 0, 0, STONE), entry(3, 0, 0, STONE)));
        t.blocks.blocks.put(at(2, 0, 0), new BlockState(STONE, 0));
        give(STONE_I, 2);
        WorkOrder o = order(res, WorkOrderType.UPGRADE);

        tickUntil(() -> gone(o), 5000);

        assertEquals(List.of(at(1, 0, 0), at(3, 0, 0)), t.blocks.placed);
        assertEquals(0, citizen.inventory().count(STONE_I));
    }

    /** 54 entries of stack size 1: buckets of 18 = [A x18], [B x18], [C x18]. */
    @Test
    void requestsOnlyMissingOfCurrentAndNextBucketOnce() {
        BlockKey a = new BlockKey("a"), b = new BlockKey("b"), c = new BlockKey("c");
        ItemKey ai1 = new ItemKey("a_i"), bi = new ItemKey("b_i"), ci = new ItemKey("c_i");
        for (BlockKey k : List.of(a, b, c)) {
            t.catalog.kinds.put(k, BlockKind.SOLID);
        }
        t.catalog.itemForBlock.put(a, ai1);
        t.catalog.itemForBlock.put(b, bi);
        t.catalog.itemForBlock.put(c, ci);
        for (ItemKey k : List.of(ai1, bi, ci)) {
            t.catalog.maxStacks.put(k, 1);
        }
        List<BlueprintEntry> entries = new ArrayList<>();
        for (int x = 1; x <= 9; x++) {
            for (int z = 0; z < 6; z++) {
                int idx = (x - 1) * 6 + z; // bottom-up order: y, x, z
                entries.add(entry(x, 0, z, idx < 18 ? a : idx < 36 ? b : c));
            }
        }
        Building res = hut(ConstructionBuildingTypes.RESIDENCE, RES, 1);
        blueprint = bp(entries);
        // Netted from the inventory: B in the hut would go to the building's resolver (min 1, as in MC).
        give(bi, 5);
        order(res, WorkOrderType.UPGRADE);

        tick(400);
        assertEquals("NEEDS_ITEM", ai.stateName());
        tick(2000);

        List<Request> reqs = builderRequests();
        assertEquals(2, reqs.size(), () -> "requests: " + reqs);
        Request a18 = reqs.stream()
                .filter(r -> r.requestable().equals(new StackRequest(ai1, 18, 1, true)))
                .findFirst()
                .orElseThrow();
        Request b13 = reqs.stream()
                .filter(r -> r.requestable().equals(new StackRequest(bi, 13, 1, true)))
                .findFirst()
                .orElseThrow();
        assertEquals(citizen.id(), a18.citizenId()); // needed now: sync
        assertEquals(-1, b13.citizenId()); // bucket request: the building's, async
        assertEquals("NEEDS_ITEM", ai.stateName());
        assertTrue(ai.lastError().isEmpty());
    }

    @Test
    void gathersFromHutBeforeRequesting() {
        Building res = hut(ConstructionBuildingTypes.RESIDENCE, RES, 1);
        blueprint = bp(List.of(entry(1, 0, 0, STONE), entry(2, 0, 0, STONE), entry(3, 0, 0, STONE)));
        t.containers.containers.put(HUT, new java.util.LinkedHashMap<>(Map.of(STONE_I, 3)));
        WorkOrder o = order(res, WorkOrderType.UPGRADE);

        for (int i = 0; i < 5000 && !gone(o); i++) {
            ai.tick();
            assertTrue(colony.requests().all().isEmpty(), "requested although the hut had the items");
        }

        assertTrue(gone(o));
        assertEquals(3, t.blocks.placed.size());
        assertEquals(0, t.containers.count(List.of(HUT), STONE_I));
    }

    @Test
    void freeOrderBuildsWithoutItemsNorRequests() {
        t.players.creativeOperators.add(alice);
        Building res = hut(ConstructionBuildingTypes.RESIDENCE, RES, 0);
        blueprint = bp(List.of(entry(1, 0, 0, STONE), entry(2, 0, 0, TORCH)));
        t.blocks.blocks.put(at(2, 0, 0), new BlockState(DIRT, 0)); // cleared as usual
        t.blocks.drops.put(at(2, 0, 0), List.of(new ItemAmount(DIRT_I, 1)));
        t.containers.containers.put(HUT, new java.util.LinkedHashMap<>(Map.of(STONE_I, 3)));
        give(STONE_I, 1);
        WorkOrder o = order(res, WorkOrderType.BUILD);
        assertTrue(o.free());

        for (int i = 0; i < 5000 && !gone(o); i++) {
            ai.tick();
            assertTrue(colony.requests().all().isEmpty(), "a free order requested items");
            assertFalse(ai.stateName().equals("GATHERING_REQUIRED_MATERIALS")
                    || ai.stateName().equals("NEEDS_ITEM"));
        }

        assertTrue(gone(o));
        assertTrue(ai.lastError().isEmpty());
        assertEquals(List.of(at(1, 0, 0), at(2, 0, 0)), t.blocks.placed);
        assertEquals(1, citizen.inventory().count(STONE_I)); // never used
        assertEquals(3, t.containers.count(List.of(HUT), STONE_I)); // never fetched
        assertEquals(1, citizen.inventory().count(DIRT_I)); // mining drops still kept
        assertEquals(1, res.level());
    }

    @Test
    void fullInventoryDumpsKeepingBucketItems() {
        Building res = hut(ConstructionBuildingTypes.RESIDENCE, RES, 1);
        List<BlueprintEntry> entries = new ArrayList<>();
        for (int x = 1; x <= 10; x++) {
            entries.add(entry(x, 0, 0, STONE));
        }
        blueprint = bp(entries);
        give(STONE_I, 15); // bucket: 10
        order(res, WorkOrderType.UPGRADE);
        tickUntil(() -> ai.stateName().equals("BUILDING_STEP"), 500);
        List<ItemKey> junk = new ArrayList<>();
        for (int i = 0; i < 26; i++) {
            ItemKey j = new ItemKey("junk" + i);
            junk.add(j);
            give(j, 1);
        }
        assertTrue(citizen.inventory().isFull());

        tickUntil(() -> ai.stateName().equals("INVENTORY_FULL"), 1000);
        tickUntil(() -> !ai.stateName().equals("INVENTORY_FULL"), 1000);

        for (ItemKey j : junk) {
            assertEquals(1, t.containers.count(List.of(HUT), j), j.id());
            assertEquals(0, citizen.inventory().count(j));
        }
        assertEquals(10, citizen.inventory().count(STONE_I)); // the bucket amount is kept (MC keepX)
        assertEquals(5, t.containers.count(List.of(HUT), STONE_I));
    }

    @Test
    void clearWithFullHutDoesNotLoop() {
        Building res = hut(ConstructionBuildingTypes.RESIDENCE, RES, 0);
        blueprint = new Blueprint("bp", List.of(entry(1, 0, 0, STONE)), new BlockPos(-3, 0, -3), new BlockPos(3, 2, 3));
        int n = 0;
        for (int x = -3; x <= 3; x++) {
            for (int y = 0; y <= 2; y++) {
                for (int z = -3; z <= 3; z++) {
                    if (x == 0 && y == 0 && z == 0) {
                        continue;
                    }
                    ItemKey drop = new ItemKey("debris" + n++);
                    t.catalog.maxStacks.put(drop, 1);
                    t.blocks.blocks.put(at(x, y, z), new BlockState(DIRT, 0));
                    t.blocks.drops.put(at(x, y, z), List.of(new ItemAmount(drop, 1)));
                }
            }
        }
        t.containers.full = true;
        WorkOrder o = order(res, WorkOrderType.BUILD);

        tick(10_000);

        assertTrue(ai.lastError().isEmpty());
        long mined = n
                - t.blocks.blocks.values().stream()
                        .filter(b -> b.key().equals(DIRT))
                        .count();
        assertTrue(mined > CitizenData.INVENTORY_SLOTS, "kept mining past a full inventory: " + mined);
        assertTrue(o.stage() != Stage.CLEAR || o.progressIndex() >= mined - 1);
        assertTrue(citizen.inventory().isFull());
        assertTrue(colony.log().entries().stream().map(EventLog.Entry::type).anyMatch("debrisLost"::equals));
    }

    @Test
    void missingToolCreatesToolRequest() {
        Building res = hut(ConstructionBuildingTypes.RESIDENCE, RES, 0);
        blueprint = bp(List.of(entry(1, 0, 0, STONE)));
        t.blocks.blocks.put(at(1, 0, 0), new BlockState(DIRT, 0));
        t.catalog.toolForBlock.put(DIRT, ToolType.SHOVEL);
        order(res, WorkOrderType.BUILD);

        tickUntil(() -> ai.stateName().equals("NEEDS_ITEM"), 1000);
        tick(500);

        List<Request> reqs = builderRequests();
        assertEquals(1, reqs.size());
        assertEquals(
                new ToolRequest(ToolType.SHOVEL, 0, Integer.MAX_VALUE),
                reqs.get(0).requestable(),
                "max level hut");
        assertEquals(citizen.id(), reqs.get(0).citizenId());
        assertTrue(t.blocks.blocks.containsKey(at(1, 0, 0))); // not mined without its tool
    }

    @Test
    void completionSetsLevelClaimsXpLogAndEvent() {
        BlockPos pos = new BlockPos(70, 64, 0); // claim cell 4: the edge of the initial territory
        Building res = hut(ConstructionBuildingTypes.RESIDENCE, pos, 0);
        blueprint = bp(List.of(entry(1, 0, 0, STONE)));
        give(STONE_I, 1);
        List<ColonyEvents.BuildingLevelChanged> events = new ArrayList<>();
        t.bus.subscribe(ColonyEvents.BuildingLevelChanged.class, events::add);
        BlockPos beyond = new BlockPos(85, 64, 0); // cell 5
        assertFalse(colony.contains(beyond));
        WorkOrder o = order(res, WorkOrderType.BUILD);
        t.notifier.sent.clear();

        tickUntil(() -> gone(o), 5000);

        assertEquals(1, res.level());
        assertTrue(res.isBuilt());
        assertFalse(res.isDeconstructed());
        assertTrue(colony.contains(beyond));
        CitizenData expected = new CitizenData(99);
        JobXp.award(expected, Skill.Adaptability, Skill.Athletics, 0.05, new JobXp.Levels(hut.level(), 0));
        JobXp.award(expected, Skill.Adaptability, Skill.Athletics, 8, new JobXp.Levels(hut.level(), 0));
        for (Skill s : Skill.values()) {
            assertEquals(expected.skills().level(s), citizen.skills().level(s), s.name());
            assertEquals(expected.skills().experience(s), citizen.skills().experience(s), 1e-9, s.name());
        }
        assertTrue(colony.log().entries().stream().anyMatch(e -> e.type().equals("buildingBuilt")));
        assertEquals(List.of(new ColonyEvents.BuildingLevelChanged(colony, res, 0, 1)), events);
        assertEquals(1, t.notifier.sent.size());
        assertEquals("hycolony.build.complete", t.notifier.sent.get(0).msg().key());
        assertEquals(
                List.of("%hycolony.ui.building.type.residence", "1"),
                t.notifier.sent.get(0).msg().params());
        tickUntil(() -> ai.stateName().equals("IDLE"), 200);
        assertEquals(0, resources().orderId());
    }

    @Test
    void removeOrderDeconstructsKeepingLevel() {
        Building res = hut(ConstructionBuildingTypes.RESIDENCE, RES, 2);
        blueprint = bp(List.of(entry(1, 0, 0, STONE), entry(1, 1, 0, STONE), entry(2, 0, 0, ORE)));
        for (BlockPos p : List.of(at(1, 0, 0), at(1, 1, 0))) {
            t.blocks.blocks.put(p, new BlockState(STONE, 0));
            t.blocks.drops.put(p, List.of(new ItemAmount(STONE_I, 1)));
        }
        t.blocks.blocks.put(at(2, 0, 0), new BlockState(ORE, 0));
        t.blocks.drops.put(at(2, 0, 0), List.of(new ItemAmount(ORE_I, 1)));
        WorkOrder o = order(res, WorkOrderType.REMOVE);

        tickUntil(() -> gone(o), 5000);

        assertEquals(List.of("medieval/hycolony:residence/2"), loads);
        assertFalse(t.blocks.blocks.containsKey(at(1, 0, 0)));
        assertFalse(t.blocks.blocks.containsKey(at(1, 1, 0)));
        assertEquals(2, res.level());
        assertTrue(res.isDeconstructed());
        assertEquals(2, citizen.inventory().count(STONE_I));
        assertFalse(t.blocks.blocks.containsKey(at(2, 0, 0)));
        assertEquals(0, citizen.inventory().count(ORE_I)); // ores are voided in every stage (MC mineBlock !isOre)
        assertTrue(builderRequests().isEmpty());
        assertTrue(colony.log().entries().stream().anyMatch(e -> e.type().equals("buildingDeconstructed")));
    }

    /** Simulation: a block the player broke behind the builder was never placed again. */
    @Test
    void blockBrokenBehindIsPlacedAgainBeforeCompletion() {
        Building res = hut(ConstructionBuildingTypes.RESIDENCE, RES, 1);
        blueprint =
                bp(List.of(entry(1, 0, 0, STONE), entry(2, 0, 0, STONE), entry(3, 0, 0, STONE), entry(1, 1, 0, TORCH)));
        give(STONE_I, 4);
        give(TORCH_I, 1);
        WorkOrder o = order(res, WorkOrderType.UPGRADE);
        tickUntil(() -> t.blocks.placed.size() == 2, 1000);

        t.blocks.blocks.remove(at(1, 0, 0)); // the player breaks it
        tickUntil(() -> gone(o), 5000);

        assertEquals(List.of(at(1, 0, 0), at(2, 0, 0), at(3, 0, 0), at(1, 1, 0), at(1, 0, 0)), t.blocks.placed);
        assertEquals(new BlockState(STONE, 0), t.blocks.blocks.get(at(1, 0, 0)));
        assertEquals(0, citizen.inventory().count(STONE_I));
        assertTrue(builderRequests().isEmpty());
    }

    /** Simulation: a REMOVE left the mined chest registered as the building's container. */
    @Test
    void minedContainerIsUnregistered() {
        Building res = hut(ConstructionBuildingTypes.RESIDENCE, RES, 1);
        BlockKey chest = new BlockKey("chest");
        t.catalog.kinds.put(chest, BlockKind.SOLID);
        blueprint = bp(List.of(new BlueprintEntry(new BlockPos(1, 0, 0), new BlockState(chest, 0), true)));
        t.blocks.blocks.put(at(1, 0, 0), new BlockState(chest, 0));
        res.registeredBlocks().addContainer(at(1, 0, 0));
        WorkOrder o = order(res, WorkOrderType.REMOVE);

        tickUntil(() -> gone(o), 5000);

        assertFalse(t.blocks.blocks.containsKey(at(1, 0, 0)));
        assertTrue(res.registeredBlocks().containers().isEmpty());
    }

    @Test
    void movesWhenNextBlockBeyondMineColoniesReach() {
        Building res = hut(ConstructionBuildingTypes.RESIDENCE, RES, 1);
        List<BlueprintEntry> entries = new ArrayList<>();
        for (int x = 1; x <= 30; x++) {
            entries.add(entry(x, 0, 0, STONE));
        }
        for (int x = -2; x <= 33; x++) {
            for (int z = -3; z <= 3; z++) {
                t.blocks.blocks.put(at(x, -1, z), new BlockState(DIRT, 0)); // the ground
            }
        }
        blueprint = bp(entries);
        give(STONE_I, 30);
        WorkOrder o = order(res, WorkOrderType.UPGRADE);

        tickUntil(() -> gone(o), 20_000);

        List<Vec3> workMoves =
                t.bodies.moves.stream().filter(v -> !v.equals(Vec3.center(HUT))).toList();
        // A line at x 31..60: the spot 2 outward is on the line (planned), so the builder stands 2 to the side, on
        // the ground, and takes a new spot once the block is more than 5 away (MC walkToConstructionSite:
        // getDistance2D, |dx| + |dz|, > 5): x 31, 35, ... 55. Past the line's end, x 61 outward is free again.
        List<Vec3> expected = new ArrayList<>();
        for (int x = 1; x <= 25; x += 4) {
            expected.add(Vec3.center(at(x, 0, 2)));
        }
        expected.add(Vec3.center(at(31, 0, 0)));
        assertEquals(expected, workMoves);
        assertEquals(30, t.blocks.placed.size());
    }

    @Test
    void workSpotNeverStandsInPlannedCells() {
        Building res = hut(ConstructionBuildingTypes.RESIDENCE, RES, 1);
        // at(3, 1, 0) is the outward spot of the first block: planned, so the builder stands beside instead.
        blueprint = bp(List.of(entry(1, 0, 0, STONE), entry(3, 1, 0, STONE)));
        give(STONE_I, 2);
        order(res, WorkOrderType.UPGRADE);

        tickUntil(() -> t.blocks.placed.size() == 1, 2000);

        List<Vec3> workMoves =
                t.bodies.moves.stream().filter(v -> !v.equals(Vec3.center(HUT))).toList();
        assertEquals(Vec3.center(at(1, 1, 2)), workMoves.get(0));
    }

    @Test
    void facesEachBlockBeforePlacingOrBreakingIt() {
        Building res = hut(ConstructionBuildingTypes.RESIDENCE, RES, 1);
        blueprint = bp(List.of(entry(1, 0, 0, STONE), entry(2, 0, 0, STONE)));
        t.blocks.blocks.put(at(1, 0, 0), new BlockState(DIRT, 0)); // cleared first
        give(STONE_I, 2);
        List<BlockPos> changed = new ArrayList<>();
        t.blocks.beforeChange = p -> {
            assertFalse(t.bodies.looks.isEmpty(), "changed a block without facing it");
            assertEquals(Vec3.middle(p), t.bodies.looks.get(t.bodies.looks.size() - 1));
            changed.add(p);
        };
        WorkOrder o = order(res, WorkOrderType.UPGRADE);

        tickUntil(() -> gone(o), 5000);

        assertEquals(List.of(at(1, 0, 0), at(1, 0, 0), at(2, 0, 0)), changed); // break, then two placements
    }

    @Test
    void cancelledOrderSendsBuilderIdle() {
        Building res = hut(ConstructionBuildingTypes.RESIDENCE, RES, 1);
        blueprint = bp(List.of(entry(1, 0, 0, STONE), entry(2, 0, 0, STONE)));
        give(STONE_I, 2);
        WorkOrder o = order(res, WorkOrderType.UPGRADE);
        tickUntil(() -> t.blocks.placed.size() == 1, 1000);

        colony.work().cancel(o.id()); // mid-build, during the place delay
        Stage stage = o.stage();
        int progress = o.progressIndex();
        tick(400);

        assertEquals(1, t.blocks.placed.size()); // never placed for the dead order
        assertEquals(stage, o.stage());
        assertEquals(progress, o.progressIndex());
        assertEquals("IDLE", ai.stateName());
        assertEquals(0, resources().orderId());
        assertTrue(builderRequests().isEmpty());
        assertTrue(ai.lastError().isEmpty());
    }

    /** Buckets [A x18], [A x18], [B x4]; then a player places every A block: the next need, B, is in no bucket. */
    @Test
    void recomputesNeedsWhenNeededItemNotInCurrentOrNextBucket() {
        BlockKey a = new BlockKey("a"), b = new BlockKey("b");
        ItemKey ai1 = new ItemKey("a_i"), bi = new ItemKey("b_i");
        t.catalog.kinds.put(a, BlockKind.SOLID);
        t.catalog.kinds.put(b, BlockKind.SOLID);
        t.catalog.itemForBlock.put(a, ai1);
        t.catalog.itemForBlock.put(b, bi);
        t.catalog.maxStacks.put(ai1, 1);
        t.catalog.maxStacks.put(bi, 1);
        List<BlueprintEntry> entries = new ArrayList<>();
        List<BlockPos> aPositions = new ArrayList<>();
        for (int x = 1; x <= 10; x++) {
            for (int z = 0; z < 4; z++) {
                boolean isA = (x - 1) * 4 + z < 36;
                entries.add(entry(x, 0, z, isA ? a : b));
                if (isA) {
                    aPositions.add(at(x, 0, z));
                }
            }
        }
        Building res = hut(ConstructionBuildingTypes.RESIDENCE, RES, 1);
        blueprint = bp(entries);
        order(res, WorkOrderType.UPGRADE);
        tickUntil(() -> ai.stateName().equals("BUILDING_STEP"), 500);
        assertEquals(Map.of(ai1, 18), resources().currentBucket().orElseThrow());
        aPositions.forEach(p -> t.blocks.blocks.put(p, new BlockState(a, 0)));

        tickUntil(() -> !builderRequests().isEmpty(), 1000);

        assertEquals(Map.of(bi, 4), resources().currentBucket().orElseThrow());
        assertEquals(
                List.of(new StackRequest(bi, 4, 1, true)),
                builderRequests().stream().map(Request::requestable).toList());
    }

    /** An UPGRADE of {@code n} stones with nothing in stock: the builder waits on its one (sync) request. */
    private Request waitingForStone(int n) {
        Building res = hut(ConstructionBuildingTypes.RESIDENCE, RES, 1);
        List<BlueprintEntry> entries = new ArrayList<>();
        for (int x = 1; x <= n; x++) {
            entries.add(entry(x, 0, 0, STONE));
        }
        blueprint = bp(entries);
        order(res, WorkOrderType.UPGRADE);
        tickUntil(() -> ai.stateName().equals("NEEDS_ITEM"), 1000);
        List<Request> reqs = builderRequests();
        assertEquals(1, reqs.size());
        assertEquals(citizen.id(), reqs.get(0).citizenId());
        return reqs.get(0);
    }

    @Test
    void bucketRequestsDoNotBlockBuilding() {
        BlockKey b = new BlockKey("b");
        ItemKey bi = new ItemKey("b_i");
        t.catalog.kinds.put(b, BlockKind.SOLID);
        t.catalog.itemForBlock.put(b, bi);
        t.catalog.maxStacks.put(STONE_I, 1);
        t.catalog.maxStacks.put(bi, 1);
        List<BlueprintEntry> entries = new ArrayList<>();
        for (int x = 1; x <= 6; x++) {
            for (int z = 0; z < 6; z++) {
                entries.add(entry(x, 0, z, (x - 1) * 6 + z < 18 ? STONE : b)); // buckets [stone x18], [b x18]
            }
        }
        Building res = hut(ConstructionBuildingTypes.RESIDENCE, RES, 1);
        blueprint = bp(entries);
        t.containers.containers.put(HUT, new java.util.LinkedHashMap<>(Map.of(STONE_I, 10)));
        order(res, WorkOrderType.UPGRADE);

        tickUntil(() -> t.blocks.placed.size() == 10, 5000);

        // Placing went on while the bucket requests (stone x8, b x18) stayed open and async.
        List<Request> reqs = builderRequests();
        assertEquals(2, reqs.size(), () -> "requests: " + reqs);
        assertTrue(reqs.stream().allMatch(r -> r.citizenId() == -1));
        assertTrue(reqs.stream().allMatch(r -> r.state().ordinal() < RequestState.COMPLETED.ordinal()));
        assertTrue(reqs.stream().anyMatch(r -> r.requestable().equals(new StackRequest(bi, 18, 1, true))));
    }

    @Test
    void existingAsyncRequestBecomesSyncWhenItemNeededNow() {
        Building res = hut(ConstructionBuildingTypes.RESIDENCE, RES, 1);
        blueprint = bp(List.of(entry(1, 0, 0, STONE), entry(2, 0, 0, STONE), entry(3, 0, 0, STONE)));
        var token = colony.requests().createAndAssign(hut, new StackRequest(STONE_I, 3, 1, true), -1);
        order(res, WorkOrderType.UPGRADE);

        tickUntil(() -> ai.stateName().equals("NEEDS_ITEM"), 1000);

        List<Request> reqs = builderRequests();
        assertEquals(1, reqs.size());
        assertEquals(token, reqs.get(0).token()); // moved to the builder, not duplicated
        assertEquals(citizen.id(), reqs.get(0).citizenId());
    }

    @Test
    void completedAsyncRequestsAreMarkedReceived() {
        Building res = hut(ConstructionBuildingTypes.RESIDENCE, RES, 1);
        blueprint = bp(List.of(entry(1, 0, 0, STONE), entry(2, 0, 0, STONE), entry(3, 0, 0, STONE)));
        t.containers.containers.put(HUT, new java.util.LinkedHashMap<>(Map.of(STONE_I, 3)));
        var token = colony.requests().createAndAssign(hut, new StackRequest(STONE_I, 3, 1, true), -1);
        assertEquals(
                RequestState.COMPLETED,
                colony.requests().get(token).orElseThrow().state()); // hut stock
        WorkOrder o = order(res, WorkOrderType.UPGRADE);

        tickUntil(() -> !t.blocks.placed.isEmpty(), 2000);

        assertTrue(colony.requests().get(token).isEmpty(), "still pending: blocks re-requesting the item");
        tickUntil(() -> gone(o), 2000);
        assertEquals(3, t.blocks.placed.size());
    }

    @Test
    void firedBuilderRequestsCancelledAndReplacementRequestsAgain() {
        Request first = waitingForStone(2);

        hut.module(WorkerModule.class).orElseThrow().fire(colony, hut, citizen.id());

        assertTrue(colony.requests().get(first.token()).isEmpty());
        CitizenData next = new CitizenData(2);
        colony.citizens().restore(next);
        assertTrue(hut.module(WorkerModule.class).orElseThrow().hire(colony, hut, next));
        BuilderAI replacement =
                new BuilderAI(BuilderContext.of(colony, next, t.bodies.existing(colony.id(), 2, Vec3.center(HUT)))
                        .orElseThrow());
        for (int i = 0; i < 1000 && builderRequests().isEmpty(); i++) {
            replacement.tick();
        }
        List<Request> reqs = builderRequests();
        assertEquals(1, reqs.size());
        assertEquals(next.id(), reqs.get(0).citizenId());
        for (int i = 0; i < 100 && !replacement.stateName().equals("NEEDS_ITEM"); i++) {
            replacement.tick();
        }
        assertEquals("NEEDS_ITEM", replacement.stateName());
    }

    @Test
    void completedRequestPickedUpFromHutThenReceivedAndBuildResumes() {
        Request r = waitingForStone(2);
        t.containers.containers.put(HUT, new java.util.LinkedHashMap<>(Map.of(STONE_I, 2))); // "Ajouter"
        colony.requests().overrule(r.token(), List.of(new ItemAmount(STONE_I, 2)));

        tickUntil(() -> colony.requests().get(r.token()).isEmpty(), 1000);

        assertEquals(2, citizen.inventory().count(STONE_I) + t.blocks.placed.size());
        assertEquals(0, t.containers.count(List.of(HUT), STONE_I));
        tickUntil(() -> t.blocks.placed.size() == 2, 2000);
    }

    @Test
    void missingDeliveryIsRequestedAgainForMissingCount() {
        Request r = waitingForStone(2);
        t.containers.containers.put(HUT, new java.util.LinkedHashMap<>(Map.of(STONE_I, 1))); // one taken meanwhile
        colony.requests().overrule(r.token(), List.of(new ItemAmount(STONE_I, 2)));

        tickUntil(() -> colony.requests().get(r.token()).isEmpty(), 1000);

        assertEquals(1, citizen.inventory().count(STONE_I));
        List<Request> reqs = builderRequests();
        assertEquals(1, reqs.size());
        assertEquals(new StackRequest(STONE_I, 1, 1, true), reqs.get(0).requestable());
        assertEquals(citizen.id(), reqs.get(0).citizenId());
    }

    @Test
    void overruledDeliveryAlreadyInInventoryNotExtracted() {
        Request r = waitingForStone(2);
        give(STONE_I, 2); // "Fournir" hands the items to the citizen
        t.containers.containers.put(HUT, new java.util.LinkedHashMap<>(Map.of(STONE_I, 5)));
        colony.requests().overrule(r.token(), List.of(new ItemAmount(STONE_I, 2)), true);

        tickUntil(() -> colony.requests().get(r.token()).isEmpty(), 1000);

        assertEquals(5, t.containers.count(List.of(HUT), STONE_I));
        assertEquals(2, citizen.inventory().count(STONE_I));
    }

    @Test
    void newAiResumesFromSavedIndexWithoutReplacing() {
        Building res = hut(ConstructionBuildingTypes.RESIDENCE, RES, 1);
        blueprint = bp(List.of(entry(1, 0, 0, STONE), entry(2, 0, 0, STONE), entry(3, 0, 0, STONE)));
        give(STONE_I, 4);
        WorkOrder o = order(res, WorkOrderType.UPGRADE);
        tickUntil(() -> t.blocks.placed.size() == 1, 1000);
        assertEquals(1, o.progressIndex());
        t.blocks.blocks.remove(at(1, 0, 0)); // gone again: a restart from 0 would place it first

        BuilderAI fresh =
                new BuilderAI(BuilderContext.of(colony, citizen, body).orElseThrow()); // e.g. after a server restart
        for (int i = 0; i < 5000 && !gone(o); i++) {
            fresh.tick();
        }

        assertTrue(gone(o));
        // Resumed at index 1; the final check places the broken block again, last.
        assertEquals(List.of(at(1, 0, 0), at(2, 0, 0), at(3, 0, 0), at(1, 0, 0)), t.blocks.placed);
    }

    @Test
    void oneToolPerTypeIsKeptSoANeededToolFitsAgain() {
        Building res = hut(ConstructionBuildingTypes.RESIDENCE, RES, 0);
        blueprint = bp(List.of(entry(1, 0, 0, STONE)));
        t.blocks.blocks.put(at(1, 0, 0), new BlockState(DIRT, 0));
        t.catalog.toolForBlock.put(DIRT, ToolType.SHOVEL);
        ItemKey shovel = new ItemKey("shovel");
        t.catalog.tools.put(shovel, new ToolInfo(ToolType.SHOVEL, 0, 2f));
        t.containers.containers.put(HUT, new java.util.LinkedHashMap<>(Map.of(shovel, 1)));
        for (int i = 0; i < CitizenData.INVENTORY_SLOTS; i++) {
            ItemKey pick = new ItemKey("pickaxe" + i);
            t.catalog.tools.put(pick, new ToolInfo(ToolType.PICKAXE, 0, 2f));
            give(pick, 1);
        }
        WorkOrder o = order(res, WorkOrderType.BUILD);

        tickUntil(() -> o.stage() != Stage.CLEAR, 5000);

        assertFalse(t.blocks.blocks.containsKey(at(1, 0, 0)));
        assertEquals(1, citizen.inventory().count(shovel));
        assertTrue(builderRequests().stream().noneMatch(r -> r.requestable() instanceof ToolRequest));
    }

    /** MC IBuilderUndestroyable: another hut standing where the plan wants a block is neither mined nor built over. */
    @Test
    void builderNeverMinesAnotherHut() {
        Building res = hut(ConstructionBuildingTypes.RESIDENCE, RES, 1);
        BlockPos other = at(2, 0, 0);
        hut(ConstructionBuildingTypes.RESIDENCE, other, 1);
        t.blocks.blocks.put(other, new BlockState(DIRT, 0)); // the other hut's block
        blueprint = bp(List.of(entry(1, 0, 0, STONE), entry(2, 0, 0, STONE), entry(3, 0, 0, STONE)));
        give(STONE_I, 3);
        WorkOrder o = order(res, WorkOrderType.UPGRADE);

        tickUntil(() -> gone(o), 5000);

        assertEquals(new BlockState(DIRT, 0), t.blocks.blocks.get(other));
        assertEquals(List.of(at(1, 0, 0), at(3, 0, 0)), t.blocks.placed);
    }

    /** Three dirt blocks in the way of the plan, all to dig with a shovel. */
    private ItemKey shovelWork(int durability) {
        Building res = hut(ConstructionBuildingTypes.RESIDENCE, RES, 0);
        blueprint = bp(List.of(entry(3, 0, 0, STONE)));
        for (int x = 1; x <= 3; x++) {
            t.blocks.blocks.put(at(x, 0, 0), new BlockState(DIRT, 0));
        }
        t.catalog.toolForBlock.put(DIRT, ToolType.SHOVEL);
        ItemKey shovel = new ItemKey("shovel");
        t.catalog.tools.put(shovel, new ToolInfo(ToolType.SHOVEL, 0, 2f));
        t.catalog.durability.put(shovel, durability);
        t.catalog.maxStacks.put(shovel, 1);
        order(res, WorkOrderType.BUILD);
        return shovel;
    }

    private long dirtLeft() {
        return t.blocks.blocks.values().stream()
                .filter(b -> b.key().equals(DIRT))
                .count();
    }

    /** MC CitizenItemUtils.damageItemInHand: 1 per block; the tool breaks at its durability, then another is asked. */
    @Test
    void aToolBreaksExactlyAtItsDurabilityAndTheBuilderRequestsANewOne() {
        ItemKey shovel = shovelWork(2);
        give(shovel, 1);

        tickUntil(() -> dirtLeft() == 2, 5000);
        assertEquals(List.of(new ItemAmount(shovel, 1, 1)), citizen.inventory().contents());
        tickUntil(() -> builderRequests().stream().anyMatch(r -> r.requestable() instanceof ToolRequest), 5000);

        assertEquals(0, citizen.inventory().count(shovel)); // broken after its 2 uses
        assertEquals(1, dirtLeft());
    }

    @Test
    void fiveFreshToolsWearOneByOne() {
        ItemKey shovel = shovelWork(2);
        give(shovel, 5);

        tickUntil(() -> dirtLeft() == 0, 5000);

        // 3 blocks: the first shovel broke after 2, the next one took the third.
        assertEquals(4, citizen.inventory().count(shovel));
        assertEquals(
                List.of(new ItemAmount(shovel, 1, 1), new ItemAmount(shovel, 1), new ItemAmount(shovel, 1)),
                citizen.inventory().contents().subList(0, 3));
    }

    /** A tool Hytale broke (durability 0) is worn out: the builder does not dig with it but asks for another. */
    @Test
    void aWornOutToolIsNeverUsed() {
        ItemKey shovel = shovelWork(2);
        citizen.inventory().set(0, java.util.Optional.of(new ItemAmount(shovel, 1, 2)));

        tickUntil(() -> builderRequests().stream().anyMatch(r -> r.requestable() instanceof ToolRequest), 5000);

        assertEquals(3, dirtLeft());
    }

    /** MC: a broken tool no longer exists; the builder takes the good shovel from its hut, not the broken one. */
    @Test
    void aBrokenToolInTheHutIsSkippedAndTheGoodOneTaken() {
        ItemKey shovel = shovelWork(2);
        t.containers.worn.put(HUT, new ArrayList<>(List.of(new ItemAmount(shovel, 1, 2))));
        t.containers.containers.put(HUT, new java.util.LinkedHashMap<>(Map.of(shovel, 1)));

        tickUntil(() -> dirtLeft() == 2, 5000);

        assertEquals(List.of(new ItemAmount(shovel, 1, 2)), t.containers.stacks(HUT));
        assertEquals(List.of(new ItemAmount(shovel, 1, 1)), citizen.inventory().contents());
    }

    /** A broken tool alone in the hut does not resolve the builder's tool request from the hut. */
    @Test
    void aBrokenToolInTheHutNeverResolvesAToolRequest() {
        ItemKey shovel = shovelWork(2);
        t.containers.worn.put(HUT, new ArrayList<>(List.of(new ItemAmount(shovel, 1, 2))));

        tickUntil(() -> builderRequests().stream().anyMatch(r -> r.requestable() instanceof ToolRequest), 5000);
        tick(100);

        Request r = builderRequests().stream()
                .filter(q -> q.requestable() instanceof ToolRequest)
                .findFirst()
                .orElseThrow();
        assertNotEquals(
                hut.requesterId().value(),
                colony.requests().resolverOf(r.token()).orElseThrow().resolverId());
        assertEquals(3, dirtLeft());
        assertEquals(List.of(new ItemAmount(shovel, 1, 2)), t.containers.stacks(HUT));
    }

    @Test
    void mineWithoutAnyRequestIsEmpty() {
        assertTrue(new BuilderRequests(
                        colony, citizen, hut, new WorkerStock(colony, citizen, hut, BuilderContext.ACTIONS_UNTIL_DUMP))
                .mine()
                .isEmpty());
    }

    @Test
    void directDeliveryPartlyUsedDoesNotTakeHutStock() {
        Request r = waitingForStone(2);
        t.containers.containers.put(HUT, new java.util.LinkedHashMap<>(Map.of(STONE_I, 5))); // reserved elsewhere
        t.playerInventory.give(alice, new ItemAmount(STONE_I, 2));
        assertTrue(
                manager.requestActions().fulfil(alice, colony.id(), r.token())); // "Fournir": straight to the citizen
        citizen.inventory().extract(STONE_I, 1); // one used before the pick-up

        tickUntil(() -> colony.requests().get(r.token()).isEmpty(), 1000);

        assertEquals(5, t.containers.count(List.of(HUT), STONE_I));
        assertEquals(1, citizen.inventory().count(STONE_I));
    }

    /** The final walk only refills air: a block the player changed (not broken) stays. */
    @Test
    void verificationPassOnlyRefillsAir() {
        Building res = hut(ConstructionBuildingTypes.RESIDENCE, RES, 1);
        blueprint =
                bp(List.of(entry(1, 0, 0, STONE), entry(2, 0, 0, STONE), entry(3, 0, 0, STONE), entry(1, 1, 0, TORCH)));
        give(STONE_I, 4);
        give(TORCH_I, 1);
        WorkOrder o = order(res, WorkOrderType.UPGRADE);
        tickUntil(() -> t.blocks.placed.size() == 2, 1000);

        t.blocks.blocks.put(at(1, 0, 0), new BlockState(DIRT, 0)); // the player swaps it
        tickUntil(() -> gone(o), 5000);

        assertEquals(List.of(at(1, 0, 0), at(2, 0, 0), at(3, 0, 0), at(1, 1, 0)), t.blocks.placed);
        assertEquals(new BlockState(DIRT, 0), t.blocks.blocks.get(at(1, 0, 0)));
    }

    /** Only a player can provide it now: the field bug's state, where no container event ever comes. */
    private void toPlayer(Request r) {
        colony.requests().reassign(r.token(), Set.of(RetryingResolver.ID));
        assertEquals(
                PlayerResolver.ID,
                colony.requests().resolverOf(r.token()).orElseThrow().resolverId());
    }

    @Test
    void waitingBuilderTakesToolPlacedInHutAndResumes() {
        ItemKey shovel = new ItemKey("shovel");
        t.catalog.tools.put(shovel, new ToolInfo(ToolType.SHOVEL, 0, 1f));
        Building res = hut(ConstructionBuildingTypes.RESIDENCE, RES, 0);
        blueprint = bp(List.of(entry(1, 0, 0, STONE)));
        t.blocks.blocks.put(at(1, 0, 0), new BlockState(DIRT, 0));
        t.catalog.toolForBlock.put(DIRT, ToolType.SHOVEL);
        give(STONE_I, 1);
        WorkOrder o = order(res, WorkOrderType.BUILD);
        tickUntil(() -> ai.stateName().equals("NEEDS_ITEM"), 1000);
        Request r = builderRequests().get(0);
        assertTrue(r.requestable() instanceof ToolRequest);
        toPlayer(r);
        tick(200);
        assertEquals("NEEDS_ITEM", ai.stateName(), "nothing in the hut yet");

        t.containers.containers.put(HUT, new java.util.LinkedHashMap<>(Map.of(shovel, 1))); // no container event

        tickUntil(() -> gone(o), 5000);
        assertTrue(colony.requests().get(r.token()).isEmpty(), "closed, not leaked");
        assertTrue(builderRequests().isEmpty());
        assertEquals(1, citizen.inventory().count(shovel));
        assertEquals(0, t.containers.count(List.of(HUT), shovel));
        assertEquals(new BlockState(STONE, 0), t.blocks.blocks.get(at(1, 0, 0)));
    }

    @Test
    void builderToolRequestUsesMaxEquipmentLevel() {
        ItemKey stoneShovel = new ItemKey("stone_shovel");
        t.catalog.tools.put(stoneShovel, new ToolInfo(ToolType.SHOVEL, 1, 1f));
        Building res = hut(ConstructionBuildingTypes.RESIDENCE, RES, 0);
        blueprint = bp(List.of(entry(1, 0, 0, STONE)));
        t.blocks.blocks.put(at(1, 0, 0), new BlockState(DIRT, 0));
        t.catalog.toolForBlock.put(DIRT, ToolType.SHOVEL);
        give(STONE_I, 1);
        WorkOrder o = order(res, WorkOrderType.BUILD);
        hut.setLevel(0); // after the order: a level 0 builder is not offered one
        tickUntil(() -> ai.stateName().equals("NEEDS_ITEM"), 1000);
        Request r = builderRequests().get(0);
        assertEquals(new ToolRequest(ToolType.SHOVEL, 0, 1), r.requestable(), "MC BASIC_TOOL_LEVEL at hut level 0");
        toPlayer(r);

        t.containers.containers.put(HUT, new java.util.LinkedHashMap<>(Map.of(stoneShovel, 1)));

        tickUntil(() -> gone(o), 5000);
        assertEquals(1, citizen.inventory().count(stoneShovel), "the level 0 hut uses the stone tool it asked for");
    }

    @Test
    void waitingBuilderTakesStackPlacedInHutAndResumes() {
        Request r = waitingForStone(2);
        toPlayer(r);
        t.containers.containers.put(HUT, new java.util.LinkedHashMap<>(Map.of(STONE_I, 2))); // no container event

        tickUntil(() -> t.blocks.placed.size() == 2, 3000);
        assertTrue(colony.requests().get(r.token()).isEmpty(), "closed, not leaked");
        assertEquals(0, t.containers.count(List.of(HUT), STONE_I));
    }

    @Test
    void waitingBuilderIgnoresToolAboveTheRequestedLevel() {
        ItemKey iron = new ItemKey("iron_shovel");
        t.catalog.tools.put(iron, new ToolInfo(ToolType.SHOVEL, 2, 1f));
        Building res = hut(ConstructionBuildingTypes.RESIDENCE, RES, 0);
        blueprint = bp(List.of(entry(1, 0, 0, STONE)));
        t.blocks.blocks.put(at(1, 0, 0), new BlockState(DIRT, 0));
        t.catalog.toolForBlock.put(DIRT, ToolType.SHOVEL);
        hut.setLevel(1);
        order(res, WorkOrderType.BUILD);
        tickUntil(() -> ai.stateName().equals("NEEDS_ITEM"), 1000);
        Request r = builderRequests().get(0);
        toPlayer(r);
        t.containers.containers.put(HUT, new java.util.LinkedHashMap<>(Map.of(iron, 1)));

        tick(1000);
        assertEquals("NEEDS_ITEM", ai.stateName());
        assertEquals(1, t.containers.count(List.of(HUT), iron));
        assertEquals(
                RequestState.IN_PROGRESS,
                colony.requests().get(r.token()).orElseThrow().state());
    }

    @Test
    void waitingBuilderLeavesHutStockReservedForAnotherRequest() {
        Request r = waitingForStone(2);
        toPlayer(r);
        t.containers.containers.put(HUT, new java.util.LinkedHashMap<>(Map.of(STONE_I, 2))); // no container event
        // Another worker of the hut asked for the same stone first: the hut's resolver reserved both for it.
        RequestToken other = colony.requests().createAndAssign(hut, new StackRequest(STONE_I, 2, 2, true), 99);
        tick(20);
        assertEquals(
                List.of(new ItemAmount(STONE_I, 2)),
                colony.requests().get(other).orElseThrow().deliveries());

        tick(1000);
        assertEquals("NEEDS_ITEM", ai.stateName());
        assertEquals(
                RequestState.IN_PROGRESS,
                colony.requests().get(r.token()).orElseThrow().state());
        assertEquals(2, t.containers.count(List.of(HUT), STONE_I));
        assertEquals(0, t.blocks.placed.size());
    }

    @Test
    void builderWhoseNavNeverEndsIsUnstuckAndBuilds() {
        Building res = hut(ConstructionBuildingTypes.RESIDENCE, RES, 1);
        blueprint = bp(List.of(entry(1, 0, 0, STONE)));
        give(STONE_I, 1);
        t.bodies.frozen = true; // moveTo never moves the body, navStatus stays MOVING
        WorkOrder o = order(res, WorkOrderType.UPGRADE);

        tickUntil(() -> gone(o), 5000);

        assertEquals(List.of(Vec3.center(at(3, 1, 0))), t.bodies.teleports, "repathed, then teleported to the spot");
        assertEquals(List.of(at(1, 0, 0)), t.blocks.placed);
    }

    @Test
    void describesWhatItIsDoing() {
        Request r = waitingForStone(2);
        assertEquals(Optional.of(dev.hycolony.core.kernel.port.Msg.of("hycolony.ai.builder.waiting")), ai.describe());

        give(STONE_I, 2);
        colony.requests().overrule(r.token(), List.of(new ItemAmount(STONE_I, 2)), true);
        tickUntil(() -> t.blocks.placed.size() == 1, 2000);
        assertEquals(
                Optional.of(dev.hycolony.core.kernel.port.Msg.of(
                        "hycolony.ai.builder.placing", "%hycolony.ui.stage.solid", "1", STONE_I.id())),
                ai.describe());
    }
}
