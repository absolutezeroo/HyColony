package dev.hycolony.core.construction.builder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.testing.FakeBodies;
import java.util.List;
import org.junit.jupiter.api.Test;

class BuilderWalkerTest {
    private static final BlockPos BLOCK = new BlockPos(20, 0, 0);
    private static final BlockPos SPOT = new BlockPos(22, 1, 0);

    private final FakeBodies bodies = new FakeBodies();
    private final BodyId body = bodies.existing(1, 1, Vec3.center(new BlockPos(0, 1, 0)));
    private long now;
    private final BuilderWalker walker = new BuilderWalker(bodies, body, () -> now);

    BuilderWalkerTest() {
        bodies.frozen = true; // moveTo never moves the body, navStatus stays MOVING
    }

    /** Walks to the block's work spot every tick; true once the walk ended within {@code ticks}. */
    private boolean walk(WorkSpot.Spot spot, int ticks) {
        for (now = 0; now <= ticks; now++) {
            if (walker.walkToWorkPos(BLOCK, () -> spot)) {
                return true;
            }
        }
        return false;
    }

    @Test
    void neverTeleportsOntoAnUnverifiedSpotAndGivesUpInstead() {
        assertTrue(walk(new WorkSpot.Spot(SPOT, false), 2000), "the walk ended");

        assertEquals(List.of(), bodies.teleports);
        assertEquals(Vec3.center(new BlockPos(0, 1, 0)), bodies.position(body).orElseThrow(), "works from where it is");
    }

    @Test
    void teleportsOntoAVerifiedSpot() {
        assertTrue(walk(new WorkSpot.Spot(SPOT, true), 2000), "the walk ended");

        assertEquals(List.of(Vec3.center(SPOT)), bodies.teleports);
    }
}
