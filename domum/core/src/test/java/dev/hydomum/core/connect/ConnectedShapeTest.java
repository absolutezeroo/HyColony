package dev.hydomum.core.connect;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The HyDomum fence template's shape and yaw for each set of joined sides. */
class ConnectedShapeTest {
    private static ConnectedShape shape(Side... sides) {
        return ConnectedShape.of(sides.length == 0 ? EnumSet.noneOf(Side.class) : EnumSet.of(sides[0], sides));
    }

    @Test
    void aBlockWithNoJoinedSideIsALonePost() {
        assertEquals(new ConnectedShape("Post", 0), shape());
    }

    @Test
    void anEndPointsItsArmAtItsOnlyNeighbour() {
        // The End reaches north at yaw 0; each yaw turns north to west, west to south, south to east.
        assertEquals(new ConnectedShape("End", 0), shape(Side.NORTH));
        assertEquals(new ConnectedShape("End", 1), shape(Side.WEST));
        assertEquals(new ConnectedShape("End", 2), shape(Side.SOUTH));
        assertEquals(new ConnectedShape("End", 3), shape(Side.EAST));
    }

    @Test
    void aStraightRunLiesAlongItsTwoOppositeNeighbours() {
        assertEquals(new ConnectedShape("Straight", 0), shape(Side.EAST, Side.WEST));
        assertEquals(new ConnectedShape("Straight", 1), shape(Side.NORTH, Side.SOUTH));
    }

    @Test
    void aCornerTurnsLikeTheVanillaCorner() {
        // Same yaws as the blueprint converter's CORNER_YAW, checked in game.
        assertEquals(new ConnectedShape("Corner", 0), shape(Side.WEST, Side.SOUTH));
        assertEquals(new ConnectedShape("Corner", 1), shape(Side.SOUTH, Side.EAST));
        assertEquals(new ConnectedShape("Corner", 2), shape(Side.EAST, Side.NORTH));
        assertEquals(new ConnectedShape("Corner", 3), shape(Side.NORTH, Side.WEST));
    }

    @Test
    void aJunctionOpensAwayFromItsMissingSide() {
        assertEquals(new ConnectedShape("T_Junction", 0), shape(Side.EAST, Side.WEST, Side.SOUTH));
        assertEquals(new ConnectedShape("T_Junction", 1), shape(Side.NORTH, Side.SOUTH, Side.EAST));
        assertEquals(new ConnectedShape("T_Junction", 2), shape(Side.EAST, Side.WEST, Side.NORTH));
        assertEquals(new ConnectedShape("T_Junction", 3), shape(Side.NORTH, Side.SOUTH, Side.WEST));
        assertEquals(new ConnectedShape("Cross_Junction", 0), shape(Side.values()));
    }

    @Test
    void everySetOfSidesHasAShape() {
        for (int mask = 0; mask < 16; mask++) {
            Set<Side> sides = EnumSet.noneOf(Side.class);
            for (Side side : Side.values()) {
                if ((mask & (1 << side.ordinal())) != 0) {
                    sides.add(side);
                }
            }
            ConnectedShape shape = ConnectedShape.of(sides);
            assertEquals(sides, Side.turned(ConnectedShape.sidesOf(shape.name()), shape.yaw()), sides.toString());
        }
    }
}
