package dev.hycolony.core.citizen.vitals;

import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.CitizenState;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.nav.StuckHandler;

/**
 * A citizen's debug events, posted on the world's bus only while someone listens: one per walk end, stuck action or
 * state change, too many to build for nobody.
 */
public final class CitizenDebugEvents {
    private CitizenDebugEvents() {}

    /** A walk of {@code citizen} ended, as {@code end} tells. */
    public record WalkEnded(Colony colony, CitizenData citizen, EndedWalk end) {}

    /** The stuck handler took {@code action} on a walk to {@code target}, the body at {@code at}. */
    public record StuckActed(
            Colony colony, CitizenData citizen, BlockPos target, Vec3 at, StuckHandler.Action action) {}

    /** The citizen AI went from {@code from} to {@code to}. */
    public record AiStateChanged(Colony colony, CitizenData citizen, CitizenState from, CitizenState to) {}

    /** The job's AI went from the step {@code from} to {@code to} ({@code ""} for none). */
    public record JobStepChanged(Colony colony, CitizenData citizen, String from, String to) {}
}
