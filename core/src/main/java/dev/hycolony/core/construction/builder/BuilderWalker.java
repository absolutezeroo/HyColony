package dev.hycolony.core.construction.builder;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.nav.BodyWalker;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.kernel.port.CitizenBodies;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * The builder's walks (MC walkToBuilding / walkToConstructionSite) over a {@link BodyWalker}, plus its work spot. It
 * never teleports onto an unverified work spot (Deviation from MC, see {@link WorkSpot#choose}): that walk gives up
 * instead.
 */
final class BuilderWalker {
    private final BodyWalker walker;

    private WorkSpot.Spot workPos;
    /** The block the work spot was already chosen again for, because it was out of reach from the first one. */
    private BlockPos repickedFor;

    BuilderWalker(CitizenBodies bodies, BodyId body, LongSupplier clock) {
        this.walker = new BodyWalker(bodies, body, clock);
    }

    void forgetWorkPos() {
        workPos = null;
        repickedFor = null;
    }

    /** True while a walk is under way (for the citizen window). */
    boolean walking() {
        return walker.walking();
    }

    /**
     * EntityAIStructureBuilder.walkToConstructionSite: walks to the block's work spot and keeps it while the block is
     * within {@link WorkSpot#REACH} of where the builder stands; beyond, a new spot is chosen, once per block (a
     * block still out of reach from there is worked from where the builder got).
     *
     * <p>Deviation from MC: MC still works the out-of-reach block once before moving; here the builder moves first,
     * so it never works on a block more than 5 blocks away.
     */
    boolean walkToWorkPos(BlockPos block, Supplier<WorkSpot.Spot> spot) {
        if (workPos == null) {
            workPos = spot.get();
            repickedFor = null;
        }
        if (!walker.walkTo(workPos.pos(), workPos.verified())) {
            return false;
        }
        BlockPos at = walker.at();
        if (at == null || WorkSpot.inReach(at, block) || block.equals(repickedFor)) {
            return true;
        }
        repickedFor = block;
        workPos = spot.get();
        return walker.walkTo(workPos.pos(), workPos.verified());
    }

    /** {@link BodyWalker#walkTo(BlockPos)}. */
    boolean walkTo(BlockPos to) {
        return walker.walkTo(to);
    }
}
