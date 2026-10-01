package dev.hydomum.core.connect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** MC WallBlock.updateShape: tall sides and the raised post from the block above. */
class WallTopTest {
    private static final Footprint FULL = new Footprint(List.of(new Footprint.Rect(0, 0, 1, 1)));
    // A fence post, 6 to 10 sixteenths, with a thin arm north, seen from below.
    private static final Footprint FENCE_END_NORTH = new Footprint(List.of(
            new Footprint.Rect(6 / 16.0, 6 / 16.0, 10 / 16.0, 10 / 16.0),
            new Footprint.Rect(7 / 16.0, 0, 9 / 16.0, 6 / 16.0)));
    private static final Footprint LANTERN = new Footprint(List.of(new Footprint.Rect(0.3, 0.3, 0.7, 0.7)));

    private static Set<Side> sides(Side... sides) {
        return sides.length == 0 ? EnumSet.noneOf(Side.class) : EnumSet.of(sides[0], sides);
    }

    @Test
    void nothingAboveLeavesLowSidesAndAPostOnlyWhereAnArmLacksItsOpposite() {
        assertEquals(new WallLook(sides(), false), WallTop.of(sides(Side.EAST, Side.WEST), Footprint.NONE, false));
        assertEquals(new WallLook(sides(), true), WallTop.of(sides(), Footprint.NONE, false));
        assertEquals(new WallLook(sides(), true), WallTop.of(sides(Side.NORTH), Footprint.NONE, false));
        assertEquals(new WallLook(sides(), true), WallTop.of(sides(Side.NORTH, Side.EAST), Footprint.NONE, false));
        assertEquals(new WallLook(sides(), false), WallTop.of(EnumSet.allOf(Side.class), Footprint.NONE, false));
    }

    @Test
    void aFullBlockAboveMakesEveryJoinedSideTallAndAStraightRunPostless() {
        assertEquals(
                new WallLook(sides(Side.EAST, Side.WEST), false), WallTop.of(sides(Side.EAST, Side.WEST), FULL, false));
        assertEquals(new WallLook(sides(Side.NORTH), true), WallTop.of(sides(Side.NORTH), FULL, false));
    }

    @Test
    void aFenceAboveRaisesTheTopUnderItsPostAndArm() {
        // The user's case: a wall end against a house, a fence on it reaching the house too.
        assertEquals(new WallLook(sides(Side.NORTH), true), WallTop.of(sides(Side.NORTH), FENCE_END_NORTH, false));
        // A straight run under the fence's post only: post raised, sides low.
        assertEquals(new WallLook(sides(), true), WallTop.of(sides(Side.EAST, Side.WEST), FENCE_END_NORTH, false));
    }

    @Test
    void aLanternOrAWallPostAboveRaisesAStraightRunsPost() {
        assertEquals(new WallLook(sides(), true), WallTop.of(sides(Side.NORTH, Side.SOUTH), LANTERN, false));
        assertEquals(new WallLook(sides(), true), WallTop.of(sides(Side.NORTH, Side.SOUTH), Footprint.NONE, true));
    }

    @Test
    void aWallPostAboveRaisesThePostEvenOverATallLine() {
        // A T on a straight run: its arms make both sides tall, its post still raises the one below (MC checks it
        // first).
        assertEquals(
                new WallLook(sides(Side.EAST, Side.WEST), true), WallTop.of(sides(Side.EAST, Side.WEST), FULL, true));
    }

    @Test
    void onlyTheSideWhoseBandIsCoveredTurnsTall() {
        Footprint eastHalf = new Footprint(List.of(new Footprint.Rect(0.4, 0.4, 1, 0.6)));
        assertEquals(new WallLook(sides(Side.EAST), true), WallTop.of(sides(Side.EAST, Side.WEST), eastHalf, false));
        Footprint westHalf = new Footprint(List.of(new Footprint.Rect(0, 0.4, 0.6, 0.6)));
        assertEquals(new WallLook(sides(Side.WEST), true), WallTop.of(sides(Side.EAST, Side.WEST), westHalf, false));
    }

    @Test
    void aFootprintCoversARectOnlyWhenItsRectsFillIt() {
        Footprint split = new Footprint(List.of(new Footprint.Rect(0, 0, 0.5, 1), new Footprint.Rect(0.5, 0, 1, 1)));
        assertTrue(split.covers(new Footprint.Rect(0.4, 0.1, 0.6, 0.2)));
        assertFalse(LANTERN.covers(new Footprint.Rect(0.2, 0.4, 0.5, 0.5)));
        assertFalse(Footprint.NONE.covers(WallTop.POST));
    }
}
