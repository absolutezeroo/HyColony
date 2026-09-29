package dev.hycolony.core.kernel.nav;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.kernel.port.NavStatus;
import dev.hycolony.core.testing.FakeBodies;
import dev.hycolony.core.testing.FakeClock;
import java.util.List;
import org.junit.jupiter.api.Test;

/** MC EntityNavigationUtils.walkCloseToXNearY: a walk to a block ends only close to that block. */
class BodyWalkerTest {
    private static final BlockPos HUT = new BlockPos(0, 64, 0);
    private static final BlockPos STAND = new BlockPos(1, 64, 0);
    private static final Vec3 ROOF = new Vec3(0.5, 69, 0.5);

    private final FakeBodies bodies = new FakeBodies();
    private final FakeClock clock = new FakeClock();
    private final BodyId body = bodies.existing(1, 1, new Vec3(30, 64, 0));
    private final BodyWalker walker = new BodyWalker(bodies, body, () -> clock.tick);

    /** The nav ends ({@code status}) with the body at {@code where}. */
    private void navEnds(NavStatus status, Vec3 where) {
        bodies.bodies.get(body).position = where;
        bodies.bodies.get(body).status = status;
    }

    @Test
    void walkEndedOnTheRoofWalksAgain() {
        walker.walkCloseTo(STAND, HUT, 4, true);
        navEnds(NavStatus.ARRIVED, ROOF);

        assertFalse(walker.walkCloseTo(STAND, HUT, 4, true), "5 blocks above the hut is not at the hut");
        assertEquals(List.of(Vec3.center(STAND), Vec3.center(STAND)), bodies.moves);
    }

    @Test
    void walkEndedWithinReachOfTheBlockArrives() {
        walker.walkCloseTo(STAND, HUT, 4, true);
        navEnds(NavStatus.BLOCKED, new Vec3(-2.5, 64, 0.5));

        assertTrue(walker.walkCloseTo(STAND, HUT, 4, true));
    }

    @Test
    void wideReachAcceptsAWalkEndedSixBlocksAway() {
        walker.walkCloseTo(STAND, HUT, 7, true);
        navEnds(NavStatus.BLOCKED, new Vec3(-5.5, 64, 0.5));

        assertTrue(walker.walkCloseTo(STAND, HUT, 7, true));
    }

    @Test
    void walkEndedWithinAWideReachStillWalksForAShortOne() {
        walker.walkCloseTo(STAND, HUT, 7, true);
        navEnds(NavStatus.BLOCKED, new Vec3(-5.5, 64, 0.5));
        assertTrue(walker.walkCloseTo(STAND, HUT, 7, true));

        assertFalse(walker.walkCloseTo(STAND, HUT, 4, true), "MC checks the distance again: 6 blocks is not 4");
        assertEquals(2, bodies.moves.size());
    }

    @Test
    void walkEndedWithinReachStaysArrivedOnceTheNavReportsIdle() {
        walker.walkCloseTo(STAND, HUT, 4, true);
        navEnds(NavStatus.BLOCKED, new Vec3(-2.5, 64, 0.5));
        assertTrue(walker.walkCloseTo(STAND, HUT, 4, true));

        bodies.bodies.get(body).status = NavStatus.IDLE; // Hytale reports an ended nav once, then idle

        assertTrue(walker.walkCloseTo(STAND, HUT, 4, true));
        assertEquals(1, bodies.moves.size(), "no walk again once arrived");
    }

    @Test
    void walkLeavingItsReachAfterArrivingIsWatchedAnew() {
        walker.walkCloseTo(STAND, HUT, 7, true);
        navEnds(NavStatus.BLOCKED, new Vec3(-5.5, 64, 0.5));
        assertTrue(walker.walkCloseTo(STAND, HUT, 7, true));
        clock.tick += 5_000; // long after that walk started

        for (int i = 0; i < 50; i++) {
            clock.tick++;
            walker.walkCloseTo(STAND, HUT, 4, true);
            bodies.bodies.get(body).status = NavStatus.BLOCKED;
        }

        assertTrue(bodies.teleports.isEmpty(), "a walk starting again is not stuck at once");
        for (int i = 0; i < 400 && bodies.teleports.isEmpty(); i++) {
            clock.tick++;
            walker.walkCloseTo(STAND, HUT, 4, true);
            bodies.bodies.get(body).status = NavStatus.BLOCKED;
        }
        assertEquals(List.of(Vec3.center(STAND)), bodies.teleports, "watched once anew, it still ends");
    }

    @Test
    void bodyBesideTheBlockHasArrivedWithoutWalking() {
        bodies.bodies.get(body).position = new Vec3(1.5, 64, 0.5);

        assertTrue(walker.walkCloseTo(STAND, HUT, 4, true));
        assertTrue(bodies.moves.isEmpty());
    }

    @Test
    void walkStuckOnTheRoofTeleportsToTheStandingCell() {
        walker.walkCloseTo(STAND, HUT, 4, true);
        navEnds(NavStatus.BLOCKED, ROOF);

        for (int i = 0; i < 400 && bodies.teleports.isEmpty(); i++) {
            clock.tick++;
            walker.walkCloseTo(STAND, HUT, 4, true);
            bodies.bodies.get(body).status = NavStatus.BLOCKED;
        }

        assertEquals(List.of(Vec3.center(STAND)), bodies.teleports);
        assertTrue(walker.walkCloseTo(STAND, HUT, 4, true));
    }

    @Test
    void walkToAnUncheckedCellGivesUpRatherThanTeleport() {
        walker.walkCloseTo(HUT, HUT, 4, false);
        navEnds(NavStatus.BLOCKED, ROOF);

        boolean ended = false;
        for (int i = 0; i < 400 && !ended; i++) {
            clock.tick++;
            ended = walker.walkCloseTo(HUT, HUT, 4, false);
            bodies.bodies.get(body).status = NavStatus.BLOCKED;
        }

        assertTrue(ended, "no walk waits forever");
        assertTrue(bodies.teleports.isEmpty());
    }

    @Test
    void plainWalkStillEndsWhereverTheNavEnds() {
        walker.walkTo(HUT);
        navEnds(NavStatus.BLOCKED, ROOF);

        assertTrue(walker.walkTo(HUT));
    }

    @Test
    void walksCountsEachWalkToANewTarget() {
        walker.walkCloseTo(STAND, HUT, 4, true);
        walker.walkCloseTo(STAND, HUT, 4, true);
        int first = walker.walks();
        walker.walkTo(new BlockPos(20, 64, 0));

        assertEquals(first + 1, walker.walks());
    }
}
