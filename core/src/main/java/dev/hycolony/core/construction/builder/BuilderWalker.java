package dev.hycolony.core.construction.builder;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.colony.BlockApproach;
import dev.hycolony.core.colony.GamePorts;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.nav.BodyWalker;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.kernel.port.CitizenBodies;
import java.util.Optional;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * The builder's walks (MC walkToBuilding / walkToConstructionSite) over a {@link BodyWalker}, plus its work spot. It
 * never teleports onto an unverified work spot (Deviation from MC, see {@link WorkSpot#choose}): that walk gives up
 * instead.
 */
final class BuilderWalker {
    private final BodyWalker walker;
    private final BlockApproach approach;

    private WorkSpot.@Nullable Spot workPos;
    /** The block the work spot was already chosen again for, because it was out of reach from the first one. */
    private @Nullable BlockPos repickedFor;

    BuilderWalker(CitizenBodies bodies, BodyId body, LongSupplier clock, GamePorts ports) {
        this.walker = new BodyWalker(bodies, body, clock);
        this.approach = new BlockApproach(ports, walker);
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
        Optional<BlockPos> at = walker.at();
        if (at.isEmpty() || WorkSpot.inReach(at.get(), block) || block.equals(repickedFor)) {
            return true;
        }
        repickedFor = block;
        workPos = spot.get();
        return walker.walkTo(workPos.pos(), workPos.verified());
    }

    /** MC walkToBuilding: true once beside the hut block ({@link BlockApproach}). */
    boolean walkToBuilding(Building hut) {
        return approach.walkToBuilding(hut);
    }

    /** MC walkToWorkPos: true once within {@link BlockApproach#WORK_IN_BUILDING_REACH} of {@code pos} in the hut. */
    boolean walkToPosInBuilding(BlockPos pos, Building hut) {
        return approach.walkToPosInBuilding(pos, hut, BlockApproach.WORK_IN_BUILDING_REACH);
    }
}
