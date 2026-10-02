package dev.hycolony.core.construction.tape;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.BlockKey;
import dev.hycolony.core.kernel.item.BlockKind;
import dev.hycolony.core.kernel.item.BlockState;
import dev.hycolony.core.testing.FakeCatalog;
import dev.hycolony.core.testing.FakeWorldBlocks;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The tapes around a footprint (MC ConstructionTapeHelper.placeConstructionTape and firstValidPosition). */
class TapeLayoutTest {
    private static final BlockState STONE = new BlockState(new BlockKey("Rock_Stone"), 0);
    private static final BlockState GRASS = new BlockState(new BlockKey("Plant_Grass"), 0);
    private static final BlockPos MIN = new BlockPos(0, 64, 0);
    private static final BlockPos MAX = new BlockPos(2, 66, 2);

    private final FakeWorldBlocks world = new FakeWorldBlocks();
    private final FakeCatalog catalog = new FakeCatalog();

    TapeLayoutTest() {
        catalog.kinds.put(GRASS.key(), BlockKind.NON_SOLID);
        for (int x = -3; x <= 5; x++) {
            for (int z = -3; z <= 5; z++) {
                world.blocks.put(new BlockPos(x, 63, z), STONE);
            }
        }
    }

    private List<Tape> layout() {
        return TapeLayout.of(MIN, MAX, world, catalog);
    }

    private static Tape tape(int x, int z, TapeShape shape, int rotation) {
        return new Tape(new BlockPos(x, 64, z), shape, rotation);
    }

    @Test
    void theFootprintWidenedByOneIsTapedInMcsOrderWithCornersAtTheAngles() {
        List<Tape> tapes = layout();

        assertEquals(16, tapes.size(), "the border of the 5 x 5 square, each cell once");
        // MC's first step: north and south edges at x - 1, then the west edge (already taped) and the east edge.
        assertEquals(
                List.of(
                        tape(-1, -1, TapeShape.CORNER, 3),
                        tape(-1, 3, TapeShape.CORNER, 0),
                        tape(3, -1, TapeShape.CORNER, 2),
                        tape(0, -1, TapeShape.STRAIGHT, 1),
                        tape(0, 3, TapeShape.STRAIGHT, 1),
                        tape(-1, 0, TapeShape.STRAIGHT, 0),
                        tape(3, 0, TapeShape.STRAIGHT, 0)),
                tapes.subList(0, 7));
        assertEquals(tape(3, 3, TapeShape.CORNER, 1), tapes.getLast(), "MC places the south-east corner last");
    }

    @Test
    void aTapeStandsOnTheFirstSolidBlockBelowTheTopAndReplacesAPlant() {
        world.blocks.put(new BlockPos(0, 65, -1), STONE);
        world.blocks.put(new BlockPos(1, 64, -1), GRASS);

        List<Tape> tapes = layout();

        assertTrue(tapes.contains(new Tape(new BlockPos(0, 66, -1), TapeShape.STRAIGHT, 1)));
        assertTrue(tapes.contains(tape(1, -1, TapeShape.STRAIGHT, 1)));
    }

    @Test
    void aTapeStandsInWaterOnTheGroundBelowIt() {
        // MC firstValidPosition: water is replaceable (the tape is then waterlogged).
        BlockState water = new BlockState(new BlockKey("~fluid:Water_Source"), 0);
        catalog.kinds.put(water.key(), BlockKind.FLUID);
        world.blocks.put(new BlockPos(0, 64, -1), water);
        world.blocks.put(new BlockPos(0, 65, -1), water);

        assertTrue(layout().contains(tape(0, -1, TapeShape.STRAIGHT, 1)));
    }

    @Test
    void theGroundSearchReachesExactlyHeightPlusFiveBelowTheTop() {
        // The top is 66 and the height 2: MC tests the cells 66 down to 59 (i = 0 .. 2 + 5).
        world.blocks.remove(new BlockPos(0, 63, -1));
        world.blocks.put(new BlockPos(0, 59, -1), STONE);
        world.blocks.remove(new BlockPos(1, 63, -1));
        world.blocks.put(new BlockPos(1, 58, -1), STONE);

        List<Tape> tapes = layout();

        assertTrue(tapes.contains(new Tape(new BlockPos(0, 60, -1), TapeShape.STRAIGHT, 1)), "the last cell in reach");
        assertFalse(tapes.stream().anyMatch(t -> t.pos().x() == 1 && t.pos().z() == -1), "one cell too deep");
    }

    @Test
    void anUnbreakableBlockIsGround() {
        BlockState bedrock = new BlockState(new BlockKey("Rock_Bedrock"), 0);
        catalog.kinds.put(bedrock.key(), BlockKind.UNBREAKABLE);
        world.blocks.put(new BlockPos(0, 63, 3), bedrock);

        assertTrue(layout().contains(tape(0, 3, TapeShape.STRAIGHT, 1)));
    }

    @Test
    void aColumnWithoutGroundOrRoomInReachGetsNoTape() {
        world.blocks.remove(new BlockPos(1, 63, -1)); // a hole deeper than height + 5
        for (int y = 58; y <= 67; y++) {
            world.blocks.put(new BlockPos(2, y, -1), STONE); // solid all the way up
        }

        List<Tape> tapes = layout();

        assertFalse(tapes.stream().anyMatch(t -> t.pos().x() == 1 && t.pos().z() == -1));
        assertFalse(tapes.stream().anyMatch(t -> t.pos().x() == 2 && t.pos().z() == -1));
        assertEquals(14, tapes.size());
    }
}
