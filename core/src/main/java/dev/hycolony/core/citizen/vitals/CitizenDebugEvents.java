package dev.hycolony.core.citizen.vitals;

import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.CitizenState;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.nav.StuckHandler;
import dev.hycolony.core.kernel.nav.WalkEnd;
import dev.hycolony.core.kernel.port.NavStatus;

/**
 * A citizen's debug events, posted on the world's bus only while someone listens: one per walk end, stuck action or
 * state change, too many to build for nobody.
 */
public final class CitizenDebugEvents {
    private CitizenDebugEvents() {}

    /**
     * A walk to {@code target} ended {@code how}, the body at {@code at}, {@code distance} blocks from its goal;
     * {@code nav} is the nav's status when it ended the walk, {@code MOVING} when the walk ended without it.
     */
    public record WalkEnded(
            Colony colony,
            CitizenData citizen,
            BlockPos target,
            Vec3 at,
            WalkEnd how,
            double distance,
            NavStatus nav) {}

    /** The stuck handler took {@code action} on a walk to {@code target}, the body at {@code at}. */
    public record StuckActed(
            Colony colony, CitizenData citizen, BlockPos target, Vec3 at, StuckHandler.Action action) {}

    /** The citizen AI went from {@code from} to {@code to}. */
    public record AiStateChanged(Colony colony, CitizenData citizen, CitizenState from, CitizenState to) {}

    /** The job's AI went from the step {@code from} to {@code to} ({@code ""} for none). */
    public record JobStepChanged(Colony colony, CitizenData citizen, String from, String to) {}
}
