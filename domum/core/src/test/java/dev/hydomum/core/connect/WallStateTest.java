package dev.hydomum.core.connect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The name of a HyDomum wall's state: its template shape, tall sides at yaw 0, and an optional raised post. */
class WallStateTest {
    private static WallLook look(boolean post, Side... tall) {
        Set<Side> sides = tall.length == 0 ? EnumSet.noneOf(Side.class) : EnumSet.of(tall[0], tall);
        return new WallLook(sides, post);
    }

    @Test
    void aWallWithLowSidesKeepsItsShapesName() {
        assertEquals("Straight", WallState.name(new ConnectedShape("Straight", 1), look(false)));
        assertEquals("T_Junction", WallState.name(new ConnectedShape("T_Junction", 2), look(true)));
        assertEquals("Post", WallState.name(new ConnectedShape("Post", 0), look(true)));
    }

    @Test
    void tallSidesAreNamedAtYawZero() {
        // An end at yaw 3 reaches east; its tall arm, east in the world, is north at yaw 0.
        assertEquals("End_TallN", WallState.name(new ConnectedShape("End", 3), look(true, Side.EAST)));
        // A straight run at yaw 1 lies north-south (east turns north): its tall north arm is east at yaw 0.
        assertEquals("Straight_TallE", WallState.name(new ConnectedShape("Straight", 1), look(false, Side.NORTH)));
        assertEquals(
                "Cross_Junction_TallNESW",
                WallState.name(new ConnectedShape("Cross_Junction", 0), look(false, Side.values())));
    }

    @Test
    void onlyAStraightRunOrACrossNamesItsRaisedPost() {
        assertEquals("Straight_Up", WallState.name(new ConnectedShape("Straight", 0), look(true)));
        assertEquals(
                "Cross_Junction_TallE_Up",
                WallState.name(new ConnectedShape("Cross_Junction", 0), look(true, Side.EAST)));
        assertEquals("Corner", WallState.name(new ConnectedShape("Corner", 0), look(true)));
    }

    @Test
    void aStateNamesWhetherItsPostIsRaised() {
        assertTrue(WallState.hasPost("Post"));
        assertTrue(WallState.hasPost("End_TallN"));
        assertTrue(WallState.hasPost("T_Junction"));
        assertTrue(WallState.hasPost("Straight_TallE_Up"));
        assertFalse(WallState.hasPost("Straight"));
        assertFalse(WallState.hasPost("Cross_Junction_TallNS"));
    }
}
