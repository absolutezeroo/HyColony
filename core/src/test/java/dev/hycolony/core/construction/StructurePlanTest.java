package dev.hycolony.core.construction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.BlockKey;
import dev.hycolony.core.kernel.item.BlockKind;
import dev.hycolony.core.kernel.item.BlockState;
import dev.hycolony.core.testing.FakeCatalog;
import dev.hycolony.core.testing.FakeWorldBlocks;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class StructurePlanTest {
    private static final BlockKey STONE = new BlockKey("stone");
    private static final BlockKey PLANK = new BlockKey("plank");
    private static final BlockKey WATER = new BlockKey("water");
    private static final BlockKey AIR = new BlockKey("air");
    private static final BlockPos HUT = new BlockPos(10, 64, 10);

    private static BlueprintEntry entry(int x, int y, int z, BlockKey key) {
        return new BlueprintEntry(new BlockPos(x, y, z), new BlockState(key, 0), false);
    }

    @Test
    void solidAndDecoSortedBottomUp() {
        FakeCatalog catalog = new FakeCatalog();
        catalog.kinds.put(STONE, BlockKind.SOLID);
        catalog.kinds.put(PLANK, BlockKind.NON_SOLID);
        catalog.kinds.put(WATER, BlockKind.FLUID);

        // Deliberately scrambled input order.
        List<BlueprintEntry> entries = List.of(
                entry(1, 2, 0, STONE),
                entry(0, 0, 1, STONE),
                entry(1, 0, 0, PLANK),
                entry(0, 0, 0, STONE),
                entry(0, 1, 0, WATER),
                entry(0, 0, 0, PLANK) // duplicate offset with a different key: fine, entries aren't deduped here
        );
        Blueprint bp = new Blueprint("k", entries, new BlockPos(0, 0, 0), new BlockPos(2, 2, 2));
        StructurePlan plan = StructurePlan.build(bp, HUT, catalog);

        List<BlueprintEntry> solid = plan.solidList();
        assertEquals(3, solid.size());
        assertEquals(new BlockPos(0, 0, 0), solid.get(0).offset());
        assertEquals(new BlockPos(0, 0, 1), solid.get(1).offset());
        assertEquals(new BlockPos(1, 2, 0), solid.get(2).offset());
        for (BlueprintEntry e : solid) {
            assertEquals(STONE, e.state().key());
        }

        List<BlueprintEntry> deco = plan.decoList();
        assertEquals(3, deco.size());
        assertEquals(new BlockPos(0, 0, 0), deco.get(0).offset());
        assertEquals(PLANK, deco.get(0).state().key());
        assertEquals(new BlockPos(1, 0, 0), deco.get(1).offset());
        assertEquals(PLANK, deco.get(1).state().key());
        assertEquals(new BlockPos(0, 1, 0), deco.get(2).offset());
        assertEquals(WATER, deco.get(2).state().key());
    }

    @Test
    void clearListTopDownExcludesHut() {
        FakeCatalog catalog = new FakeCatalog();
        Blueprint bp = new Blueprint("k", List.of(), new BlockPos(-1, 0, -1), new BlockPos(1, 2, 1));
        StructurePlan plan = StructurePlan.build(bp, HUT, catalog);

        List<BlockPos> clear = plan.clearList();
        // 3x3x3 box minus the hut cell itself (offset 0,0,0).
        assertEquals(26, clear.size());
        assertFalse(clear.contains(HUT));

        // Top-down: y desc, then x asc, then z asc.
        assertEquals(new BlockPos(9, 66, 9), clear.get(0));
        assertEquals(new BlockPos(9, 66, 11), clear.get(2));
        assertEquals(new BlockPos(11, 64, 11), clear.get(clear.size() - 1));
        for (int i = 1; i < clear.size(); i++) {
            assertTrue(clear.get(i - 1).y() >= clear.get(i).y());
        }
    }

    @Test
    void removeListOnlyNonAir() {
        FakeCatalog catalog = new FakeCatalog();
        catalog.kinds.put(STONE, BlockKind.SOLID);
        catalog.kinds.put(AIR, BlockKind.AIR);

        List<BlueprintEntry> entries = List.of(
                entry(0, 1, 0, STONE),
                entry(0, 0, 0, AIR),
                entry(1, 0, 0, STONE)
        );
        Blueprint bp = new Blueprint("k", entries, new BlockPos(0, 0, 0), new BlockPos(1, 1, 0));
        StructurePlan plan = StructurePlan.build(bp, HUT, catalog);

        List<BlockPos> remove = plan.removeList();
        assertEquals(2, remove.size());
        // Top-down: y desc first.
        assertEquals(HUT.offset(0, 1, 0), remove.get(0));
        assertEquals(HUT.offset(1, 0, 0), remove.get(1));
    }

    @Test
    void isDoneComparesKeyAndRotation() {
        FakeCatalog catalog = new FakeCatalog();
        FakeWorldBlocks world = new FakeWorldBlocks();
        BlueprintEntry e = new BlueprintEntry(new BlockPos(0, 0, 0), new BlockState(STONE, 1), false);
        Blueprint bp = new Blueprint("k", List.of(e), new BlockPos(0, 0, 0), new BlockPos(0, 0, 0));
        StructurePlan plan = StructurePlan.build(bp, HUT, catalog);

        // Not loaded/placed yet.
        assertFalse(plan.isDone(e, world));

        // Same key, different rotation: not done.
        world.blocks.put(HUT, new BlockState(STONE, 0));
        assertFalse(plan.isDone(e, world));

        // Different key, same rotation: not done.
        world.blocks.put(HUT, new BlockState(PLANK, 1));
        assertFalse(plan.isDone(e, world));

        // Same key and rotation: done.
        world.blocks.put(HUT, new BlockState(STONE, 1));
        assertTrue(plan.isDone(e, world));
    }

    @Test
    void twentyThousandEntryPlanBuildsUnder100ms() {
        FakeCatalog catalog = new FakeCatalog();
        catalog.kinds.put(STONE, BlockKind.SOLID);

        int x = 20, y = 100, z = 10; // 20,000 entries
        List<BlueprintEntry> entries = new ArrayList<>(x * y * z);
        for (int yy = 0; yy < y; yy++) {
            for (int xx = 0; xx < x; xx++) {
                for (int zz = 0; zz < z; zz++) {
                    entries.add(entry(xx, yy, zz, STONE));
                }
            }
        }
        Blueprint bp = new Blueprint("big", entries, new BlockPos(0, 0, 0), new BlockPos(x - 1, y - 1, z - 1));

        long start = System.nanoTime();
        StructurePlan plan = StructurePlan.build(bp, HUT, catalog);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertEquals(20_000, plan.solidList().size());
        assertTrue(elapsedMs < 100, "build took " + elapsedMs + " ms");
    }
}
