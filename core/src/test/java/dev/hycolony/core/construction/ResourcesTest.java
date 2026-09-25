package dev.hycolony.core.construction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

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
    private static StructurePlan row(FakeCatalog c, BlockKey... keys) {
        List<BlueprintEntry> entries = new ArrayList<>();
        for (int i = 0; i < keys.length; i++) {
            entries.add(entry(i + 1, 0, 0, keys[i]));
        }
        Blueprint bp = new Blueprint("k", entries, new BlockPos(0, 0, 0), new BlockPos(keys.length, 0, 0));
        return StructurePlan.build(bp, HUT, c);
    }

    private static Map<ItemKey, Integer> needs(Object... kv) {
        Map<ItemKey, Integer> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((ItemKey) kv[i], (Integer) kv[i + 1]);
        }
        return m;
    }

    private static int stacks(Map<ItemKey, Integer> bucket, int maxStack) {
        int s = 0;
        for (int n : bucket.values()) {
            s += (n + maxStack - 1) / maxStack;
        }
        return s;
    }

    @Test
    void computeSkipsAlreadyPlacedAndItemless() {
        FakeCatalog c = catalog();
        FakeWorldBlocks world = new FakeWorldBlocks();
        // torch (deco) listed first to prove SOLID comes before DECO regardless of blueprint order
        StructurePlan plan = row(c, TORCH, STONE, STONE, PLANK, GHOST, STONE);
        // stone at x=2 already placed; x=3 has stone with the wrong rotation (not done)
        world.blocks.put(HUT.offset(2, 0, 0), new BlockState(STONE, 0));
        world.blocks.put(HUT.offset(3, 0, 0), new BlockState(STONE, 1));

        NeededResources n = NeededResources.compute(plan, world, c);

        assertEquals(List.of(STONE_I, PLANK_I, TORCH_I), new ArrayList<>(n.remaining().keySet()));
        assertEquals(2, n.remaining().get(STONE_I));
        assertEquals(1, n.remaining().get(PLANK_I));
        assertEquals(1, n.remaining().get(TORCH_I));
        assertEquals(4, n.total());

        n.reduce(STONE_I, 1);
        assertEquals(1, n.remaining().get(STONE_I));
        n.reduce(PLANK_I, 5);
        assertTrue(!n.remaining().containsKey(PLANK_I));
        assertEquals(2, n.total());
    }

    @Test
    void splitRespectsEighteenStacks() {
        assertEquals(18, Buckets.BUCKET_STACKS);
        Map<ItemKey, Integer> in = new LinkedHashMap<>();
        for (int i = 0; i < 40; i++) {
            in.put(new ItemKey("i" + i), 64); // 40 stacks
        }
        List<Map<ItemKey, Integer>> buckets = Buckets.split(in, k -> 64);

        assertEquals(3, buckets.size());
        assertEquals(18, stacks(buckets.get(0), 64));
        assertEquals(18, stacks(buckets.get(1), 64));
        assertEquals(4, stacks(buckets.get(2), 64));
        // insertion order kept across buckets
        List<ItemKey> flat = new ArrayList<>();
        buckets.forEach(b -> flat.addAll(b.keySet()));
        assertEquals(new ArrayList<>(in.keySet()), flat);

        // a 200-unit item with maxStack 64 takes 4 stacks
        List<Map<ItemKey, Integer>> one = Buckets.split(needs(STONE_I, 200), k -> 64);
        assertEquals(1, one.size());
        assertEquals(4, stacks(one.get(0), 64));
        assertTrue(Buckets.split(Map.of(), k -> 64).isEmpty());
    }

    @Test
    void itemSpanningBucketsIsSplit() {
        Map<ItemKey, Integer> in = new LinkedHashMap<>();
        for (int i = 0; i < 16; i++) {
            in.put(new ItemKey("i" + i), 64); // 16 stacks
        }
        in.put(STONE_I, 200); // 4 stacks: 2 fit, 2 overflow
        in.put(PLANK_I, 10);

        List<Map<ItemKey, Integer>> buckets = Buckets.split(in, k -> 64);

        assertEquals(2, buckets.size());
        assertEquals(128, buckets.get(0).get(STONE_I));
        assertEquals(18, stacks(buckets.get(0), 64));
        assertEquals(needs(STONE_I, 72, PLANK_I, 10), buckets.get(1));
    }

    @Test
    void missingSubtractsInventoryAndHut() {
        FakeCatalog c = catalog();
        c.maxStacks.put(STONE_I, 1); // 1 stone per stack: 20 stone -> buckets of 18 and 2
        List<BlockKey> keys = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            keys.add(STONE);
        }
        keys.add(PLANK);
        keys.add(TORCH);
        StructurePlan plan = row(c, keys.toArray(BlockKey[]::new));
        NeededResources n = NeededResources.compute(plan, new FakeWorldBlocks(), c);

        BuildingResourcesModule m = new BuildingResourcesModule();
        WorkOrder o = new WorkOrder(1, WorkOrderType.BUILD, HUT, 1, 1, "s", 0);
        m.start(o, n);
        assertEquals(needs(STONE_I, 18), m.currentBucket().orElseThrow());
        assertEquals(needs(STONE_I, 2, PLANK_I, 1, TORCH_I, 1), m.nextBucket().orElseThrow());

        Inventory inv = new Inventory(27);
        inv.insert(new ItemAmount(STONE_I, 5), k -> 64);
        inv.insert(new ItemAmount(TORCH_I, 1), k -> 64);
        Map<ItemKey, Integer> hut = Map.of(STONE_I, 3, PLANK_I, 4);

        Map<ItemKey, Integer> missing = m.missingForCurrentAndNext(inv, k -> hut.getOrDefault(k, 0));
        // stone 20 - (5 + 3) = 12; plank covered by hut; torch covered by inventory
        assertEquals(needs(STONE_I, 12), missing);

        // not satisfied until the current bucket's blocks are placed
        m.advanceBucketIfSatisfied(inv, k -> hut.getOrDefault(k, 0));
        assertEquals(needs(STONE_I, 18), m.currentBucket().orElseThrow());
        n.reduce(STONE_I, 18);
        m.advanceBucketIfSatisfied(inv, k -> hut.getOrDefault(k, 0));
        assertEquals(needs(STONE_I, 2, PLANK_I, 1, TORCH_I, 1), m.currentBucket().orElseThrow());
        assertTrue(m.nextBucket().isEmpty());
        assertEquals(Map.of(), m.missingForCurrentAndNext(inv, k -> hut.getOrDefault(k, 0)));
    }

    @Test
    void progressPersistsNeedsRecomputed() {
        FakeCatalog c = catalog();
        FakeWorldBlocks world = new FakeWorldBlocks();
        StructurePlan plan = row(c, STONE, STONE, PLANK);
        WorkOrder o = new WorkOrder(7, WorkOrderType.BUILD, HUT, 1, 1, "s", 0);

        BuildingResourcesModule m = new BuildingResourcesModule();
        m.start(o, NeededResources.compute(plan, world, c));
        assertEquals(7, m.orderId());
        assertEquals(Stage.CLEAR, m.stage());
        m.progress(Stage.SOLID, 1);
        world.blocks.put(HUT.offset(1, 0, 0), new BlockState(STONE, 0)); // first stone placed

        JsonObject json = new JsonObject();
        m.write(json);
        assertEquals(3, json.size()); // orderId, stage, progressIndex only

        BuildingResourcesModule loaded = new BuildingResourcesModule();
        loaded.read(json);
        assertEquals(7, loaded.orderId());
        assertEquals(Stage.SOLID, loaded.stage());
        assertEquals(1, loaded.progressIndex());
        assertEquals(0, loaded.needs().total());
        assertTrue(loaded.currentBucket().isEmpty());

        // same order: progress kept, needs recomputed from the world
        loaded.start(o, NeededResources.compute(plan, world, c));
        assertEquals(Stage.SOLID, loaded.stage());
        assertEquals(1, loaded.progressIndex());
        assertEquals(needs(STONE_I, 1, PLANK_I, 1), loaded.currentBucket().orElseThrow());

        // another order: progress restarts from that order
        WorkOrder other = new WorkOrder(8, WorkOrderType.REMOVE, HUT, 0, 1, "s", 0);
        loaded.start(other, NeededResources.compute(plan, world, c));
        assertEquals(8, loaded.orderId());
        assertEquals(Stage.REMOVE, loaded.stage());
        assertEquals(0, loaded.progressIndex());
    }
}
