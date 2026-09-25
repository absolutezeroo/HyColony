package dev.hycolony.core.construction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.BuildingType;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.Skill;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ColonyEvents;
import dev.hycolony.core.colony.ColonyManager;
import dev.hycolony.core.colony.EventLog;
import dev.hycolony.core.job.WorkerModule;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Either;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.item.BlockKey;
import dev.hycolony.core.kernel.item.BlockKind;
import dev.hycolony.core.kernel.item.BlockState;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.item.ToolType;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.request.Deliverable;
import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.StackRequest;
import dev.hycolony.core.request.ToolRequest;
import dev.hycolony.core.testing.TestContexts;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
            public List<String> styles() { return List.of("medieval"); }
        };
        t.catalog.kinds.put(STONE, BlockKind.SOLID);
        t.catalog.kinds.put(DIRT, BlockKind.SOLID);
        t.catalog.kinds.put(ORE, BlockKind.SOLID);
        t.catalog.kinds.put(TORCH, BlockKind.NON_SOLID);
        t.catalog.itemForBlock.put(STONE, STONE_I);
        t.catalog.itemForBlock.put(TORCH, TORCH_I);
        t.catalog.ores.add(ORE);

        manager = new ColonyManager(t.context());
        manager.beginFoundation(alice, "Alice", new BlockPos(0, 64, 0), 0);
        colony = manager.confirmFoundation(alice, "A").orElseThrow();
        hut = hut(ConstructionBuildingTypes.BUILDER, HUT, 5);
        citizen = new CitizenData(1);
        colony.citizens().restore(citizen);
        assertTrue(hut.module(WorkerModule.class).orElseThrow().hire(colony, hut, citizen));
        body = t.bodies.existing(colony.id(), 1, Vec3.center(HUT));
        ai = new BuilderAI(colony, citizen, body);
        t.notifier.sent.clear();
    }

    // ---- helpers ----

    private Building hut(BuildingType type, BlockPos pos, int level) {
        manager.placeHut(colony, type.id(), pos, 0);
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
            minX = Math.min(minX, e.offset().x()); maxX = Math.max(maxX, e.offset().x());
            minY = Math.min(minY, e.offset().y()); maxY = Math.max(maxY, e.offset().y());
            minZ = Math.min(minZ, e.offset().z()); maxZ = Math.max(maxZ, e.offset().z());
        }
        return new Blueprint("bp", entries, new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ));
    }

    private WorkOrder order(Building b, WorkOrderType type) {
        var r = colony.work().request(alice, b.position(), type, "", Optional.of(HUT));
        assertTrue(r instanceof Either.Left, () -> "refused: " + r);
        loads.clear();
        return ((Either.Left<WorkOrder, WorkOrderRefusal>) r).value();
    }

    private void tick(int n) {
        for (int i = 0; i < n; i++) {
            ai.tick();
        }
    }

    private void tickUntil(BooleanSupplier done, int max) {
        for (int i = 0; i < max && !done.getAsBoolean(); i++) {
            ai.tick();
        }
        assertTrue(done.getAsBoolean(), () -> "not reached; state " + ai.stateName());
        assertNull(ai.lastError);
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

    // ---- tests ----

    @Test
    void idleWithoutOrderStaysIdle() {
        tick(400);
        assertEquals("IDLE", ai.stateName());
        assertEquals(0, resources().orderId());
        assertTrue(t.bodies.moves.isEmpty()); // already at its hut
        assertNull(ai.lastError);
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
        t.containers.containers.put(HUT, new java.util.LinkedHashMap<>(Map.of(bi, 5)));
        order(res, WorkOrderType.UPGRADE);

        tick(400);
        assertEquals("NEEDS_ITEM", ai.stateName());
        tick(2000);

        List<Request> reqs = builderRequests();
        assertEquals(2, reqs.size(), () -> "requests: " + reqs);
        List<Deliverable> asked = reqs.stream().map(Request::requestable).toList();
        assertTrue(asked.contains(new StackRequest(ai1, 18, 18, true)));
        assertTrue(asked.contains(new StackRequest(bi, 13, 13, true)));
        assertTrue(reqs.stream().allMatch(r -> r.citizenId() == citizen.id()));
        assertEquals("NEEDS_ITEM", ai.stateName());
        assertNull(ai.lastError);
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
    void fullInventoryDumpsKeepingBucketItems() {
        Building res = hut(ConstructionBuildingTypes.RESIDENCE, RES, 1);
        List<BlueprintEntry> entries = new ArrayList<>();
        for (int x = 1; x <= 10; x++) {
            entries.add(entry(x, 0, 0, STONE));
        }
        blueprint = bp(entries);
        give(STONE_I, 10);
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
        assertEquals(10, citizen.inventory().count(STONE_I)); // bucket items kept
        assertEquals(0, t.containers.count(List.of(HUT), STONE_I));
    }

    @Test
    void clearWithFullHutDoesNotLoop() {
        Building res = hut(ConstructionBuildingTypes.RESIDENCE, RES, 0);
        blueprint = new Blueprint("bp", List.of(entry(1, 0, 0, STONE)), new BlockPos(-3, 0, -3),
                new BlockPos(3, 2, 3));
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

        assertNull(ai.lastError);
        assertTrue(o.stage() != Stage.CLEAR || o.progressIndex() > 0);
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
        assertEquals(new ToolRequest(ToolType.SHOVEL, 0, 5), reqs.get(0).requestable());
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
        var adaptability = citizen.skills();
        assertTrue(adaptability.level(Skill.Adaptability) > 1 || adaptability.experience(Skill.Adaptability) > 0);
        assertTrue(colony.log().entries().stream().anyMatch(e -> e.type().equals("buildingBuilt")));
        assertEquals(List.of(new ColonyEvents.BuildingLevelChanged(colony, res, 0, 1)), events);
        assertEquals(1, t.notifier.sent.size());
        assertEquals("hycolony.build.complete", t.notifier.sent.get(0).msg().key());
        assertEquals(List.of(res.displayName(), "1"), t.notifier.sent.get(0).msg().params());
        tickUntil(() -> ai.stateName().equals("IDLE"), 200);
        assertEquals(0, resources().orderId());
    }

    @Test
    void removeOrderDeconstructsKeepingLevel() {
        Building res = hut(ConstructionBuildingTypes.RESIDENCE, RES, 2);
        blueprint = bp(List.of(entry(1, 0, 0, STONE), entry(1, 1, 0, STONE)));
        for (BlockPos p : List.of(at(1, 0, 0), at(1, 1, 0))) {
            t.blocks.blocks.put(p, new BlockState(STONE, 0));
            t.blocks.drops.put(p, List.of(new ItemAmount(STONE_I, 1)));
        }
        WorkOrder o = order(res, WorkOrderType.REMOVE);

        tickUntil(() -> gone(o), 5000);

        assertEquals(List.of("medieval/hycolony:residence/2"), loads);
        assertFalse(t.blocks.blocks.containsKey(at(1, 0, 0)));
        assertFalse(t.blocks.blocks.containsKey(at(1, 1, 0)));
        assertEquals(2, res.level());
        assertTrue(res.isDeconstructed());
        assertEquals(2, citizen.inventory().count(STONE_I));
        assertTrue(builderRequests().isEmpty());
        assertTrue(colony.log().entries().stream().anyMatch(e -> e.type().equals("buildingDeconstructed")));
    }

    @Test
    void movesOnlyWhenNextBlockBeyondTenBlocks() {
        Building res = hut(ConstructionBuildingTypes.RESIDENCE, RES, 1);
        List<BlueprintEntry> entries = new ArrayList<>();
        for (int x = 1; x <= 30; x++) {
            entries.add(entry(x, 0, 0, STONE));
        }
        blueprint = bp(entries);
        give(STONE_I, 30);
        WorkOrder o = order(res, WorkOrderType.UPGRADE);

        tickUntil(() -> gone(o), 20_000);

        List<Vec3> workMoves = t.bodies.moves.stream().filter(v -> !v.equals(Vec3.center(HUT))).toList();
        // Blocks at x 31..60: work spots 2 beyond the first block and 1 up, renewed once a block is > 10 away.
        assertEquals(List.of(Vec3.center(new BlockPos(33, 65, 0)), Vec3.center(new BlockPos(45, 65, 0)),
                Vec3.center(new BlockPos(57, 65, 0))), workMoves);
        assertEquals(30, t.blocks.placed.size());
    }

    @Test
    void cancelledOrderSendsBuilderIdle() {
        Building res = hut(ConstructionBuildingTypes.RESIDENCE, RES, 1);
        blueprint = bp(List.of(entry(1, 0, 0, STONE), entry(2, 0, 0, STONE)));
        WorkOrder o = order(res, WorkOrderType.UPGRADE);
        tickUntil(() -> ai.stateName().equals("NEEDS_ITEM"), 1000);

        colony.work().cancel(o.id());
        Stage stage = o.stage();
        int progress = o.progressIndex();
        tick(400);

        assertEquals("IDLE", ai.stateName());
        assertEquals(0, resources().orderId());
        assertEquals(stage, o.stage());
        assertEquals(progress, o.progressIndex());
        assertTrue(builderRequests().isEmpty());
        assertNull(ai.lastError);
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
        assertEquals(List.of(new StackRequest(bi, 4, 4, true)),
                builderRequests().stream().map(Request::requestable).toList());
    }
}
