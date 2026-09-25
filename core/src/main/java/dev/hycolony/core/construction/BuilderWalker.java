package dev.hycolony.core.construction;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.kernel.port.CitizenBodies;
import dev.hycolony.core.kernel.port.NavStatus;

/** The builder's non-blocking walks (MC walkToBuilding / walkToConstructionSite), over the same port as wandering. */
final class BuilderWalker {
    static final int ARRIVAL_RANGE = 2;
    /** A work spot is kept while the next block is within 10 blocks of it. */
    static final long WORK_POS_REUSE_SQ = 10L * 10L;
    /** Work spots are 2 blocks out and 1 up from the block: within the 4-block work distance. */
    private static final int WORK_POS_OUT = 2;

    private final CitizenBodies bodies;
    private final BodyId body;
    private BlockPos navTarget;
    private BlockPos workPos;

    BuilderWalker(CitizenBodies bodies, BodyId body) {
        this.bodies = bodies;
        this.body = body;
    }

    void forgetWorkPos() {
        workPos = null;
    }

    /**
     * EntityAIStructureBuilder.walkToConstructionSite, simplified (MC asks a path job for a spot near the block): a
     * spot out from the block, away from {@code site}, kept while the next block is within 10 blocks of it.
     */
    boolean walkToWorkPos(BlockPos block, BlockPos site) {
        if (workPos == null || workPos.distSq(block) > WORK_POS_REUSE_SQ) {
            int dx = Integer.signum(block.x() - site.x());
            int dz = Integer.signum(block.z() - site.z());
            if (dx == 0 && dz == 0) {
                dx = 1;
            }
            workPos = block.offset(WORK_POS_OUT * dx, 1, WORK_POS_OUT * dz);
        }
        return walkTo(workPos);
    }

    /**
     * True once within {@link #ARRIVAL_RANGE} of {@code to}, or once the path to it ended (arrived, blocked or failed:
     * the builder works from where it got rather than stalling). Moves the body only when the target changes.
     */
    boolean walkTo(BlockPos to) {
        Vec3 p = bodies.position(body).orElse(null);
        if (p == null) {
            return false;
        }
        double dx = p.x() - (to.x() + 0.5), dy = p.y() - to.y(), dz = p.z() - (to.z() + 0.5);
        if (dx * dx + dy * dy + dz * dz <= ARRIVAL_RANGE * ARRIVAL_RANGE) {
            return true;
        }
        if (to.equals(navTarget)) {
            NavStatus s = bodies.navStatus(body);
            if (s == NavStatus.MOVING) {
                return false;
            }
            if (s != NavStatus.IDLE) {
                return true;
            }
        }
        navTarget = to;
        bodies.moveTo(body, Vec3.center(to));
        return false;
    }
}
