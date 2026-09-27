package dev.hycolony.core.construction.wand;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.hycolony.core.kernel.BlockPos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class WandMovesTest {
    private static final BlockPos ORIGIN = new BlockPos(0, 64, 0);

    @ParameterizedTest
    @CsvSource({"0,0,-1", "1,1,0", "2,0,1", "3,-1,0"})
    void forwardFollowsThePlayerFacing(int facing, int dx, int dz) {
        BlockPos moved = WandMoves.move(ORIGIN, WandActions.Dir.FORWARD, facing);

        assertEquals(new BlockPos(dx, 64, dz), moved);
    }

    @Test
    void leftIsCounterClockwiseOfFacing() {
        // North (facing 0): left is west (-X), the direction one quarter turn counterclockwise of north.
        BlockPos moved = WandMoves.move(ORIGIN, WandActions.Dir.LEFT, 0);

        assertEquals(new BlockPos(-1, 64, 0), moved);
    }

    @Test
    void backIsOppositeOfForward() {
        // North (facing 0): back is south (+Z), the direction opposite of north.
        BlockPos moved = WandMoves.move(ORIGIN, WandActions.Dir.BACK, 0);

        assertEquals(new BlockPos(0, 64, 1), moved);
    }

    @Test
    void rightIsClockwiseOfFacing() {
        // North (facing 0): right is east (+X), the direction one quarter turn clockwise of north.
        BlockPos moved = WandMoves.move(ORIGIN, WandActions.Dir.RIGHT, 0);

        assertEquals(new BlockPos(1, 64, 0), moved);
    }

    @Test
    void upAndDownMoveOnYOnly() {
        assertEquals(new BlockPos(0, 65, 0), WandMoves.move(ORIGIN, WandActions.Dir.UP, 2));
        assertEquals(new BlockPos(0, 63, 0), WandMoves.move(ORIGIN, WandActions.Dir.DOWN, 2));
    }

    @Test
    void rotateWrapsBothWays() {
        assertEquals(0, WandMoves.rotate(3, true));
        assertEquals(3, WandMoves.rotate(0, false));
    }
}
