package dev.hydomum.core.connect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** MC FenceBlock.connectsTo, WallBlock.connectsTo and IronBarsBlock.attachsTo. */
class ConnectionsTest {
    private static Neighbour of(NeighbourKind kind) {
        return new Neighbour(kind, false, 0);
    }

    private static boolean joins(Joiner joiner, NeighbourKind kind) {
        return Connections.joins(joiner, Side.NORTH, of(kind));
    }

    @Test
    void woodenFencesJoinWoodenFencesOnlyAndOtherFencesTheirOwn() {
        assertTrue(joins(Joiner.WOODEN_FENCE, NeighbourKind.WOODEN_FENCE));
        assertFalse(joins(Joiner.WOODEN_FENCE, NeighbourKind.FENCE));
        assertTrue(joins(Joiner.FENCE, NeighbourKind.FENCE));
        assertFalse(joins(Joiner.FENCE, NeighbourKind.WOODEN_FENCE));
        assertFalse(joins(Joiner.WOODEN_FENCE, NeighbourKind.WALL));
        assertFalse(joins(Joiner.FENCE, NeighbourKind.PANE));
    }

    @Test
    void wallsAndBarsJoinEachOtherButNotFences() {
        for (Joiner joiner : new Joiner[] {Joiner.WALL, Joiner.PANE}) {
            assertTrue(joins(joiner, NeighbourKind.WALL), joiner.name());
            assertTrue(joins(joiner, NeighbourKind.PANE), joiner.name());
            assertFalse(joins(joiner, NeighbourKind.WOODEN_FENCE), joiner.name());
            assertFalse(joins(joiner, NeighbourKind.FENCE), joiner.name());
        }
    }

    @Test
    void everyFamilyJoinsAFullFaceButNotAnyOtherBlock() {
        for (Joiner joiner : Joiner.values()) {
            assertTrue(Connections.joins(joiner, Side.EAST, new Neighbour(NeighbourKind.OTHER, true, 0)));
            assertFalse(joins(joiner, NeighbourKind.OTHER), joiner.name());
        }
    }

    @Test
    void fencesAndWallsJoinAGateOnlyFromItsSides() {
        // A gate's sides face east and west at yaw 0, north and south a quarter turn on.
        Neighbour gate = new Neighbour(NeighbourKind.GATE, false, 0);
        Neighbour turned = new Neighbour(NeighbourKind.GATE, false, 1);
        for (Joiner joiner : new Joiner[] {Joiner.WOODEN_FENCE, Joiner.FENCE, Joiner.WALL}) {
            assertTrue(Connections.joins(joiner, Side.EAST, gate), joiner.name());
            assertTrue(Connections.joins(joiner, Side.WEST, gate), joiner.name());
            assertFalse(Connections.joins(joiner, Side.NORTH, gate), joiner.name());
            assertTrue(Connections.joins(joiner, Side.NORTH, turned), joiner.name());
            assertFalse(Connections.joins(joiner, Side.EAST, turned), joiner.name());
        }
    }

    @Test
    void eachFamilyIsSeenAsItsOwnNeighbourKind() {
        assertEquals(NeighbourKind.WOODEN_FENCE, Joiner.WOODEN_FENCE.asNeighbour());
        assertEquals(NeighbourKind.FENCE, Joiner.FENCE.asNeighbour());
        assertEquals(NeighbourKind.WALL, Joiner.WALL.asNeighbour());
        assertEquals(NeighbourKind.PANE, Joiner.PANE.asNeighbour());
        for (Joiner joiner : Joiner.values()) {
            // Every family joins its own.
            assertTrue(Connections.joins(joiner, Side.NORTH, of(joiner.asNeighbour())), joiner.name());
        }
    }

    @Test
    void barsDoNotJoinAGate() {
        assertFalse(Connections.joins(Joiner.PANE, Side.EAST, new Neighbour(NeighbourKind.GATE, false, 0)));
    }

    @Test
    void joinedSidesKeepsTheSidesWhoseNeighbourJoins() {
        Map<Side, Neighbour> around = Map.of(
                Side.NORTH,
                of(NeighbourKind.WOODEN_FENCE),
                Side.EAST,
                new Neighbour(NeighbourKind.OTHER, true, 0),
                Side.SOUTH,
                of(NeighbourKind.WALL),
                Side.WEST,
                new Neighbour(NeighbourKind.GATE, false, 0));

        assertEquals(
                EnumSet.of(Side.NORTH, Side.EAST, Side.WEST), Connections.joinedSides(Joiner.WOODEN_FENCE, around));
        assertEquals(EnumSet.of(Side.EAST, Side.SOUTH), Connections.joinedSides(Joiner.PANE, around));
    }
}
