package dev.hycolony.core.construction.workorder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.construction.blueprint.Blueprint;
import dev.hycolony.core.construction.blueprint.BlueprintEntry;
import dev.hycolony.core.construction.blueprint.StructurePlan;
import dev.hycolony.core.construction.resources.Buckets;
import dev.hycolony.core.construction.resources.BuildingResourcesModule;
import dev.hycolony.core.construction.resources.NeededResources;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.BlockKey;
import dev.hycolony.core.kernel.item.BlockKind;
import dev.hycolony.core.kernel.item.BlockState;
import dev.hycolony.core.kernel.item.Inventory;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.testing.FakeCatalog;
import dev.hycolony.core.testing.FakeWorldBlocks;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** In the work order package: it drives orders through their package-private lifecycle (creation, release). */
class ResourcesTest {
    private static final BlockPos HUT = new BlockPos(0, 64, 0);
    private static final BlockKey STONE = new BlockKey("stone");
    private static final BlockKey PLANK = new BlockKey("plank");
    private static final BlockKey TORCH = new BlockKey("torch");
    private static final BlockKey GHOST = new BlockKey("ghost"); // no item
    private static final ItemKey STONE_I = new ItemKey("stone_item");
    private static final ItemKey PLANK_I = new ItemKey("plank_item");
    private static final ItemKey TORCH_I = new ItemKey("torch_item");

    private static FakeCatalog catalog() {
        FakeCatalog c = new FakeCatalog();
        c.kinds.put(STONE, BlockKind.SOLID);
        c.kinds.put(PLANK, BlockKind.SOLID);
        c.kinds.put(TORCH, BlockKind.NON_SOLID);
        c.kinds.put(GHOST, BlockKind.SOLID);
        c.itemForBlock.put(STONE, STONE_I);
        c.itemForBlock.put(PLANK, PLANK_I);
        c.itemForBlock.put(TORCH, TORCH_I);
        return c;
    }

    private static BlueprintEntry entry(int x, int y, int z, BlockKey key) {
        return new BlueprintEntry(new BlockPos(x, y, z), new BlockState(key, 0), false);
    }

    /** A single row of blocks along x at y = 0. */
    private static StructurePlan row(FakeCatalog c, List<BlockKey> keys) {
        List<BlueprintEntry> entries = new ArrayList<>();
        for (int i = 0; i < keys.size(); i++) {
            entries.add(entry(i + 1, 0, 0, keys.get(i)));
        }
        Blueprint bp = new Blueprint("k", entries, new BlockPos(0, 0, 0), new BlockPos(keys.size(), 0, 0));
        return StructurePlan.build(bp, HUT, c);
    }

