package dev.hycolony.core.kernel.nav;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.item.BlockKey;
import dev.hycolony.core.kernel.item.BlockKind;
import dev.hycolony.core.kernel.item.BlockState;
import dev.hycolony.core.testing.FakeCatalog;
import dev.hycolony.core.testing.FakeWorldBlocks;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SafeRouteTest {
    private static final BlockState FIRE = new BlockState(new BlockKey("fire"), 0);
    private static final Vec3 FROM = new Vec3(0.5, 64, 0.5);
    private static final Vec3 TO = new Vec3(10.5, 64, 0.5);

    private final FakeWorldBlocks world = new FakeWorldBlocks();
    private final FakeCatalog catalog = new FakeCatalog();
    private final SafeRoute route = new SafeRoute(new DangerousCells(world, catalog));

    SafeRouteTest() {
        catalog.kinds.put(FIRE.key(), BlockKind.NON_SOLID);
        catalog.harmful.add(FIRE.key());
    }

    @Test
    void clearLineGoesStraightToTheTarget() {
        assertEquals(List.of(TO), route.plan(FROM, TO).waypoints());
    }

    @Test
    void walksAroundAFireOnTheStraightLine() {
        world.blocks.put(new BlockPos(5, 64, 0), FIRE);

        List<Vec3> plan = route.plan(FROM, TO).waypoints();

        assertTrue(plan.size() > 1, "a detour: " + plan);
        assertEquals(TO, plan.getLast());
        assertNeverTouches(plan, 5, 0);
    }

    @Test
    void walksAroundACampfireUnderTheFeetLevel() {
        world.blocks.put(new BlockPos(5, 63, 0), FIRE);
        world.blocks.put(new BlockPos(5, 63, 1), FIRE);

        List<Vec3> plan = route.plan(FROM, TO).waypoints();

        assertEquals(TO, plan.getLast());
        assertNeverTouches(plan, 5, 0);
        assertNeverTouches(plan, 5, 1);
    }

    @Test
    void detourAroundASingleFireKeepsTheBodyAboutABlockAway() {
        world.blocks.put(new BlockPos(5, 64, 0), FIRE);

        List<Vec3> plan = route.plan(FROM, TO).waypoints();

        assertEquals(TO, plan.getLast());
        // 0.9 rather than 1.0: the line checks sample every quarter block
        assertNeverWithin(plan, 5, 0, 0.9);
    }

    @Test
    void goesThroughAOneBlockGapBetweenFiresWhenItIsTheOnlyWay() {
        for (int z = -30; z <= 30; z++) {
            if (z != 2) {
                world.blocks.put(new BlockPos(5, 64, z), FIRE);
            }
        }

        List<Vec3> plan = route.plan(FROM, TO).waypoints();

        assertTrue(plan.size() > 1, "a detour through the gap: " + plan);
        assertEquals(TO, plan.getLast());
        for (int z = -30; z <= 30; z++) {
            if (z != 2) {
                assertNeverTouches(plan, 5, z);
            }
        }
    }

    @Test
    void keepsTheStraightWalkWhenAWallOfFireHasNoGap() {
        for (int z = -30; z <= 30; z++) {
            world.blocks.put(new BlockPos(5, 64, z), FIRE);
        }

        assertEquals(List.of(TO), route.plan(FROM, TO).waypoints(), "no detour in reach: Hytale's nav decides");
    }

    /** Samples every segment of the walk, body width included, and fails if one touches column (x, z). */
    private static void assertNeverTouches(List<Vec3> plan, int x, int z) {
        assertNeverWithin(plan, x, z, 0.3);
    }

    /** Samples every segment of the walk and fails if its centre comes within {@code reach} of column (x, z). */
    private static void assertNeverWithin(List<Vec3> plan, int x, int z, double reach) {
        List<Vec3> points = new ArrayList<>(List.of(FROM));
        points.addAll(plan);
        for (int i = 1; i < points.size(); i++) {
            Vec3 a = points.get(i - 1), b = points.get(i);
            for (int s = 0; s <= 100; s++) {
                double px = a.x() + (b.x() - a.x()) * s / 100, pz = a.z() + (b.z() - a.z()) * s / 100;
                for (double dx : new double[] {-reach, 0, reach}) {
                    for (double dz : new double[] {-reach, 0, reach}) {
                        boolean hit = Math.floor(px + dx) == x && Math.floor(pz + dz) == z;
                        assertTrue(!hit, "segment " + a + " -> " + b + " crosses (" + x + ", " + z + ")");
                    }
                }
            }
        }
    }
}
