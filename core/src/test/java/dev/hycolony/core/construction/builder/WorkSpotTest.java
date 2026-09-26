package dev.hycolony.core.construction.builder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.construction.blueprint.Blueprint;
import dev.hycolony.core.construction.blueprint.StructurePlan;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.BlockKey;
import dev.hycolony.core.kernel.item.BlockKind;
import dev.hycolony.core.kernel.item.BlockState;
import dev.hycolony.core.testing.FakeCatalog;
import dev.hycolony.core.testing.FakeWorldBlocks;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorkSpotTest {
    private static final BlockPos SITE = new BlockPos(0, 0, 0);
    /** Outward is +x: the spots 2 out are (12, 1, 0), then the sides (10, 1, 2) and (10, 1, -2). */
    private static final BlockPos BLOCK = new BlockPos(10, 0, 0);

    private static final BlockState STONE = new BlockState(new BlockKey("stone"), 0);
    private static final BlockState WATER = new BlockState(new BlockKey("water"), 0);
    private static final BlockState LAVA = new BlockState(new BlockKey("lava"), 0);
    private static final BlockState CAMPFIRE = new BlockState(new BlockKey("campfire"), 0);
    private static final BlockState FIRE = new BlockState(new BlockKey("fire"), 0);

    private final FakeWorldBlocks world = new FakeWorldBlocks();
    private final FakeCatalog catalog = new FakeCatalog();
    private final StructurePlan plan = StructurePlan.build(
            new Blueprint("bp", List.of(), new BlockPos(0, 0, 0), new BlockPos(0, 0, 0)), SITE, catalog);

    WorkSpotTest() {
        catalog.kinds.put(WATER.key(), BlockKind.FLUID);
        catalog.kinds.put(LAVA.key(), BlockKind.FLUID);
        catalog.harmful.add(LAVA.key());
        catalog.harmful.add(CAMPFIRE.key());
        catalog.kinds.put(FIRE.key(), BlockKind.NON_SOLID);
        catalog.harmful.add(FIRE.key());
    }

    private BlockPos choose() {
        WorkSpot.Spot spot = new WorkSpot(world, catalog).choose(BLOCK, SITE, plan);
        assertTrue(spot.verified(), "a spot found in the world");
        return spot.pos();
    }

    @Test
    void fallbackSpotIsNeverBuriedNorInAFluid() {
        for (int y = 1; y <= 20; y++) {
            world.blocks.put(new BlockPos(12, y, 0), STONE); // outward: buried, no opening above
        }
        world.blocks.put(new BlockPos(10, 1, 2), WATER); // first side: in a fluid

        assertEquals(new BlockPos(10, 1, -2), choose(), "the open column, though without ground");
    }

    @Test
    void neverStandsAtTheBottomOfALake() {
        world.blocks.put(new BlockPos(12, 0, 0), WATER);
        world.blocks.put(new BlockPos(12, -1, 0), WATER);
        world.blocks.put(new BlockPos(12, -2, 0), STONE); // outward: a lake bed under two blocks of water
        world.blocks.put(new BlockPos(10, 0, 2), STONE); // first side: dry ground

        assertEquals(new BlockPos(10, 1, 2), choose());
    }

    @Test
    void standsInAnkleDeepWaterOnSolidGround() {
        world.blocks.put(new BlockPos(12, 0, 0), WATER);
        world.blocks.put(new BlockPos(12, -1, 0), STONE); // outward: one block of water on stone

        assertEquals(new BlockPos(12, 0, 0), choose());
    }

    @Test
    void refusesWaterTwoBlocksDeep() {
        world.blocks.put(new BlockPos(12, 1, 0), WATER);
        world.blocks.put(new BlockPos(12, 0, 0), WATER);
        world.blocks.put(new BlockPos(12, -1, 0), STONE); // outward: two blocks of water on stone
        world.blocks.put(new BlockPos(10, 0, 2), STONE); // first side: dry ground

        assertEquals(new BlockPos(10, 1, 2), choose());
    }

    @Test
    void neverStandsInAnkleDeepLava() {
        world.blocks.put(new BlockPos(12, 0, 0), LAVA);
        world.blocks.put(new BlockPos(12, -1, 0), STONE); // outward: one block of lava on stone
        world.blocks.put(new BlockPos(10, 1, 2), STONE);
        world.blocks.put(new BlockPos(10, 2, 2), LAVA); // first side: raised ground under one block of lava
        world.blocks.put(new BlockPos(10, 0, -2), STONE); // second side: dry ground

        assertEquals(new BlockPos(10, 1, -2), choose());
    }

    @Test
    void standsInAnkleDeepWaterOnRaisedGround() {
        world.blocks.put(new BlockPos(12, 1, 0), STONE);
        world.blocks.put(new BlockPos(12, 2, 0), WATER); // outward: ground above the block's level, one water on it

        assertEquals(new BlockPos(12, 2, 0), choose());
    }

    @Test
    void refusesWaterTwoBlocksDeepOnRaisedGround() {
        world.blocks.put(new BlockPos(12, 1, 0), STONE);
        world.blocks.put(new BlockPos(12, 2, 0), WATER);
        world.blocks.put(new BlockPos(12, 3, 0), WATER); // outward: raised ground under two blocks of water
        world.blocks.put(new BlockPos(10, 0, 2), STONE); // first side: dry ground

        assertEquals(new BlockPos(10, 1, 2), choose());
    }

    @Test
    void refusesASpotUnderAnOverhang() {
        world.blocks.put(new BlockPos(12, 0, 0), STONE);
        world.blocks.put(new BlockPos(12, 2, 0), STONE); // outward: ground, but no room for the head
        world.blocks.put(new BlockPos(10, 0, 2), STONE); // first side: dry ground

        assertEquals(new BlockPos(10, 1, 2), choose());
    }

    @Test
    void neverFallsBackAboveUnfitGround() {
        world.blocks.put(new BlockPos(12, 0, 0), LAVA);
        world.blocks.put(new BlockPos(12, -1, 0), STONE); // outward: open air over one block of lava on stone

        assertEquals(new BlockPos(10, 1, 2), choose(), "the first column with no ground at all");
    }

    @Test
    void neverFallsBackAboveALake() {
        world.blocks.put(new BlockPos(12, 0, 0), WATER);
        world.blocks.put(new BlockPos(12, -1, 0), WATER); // outward: open air over a lake with no bed in range

        assertEquals(new BlockPos(10, 1, 2), choose(), "the first column with no ground at all");
    }

    @Test
    void lastResortSpotIsUnverified() {
        for (int d = 2; d <= 4; d++) {
            for (BlockPos c : List.of(
                    BLOCK.offset(d, 0, 0), BLOCK.offset(-d, 0, 0), BLOCK.offset(0, 0, d), BLOCK.offset(0, 0, -d))) {
                world.blocks.put(c, LAVA);
                world.blocks.put(c.offset(0, -1, 0), STONE); // every column: open air over lava on stone
            }
        }

        assertEquals(
                new WorkSpot.Spot(new BlockPos(12, 1, 0), false),
                new WorkSpot(world, catalog).choose(BLOCK, SITE, plan),
                "2 out and 1 up, unchecked");
    }

    @Test
    void neverStandsOnACampfire() {
        world.blocks.put(new BlockPos(12, 0, 0), CAMPFIRE); // outward: the only ground is a campfire
        world.blocks.put(new BlockPos(10, 0, 2), STONE); // first side: dry ground

        assertEquals(new BlockPos(10, 1, 2), choose());
    }

    @Test
    void neverStandsInAFireBlock() {
        world.blocks.put(new BlockPos(12, 0, 0), STONE);
        world.blocks.put(new BlockPos(12, 1, 0), FIRE); // outward: stone, but a fire burns on it
        world.blocks.put(new BlockPos(10, 0, 2), STONE); // first side: dry ground

        assertEquals(new BlockPos(10, 1, 2), choose());
    }

    @Test
    void neverFallsBackIntoAFireWithNoGround() {
        world.blocks.put(new BlockPos(12, 1, 0), FIRE); // outward: a fire floating over a column with no ground

        assertEquals(new BlockPos(10, 1, 2), choose(), "the next column with no ground at all");
    }
}