    private static Map<ItemKey, Integer> needs(Object... kv) {
        Map<ItemKey, Integer> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((ItemKey) kv[i], (Integer) kv[i + 1]);
        }
        return m;
    }

    private static List<ItemKey> seq(ItemKey item, int n) {
        return Collections.nCopies(n, item);
    }

    private static int stacks(Map<ItemKey, Integer> bucket, int maxStack) {
        int s = 0;
        for (int n : bucket.values()) {
            s += (n + maxStack - 1) / maxStack;
        }
        return s;
    }

    private static WorkOrder order(int id) {
        return new WorkOrder(id, WorkOrderType.BUILD, HUT, 1, new WorkOrder.Layout("s", 1, 0));
    }

    @Test
    void computeSkipsAlreadyPlacedAndItemless() {
        FakeCatalog c = catalog();
        FakeWorldBlocks world = new FakeWorldBlocks();
        // torch (deco) listed first to prove SOLID comes before DECO regardless of blueprint order
        StructurePlan plan = row(c, List.of(TORCH, STONE, STONE, PLANK, GHOST, STONE));
        // stone at x=2 already placed; x=3 has stone with the wrong rotation (not done)
        world.blocks.put(HUT.offset(2, 0, 0), new BlockState(STONE, 0));
        world.blocks.put(HUT.offset(3, 0, 0), new BlockState(STONE, 1));

        NeededResources n = NeededResources.compute(plan, world, c);

        assertEquals(List.of(STONE_I, PLANK_I, STONE_I, TORCH_I), n.sequence());
        assertEquals(needs(STONE_I, 2, PLANK_I, 1, TORCH_I, 1), n.remaining());
        assertEquals(4, n.total());

        n.reduce(STONE_I, 1);
        assertEquals(1, n.remaining().get(STONE_I));
        n.reduce(PLANK_I, 5);
        assertFalse(n.remaining().containsKey(PLANK_I));
        assertEquals(2, n.total());
    }

    @Test
    void splitRespectsEighteenStacks() {
        assertEquals(18, Buckets.BUCKET_STACKS);
        List<ItemKey> in = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            in.addAll(seq(new ItemKey("i" + i), 64)); // 40 stacks
        }
        List<Map<ItemKey, Integer>> buckets = Buckets.split(in, k -> 64);

        assertEquals(3, buckets.size());
        assertEquals(18, stacks(buckets.get(0), 64));
        assertEquals(18, stacks(buckets.get(1), 64));
        assertEquals(4, stacks(buckets.get(2), 64));
        List<ItemKey> flat = new ArrayList<>();
        buckets.forEach(b -> flat.addAll(b.keySet()));
        assertEquals(new ArrayList<>(new LinkedHashSet<>(in)), flat);

        // a 200-unit item with maxStack 64 takes 4 stacks
        List<Map<ItemKey, Integer>> one = Buckets.split(seq(STONE_I, 200), k -> 64);
        assertEquals(List.of(needs(STONE_I, 200)), one);
        assertEquals(4, stacks(one.get(0), 64));
        assertTrue(Buckets.split(List.of(), k -> 64).isEmpty());
    }

    @Test
    void splitTreatsZeroMaxStackAsOne() {
        List<Map<ItemKey, Integer>> buckets = Buckets.split(seq(STONE_I, 20), k -> 0);
        assertEquals(List.of(needs(STONE_I, 18), needs(STONE_I, 2)), buckets);
    }

    @Test
    void itemSpanningBucketsIsSplit() {
        List<ItemKey> in = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            in.addAll(seq(new ItemKey("i" + i), 64)); // 16 stacks
        }
        in.addAll(seq(STONE_I, 200)); // 4 stacks: 2 fit, 2 overflow
        in.addAll(seq(PLANK_I, 10));

        List<Map<ItemKey, Integer>> buckets = Buckets.split(in, k -> 64);

        assertEquals(2, buckets.size());
        assertEquals(128, buckets.get(0).get(STONE_I));
        assertEquals(18, stacks(buckets.get(0), 64));
        assertEquals(needs(STONE_I, 72, PLANK_I, 10), buckets.get(1));
    }

    @Test
    void interleavedPlanBucketsAlternateAndNextItemAlwaysRequested() {
        FakeCatalog c = catalog();
        c.defaultMaxStack = 1;
        List<BlockKey> keys = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            keys.add(i % 2 == 0 ? STONE : PLANK); // A B A B ... -> 4 buckets of 18, 18, 18, 6
        }
        NeededResources n = NeededResources.compute(row(c, keys), new FakeWorldBlocks(), c);
        BuildingResourcesModule m = new BuildingResourcesModule();
        m.start(order(1), n);
        assertEquals(needs(STONE_I, 9, PLANK_I, 9), m.currentBucket().orElseThrow());
        assertEquals(needs(STONE_I, 9, PLANK_I, 9), m.nextBucket().orElseThrow());

        Inventory empty = new Inventory(27);
        for (ItemKey next : List.copyOf(n.sequence())) {
            Map<ItemKey, Integer> cur = m.currentBucket().orElseThrow();
            Map<ItemKey, Integer> nxt = m.nextBucket().orElse(Map.of());
            assertTrue(cur.getOrDefault(next, 0) > 0 || nxt.getOrDefault(next, 0) > 0, "not bucketed: " + next);
            assertTrue(m.missingForCurrentAndNext(empty, k -> 0).containsKey(next), "not requested: " + next);
            m.onPlaced(next);
        }
        assertTrue(m.currentBucket().isEmpty());
        assertEquals(0, n.total());
    }

    @Test
    void missingSubtractsInventoryAndHut() {
        FakeCatalog c = catalog();
        c.maxStacks.put(STONE_I, 1); // 1 stone per stack: 20 stone -> buckets of 18 and 2
        List<BlockKey> keys = new ArrayList<>(Collections.nCopies(20, STONE));
        keys.add(PLANK);
        keys.add(TORCH);
        NeededResources n = NeededResources.compute(row(c, keys), new FakeWorldBlocks(), c);

        BuildingResourcesModule m = new BuildingResourcesModule();
        m.start(order(1), n);
        assertEquals(needs(STONE_I, 18), m.currentBucket().orElseThrow());
        assertEquals(needs(STONE_I, 2, PLANK_I, 1, TORCH_I, 1), m.nextBucket().orElseThrow());

        Inventory inv = new Inventory(27);
        inv.insert(new ItemAmount(STONE_I, 5), k -> 64);
        inv.insert(new ItemAmount(TORCH_I, 1), k -> 64);
        Map<ItemKey, Integer> hut = Map.of(STONE_I, 3, PLANK_I, 4);

        // stone 20 - (5 + 3) = 12; plank covered by hut; torch covered by inventory
        assertEquals(needs(STONE_I, 12), m.missingForCurrentAndNext(inv, k -> hut.getOrDefault(k, 0)));
    }

    @Test
    void partialPlacementKeepsBucketFullPlacementAdvances() {
        FakeCatalog c = catalog();
        c.defaultMaxStack = 1;
        List<BlockKey> keys = new ArrayList<>(Collections.nCopies(18, STONE));
        keys.add(PLANK);
        NeededResources n = NeededResources.compute(row(c, keys), new FakeWorldBlocks(), c);
        BuildingResourcesModule m = new BuildingResourcesModule();
        m.start(order(1), n);

        for (int i = 0; i < 17; i++) {
            m.onPlaced(STONE_I);
        }
        assertEquals(needs(STONE_I, 1), m.currentBucket().orElseThrow());
        assertEquals(needs(PLANK_I, 1), m.nextBucket().orElseThrow());

        m.onPlaced(STONE_I);
        assertEquals(needs(PLANK_I, 1), m.currentBucket().orElseThrow());
        assertTrue(m.nextBucket().isEmpty());
        assertEquals(1, n.total());
    }

    @Test
    void emptiedLaterBucketIsDropped() {
        FakeCatalog c = catalog();
        c.defaultMaxStack = 1;
        // buckets: {stone:18}, {plank:18}, {torch:1}
        List<BlockKey> keys = new ArrayList<>(Collections.nCopies(18, STONE));
        keys.addAll(Collections.nCopies(18, PLANK));
        keys.add(TORCH);
        NeededResources n = NeededResources.compute(row(c, keys), new FakeWorldBlocks(), c);
        BuildingResourcesModule m = new BuildingResourcesModule();
        m.start(order(1), n);
        assertEquals(needs(PLANK_I, 18), m.nextBucket().orElseThrow());

        // planks placed out of order (e.g. a second pass) empty the middle bucket, which is dropped at once
        for (int i = 0; i < 18; i++) {
            m.onPlaced(PLANK_I);
        }
        assertEquals(needs(STONE_I, 18), m.currentBucket().orElseThrow());
        assertEquals(needs(TORCH_I, 1), m.nextBucket().orElseThrow());

        // an item in no bucket only reduces the needs
        m.onPlaced(PLANK_I);
        assertEquals(needs(STONE_I, 18), m.currentBucket().orElseThrow());
    }

    @Test
    void progressPersistsNeedsRecomputed() {
        FakeCatalog c = catalog();
        FakeWorldBlocks world = new FakeWorldBlocks();
        StructurePlan plan = row(c, List.of(STONE, STONE, PLANK));
        WorkOrder o = order(7);

        BuildingResourcesModule m = new BuildingResourcesModule();
        assertEquals(0, m.orderId());
        assertEquals(Stage.DONE, m.stage());
        m.start(o, NeededResources.compute(plan, world, c));
        assertEquals(7, m.orderId());
        assertEquals(Stage.CLEAR, m.stage());

        // progress writes through to the order, its single owner
        m.progress(Stage.SOLID, 1);
        assertEquals(Stage.SOLID, o.stage());
        assertEquals(1, o.progressIndex());
        world.blocks.put(HUT.offset(1, 0, 0), new BlockState(STONE, 0)); // first stone placed

        // the order persists the progress; the module is rebuilt from it, needs recomputed from the world
        WorkOrder loadedOrder = WorkOrder.read(o.write());
        BuildingResourcesModule loaded = new BuildingResourcesModule();
        loaded.start(loadedOrder, NeededResources.compute(plan, world, c));
        assertEquals(Stage.SOLID, loaded.stage());
        assertEquals(1, loaded.progressIndex());
        assertEquals(needs(STONE_I, 1, PLANK_I, 1), loaded.currentBucket().orElseThrow());

        // a released and reclaimed order restarts from its first stage, not a stale one
        loadedOrder.release();
        loaded.start(loadedOrder, NeededResources.compute(plan, world, c));
        assertEquals(Stage.CLEAR, loaded.stage());
        assertEquals(0, loaded.progressIndex());
    }
}
