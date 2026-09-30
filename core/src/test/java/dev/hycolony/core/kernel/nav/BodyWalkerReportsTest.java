package dev.hycolony.core.kernel.nav;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.kernel.port.NavStatus;
import dev.hycolony.core.testing.FakeBodies;
import dev.hycolony.core.testing.FakeClock;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;

/** A walker tells how its walks go, once each: what the debug tools show, and what reveals a walk ending on a roof. */
class BodyWalkerReportsTest {
    private static final BlockPos HUT = new BlockPos(0, 64, 0);
    private static final BlockPos STAND = new BlockPos(1, 64, 0);
    private static final Vec3 ROOF = new Vec3(0.5, 69, 0.5);

    private final FakeBodies bodies = new FakeBodies();
    private final FakeClock clock = new FakeClock();
    private final BodyId body = bodies.existing(1, 1, new Vec3(30, 64, 0));
    private final List<String> heard = new ArrayList<>();
    private final BodyWalker walker = new BodyWalker(bodies, body, () -> clock.tick, new WalkListener() {
        @Override
        public void walkStarted(BlockPos target, Vec3 from) {
            heard.add("start " + target.x());
        }

        @Override
        public void walkEnded(BlockPos target, Vec3 at, WalkEnd how, double distance, NavStatus nav) {
            heard.add(how + " " + Math.round(distance)
                    + (how == WalkEnd.NAV_ENDED || how == WalkEnd.GAVE_UP ? " " + nav : ""));
        }

        @Override
        public void stuck(BlockPos target, Vec3 at, StuckHandler.Action action) {
            heard.add("stuck " + action);
        }
    });

    /** Calls {@code walk} each tick until it says arrived, the nav ending blocked wherever the body stands. */
    private void walkBlocked(BooleanSupplier walk) {
        for (int i = 0; i < 400 && !walk.getAsBoolean(); i++) {
            clock.tick++;
            if (bodies.bodies.get(body).status == NavStatus.MOVING) {
                bodies.bodies.get(body).status = NavStatus.BLOCKED;
            }
        }
    }

    @Test
    void plainWalkEndedOnTheRoofSaysHowFarItEndedOnce() {
        bodies.navEndsAt = ROOF;

        walker.walkTo(HUT);
        assertTrue(walker.walkTo(HUT));
        assertTrue(walker.walkTo(HUT));

        assertEquals(List.of("start 0", "NAV_ENDED 5 ARRIVED"), heard);
    }

    @Test
    void closeWalkEndsCloseWithoutWaitingForTheNav() {
        walker.walkCloseTo(STAND, HUT, 4, true);
        bodies.bodies.get(body).position = new Vec3(1.5, 64, 0.5);

        assertTrue(walker.walkCloseTo(STAND, HUT, 4, true));
        walker.walkCloseTo(STAND, HUT, 4, true);

        assertEquals(List.of("start 1", "CLOSE 1"), heard);
    }

    @Test
    void walkStuckOnTheRoofTellsEachStuckActionThenItsTeleportedEnd() {
        bodies.navEndsAt = ROOF;
        bodies.navEndStatus = NavStatus.BLOCKED;

        walkBlocked(() -> walker.walkCloseTo(STAND, HUT, 4, true));

        assertEquals(List.of("start 1", "stuck REPATH", "stuck TELEPORT", "TELEPORTED 1"), heard);
    }

    @Test
    void walkWithinItsRangeEndsCloseOnce() {
        BlockPos column = new BlockPos(20, 64, 0);
        walker.walkTo(column, 4);
        bodies.bodies.get(body).position = new Vec3(17.5, 64, 0.5);

        assertTrue(walker.walkTo(column, 4));
        assertTrue(walker.walkTo(column, 4));

        assertEquals(List.of("start 20", "CLOSE 3"), heard);
    }

    @Test
    void walkLeavingItsReachAfterArrivingIsHeardAsANewWalk() {
        walker.walkCloseTo(STAND, HUT, 7, true);
        bodies.bodies.get(body).position = new Vec3(-5.5, 64, 0.5);
        bodies.bodies.get(body).status = NavStatus.BLOCKED;
        assertTrue(walker.walkCloseTo(STAND, HUT, 7, true));

        walker.walkCloseTo(STAND, HUT, 4, true); // 6 blocks is not 4: it walks again

        assertEquals(List.of("start 1", "NAV_ENDED 6 BLOCKED", "start 1"), heard);
    }

    @Test
    void walkAfterATeleportedOneEndsCloseAgain() {
        bodies.navEndsAt = ROOF;
        bodies.navEndStatus = NavStatus.BLOCKED;
        walkBlocked(() -> walker.walkCloseTo(STAND, HUT, 4, true));
        bodies.navEndsAt = null;
        BlockPos other = new BlockPos(10, 64, 0);
        BlockPos beside = new BlockPos(11, 64, 0);

        walker.walkCloseTo(beside, other, 4, true);
        bodies.bodies.get(body).position = new Vec3(11.5, 64, 0.5);
        walker.walkCloseTo(beside, other, 4, true);

        assertEquals(List.of("start 11", "CLOSE 1"), heard.subList(heard.size() - 2, heard.size()));
    }

    @Test
    void walkToAnUncheckedCellGivesUpAndSaysSo() {
        bodies.navEndsAt = ROOF;
        bodies.navEndStatus = NavStatus.BLOCKED;

        walkBlocked(() -> walker.walkCloseTo(HUT, HUT, 4, false));

        assertEquals(List.of("start 0", "stuck REPATH", "stuck GIVE_UP", "GAVE_UP 5 BLOCKED"), heard);
    }
}
