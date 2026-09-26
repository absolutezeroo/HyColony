package dev.hycolony.core.kernel.nav;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.item.BlockKey;
import dev.hycolony.core.kernel.item.BlockKind;
import dev.hycolony.core.kernel.item.BlockState;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.kernel.port.NavStatus;
import dev.hycolony.core.testing.FakeBodies;
import dev.hycolony.core.testing.FakeCatalog;
import dev.hycolony.core.testing.FakeWorldBlocks;
import java.util.List;
import org.junit.jupiter.api.Test;

class DetouringBodiesTest {
    private static final BlockState FIRE = new BlockState(new BlockKey("fire"), 0);
    private static final Vec3 TO = new Vec3(10.5, 64, 0.5);

    private final FakeWorldBlocks world = new FakeWorldBlocks();
    private final FakeCatalog catalog = new FakeCatalog();
    private final FakeBodies fake = new FakeBodies();
    private final DetouringBodies bodies = new DetouringBodies(fake, world, catalog);
    private final BodyId body = fake.existing(1, 1, new Vec3(0.5, 64, 0.5));

    DetouringBodiesTest() {
        catalog.kinds.put(FIRE.key(), BlockKind.NON_SOLID);
        catalog.harmful.add(FIRE.key());
        world.blocks.put(new BlockPos(5, 64, 0), FIRE);
    }

    @Test
    void walksTheDetourWaypointsOneByOneThenReportsArrival() {
        fake.instant = true;

        bodies.moveTo(body, TO);
        NavStatus status = NavStatus.MOVING;
        for (int i = 0; i < 20 && status == NavStatus.MOVING; i++) {
            status = bodies.navStatus(body);
        }

        assertEquals(NavStatus.ARRIVED, status);
        assertTrue(fake.moves.size() > 1, "waypoints before the target: " + fake.moves);
        assertEquals(TO, fake.moves.getLast());
    }

    @Test
    void movesOnWhenCloseToAWaypointTheNavNeverReaches() {
        bodies.moveTo(body, TO);
        Vec3 waypoint = fake.moves.getFirst();
        fake.bodies.get(body).position = new Vec3(waypoint.x() + 0.5, waypoint.y() + 2, waypoint.z()); // on a hill

        assertEquals(NavStatus.MOVING, bodies.navStatus(body));
        assertEquals(2, fake.moves.size(), "the next waypoint is asked for");
    }

    @Test
    void lookAtDropsTheRemainingDetour() {
        bodies.moveTo(body, TO);
        bodies.lookAt(body, TO);
        fake.bodies.get(body).status = NavStatus.ARRIVED;

        assertEquals(NavStatus.ARRIVED, bodies.navStatus(body));
        assertEquals(List.of(fake.moves.getFirst()), fake.moves, "no further waypoint");
    }

    @Test
    void neverCutsTheCornerIntoTheFireWhenCloseToAWaypoint() {
        bodies.moveTo(body, TO);
        fake.bodies.get(body).position = new Vec3(4.55, 64, 1.30); // 1 block short of the first waypoint, by the fire

        assertEquals(NavStatus.MOVING, bodies.navStatus(body));
        assertEquals(1, fake.moves.size(), "keeps seeking the waypoint: the line to the target crosses the fire");
    }

    @Test
    void replansFromWhereTheNavStoppedWhenTheNextLegCrossesTheFire() {
        bodies.moveTo(body, TO);
        fake.bodies.get(body).position = new Vec3(4.55, 64, 1.30);
        fake.bodies.get(body).status = NavStatus.ARRIVED;

        assertEquals(NavStatus.MOVING, bodies.navStatus(body));
        Vec3 next = fake.moves.getLast();
        assertTrue(!next.equals(TO), "a new detour leg, not the straight line through the fire: " + fake.moves);
        assertTrue(new SafeRoute(new DangerousCells(world, catalog))
                .clear(new Vec3(4.55, 64, 1.30), next, RouteSearch.BODY_RADIUS));
    }

    @Test
    void takesTheNextLegAsItIsAfterMaxReplansAndMoveToResetsTheCount() {
        for (int walk = 0; walk < 2; walk++) {
            bodies.moveTo(body, TO);
            fake.bodies.get(body).position = new Vec3(4.55, 64, 1.30); // stuck short: the line to the target burns
            for (int i = 0; i < DetouringBodies.MAX_REPLANS; i++) {
                arrive();
                assertTrue(!fake.moves.getLast().equals(TO), "replan " + (i + 1) + " is a detour: " + fake.moves);
            }
            arrive();
            Vec3 secondLeg = new SafeRoute(new DangerousCells(world, catalog))
                    .plan(new Vec3(4.55, 64, 1.30), TO)
                    .waypoints()
                    .get(1);
            assertEquals(secondLeg, fake.moves.getLast(), "past the cap, the last replan's next leg as it is");
        }
    }

    @Test
    void walksAOneBlockGapInAWallOfFireWaypointByWaypointWithoutReplanning() {
        wallOfFireWithAGapAt(2);
        fake.frozen = true;
        List<Vec3> plan = new SafeRoute(new DangerousCells(world, catalog))
                .plan(new Vec3(0.5, 64, 0.5), TO)
                .waypoints();

        bodies.moveTo(body, TO);
        for (int i = 1; i < plan.size(); i++) {
            fake.bodies.get(body).position = fake.moves.getLast(); // on the waypoint, the nav not done yet
            assertEquals(NavStatus.MOVING, bodies.navStatus(body));
        }

        assertTrue(plan.size() > 1, "a detour through the gap: " + plan);
        assertEquals(plan, fake.moves, "each waypoint handed over as planned, none replanned");
    }

    @Test
    void aRefusedNextLegIsCheckedAgainOnlyOnceTheBodyChangesBlock() {
        wallOfFireWithAGapAt(2);
        fake.frozen = true;
        bodies.moveTo(body, TO);
        Vec3 waypoint = fake.moves.getFirst();
        // in the waypoint's block, pressed against the fire at z = 1: the next leg burns
        fake.bodies.get(body).position = new Vec3(waypoint.x(), waypoint.y(), 2.1);

        bodies.navStatus(body);
        int reads = world.reads;
        bodies.navStatus(body);

        assertEquals(1, fake.moves.size(), "the next leg is refused: " + fake.moves);
        assertEquals(reads, world.reads, "no new check in the same block");
    }

    private void wallOfFireWithAGapAt(int gapZ) {
        for (int z = -30; z <= 30; z++) {
            if (z != gapZ) {
                world.blocks.put(new BlockPos(5, 64, z), FIRE);
            }
        }
    }

    private void arrive() {
        fake.bodies.get(body).status = NavStatus.ARRIVED;
        assertEquals(NavStatus.MOVING, bodies.navStatus(body));
    }
}
