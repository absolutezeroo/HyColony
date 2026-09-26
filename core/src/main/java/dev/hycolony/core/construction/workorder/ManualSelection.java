package dev.hycolony.core.construction.workorder;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.construction.shared.BuilderHut;
import java.util.Optional;

/**
 * A player picks a work order for a builder hut from its Work orders tab. Port of MC BuildingBuilder.setWorkOrder
 * (BuilderSelectWorkOrderMessage); the tab offers it in MANUAL mode only, the server does not check the mode.
 */
public final class ManualSelection {
    /** Why the builder may not take the order, in the order MC checks it (its MESSAGE_WARNING_* keys). */
    public enum Refusal {
        NO_WORKER,
        NOT_FOR_BUILDER,
        ALREADY_CLAIMED,
        CANNOT_BUILD
    }

    private ManualSelection() {}

    /**
     * Why {@code hut} may not claim {@code o}, or empty if it may.
     *
     * <p>Deviation from MC: setWorkOrder skips canBuildIgnoringDistance when the builder already holds an order (it
     * only queues the new one); we always check it, so a builder never queues an order above its level.
     */
    public static Optional<Refusal> check(Building hut, WorkOrder o) {
        if (!BuilderHut.is(hut)) {
            return Optional.of(Refusal.NOT_FOR_BUILDER);
        }
        if (!WorkManager.isEmployedBuilder(hut)) {
            return Optional.of(Refusal.NO_WORKER);
        }
        if (o.claimedBy().isPresent()) {
            return Optional.of(Refusal.ALREADY_CLAIMED);
        }
        if (!WorkManager.canBuildIgnoringDistance(hut.position(), hut.level(), o)) {
            return Optional.of(Refusal.CANNOT_BUILD);
        }
        return Optional.empty();
    }

    /**
     * {@code hut} claims order {@code orderId} if {@link #check} allows it; a builder that already holds an order
     * queues it (it works on its lowest id). Returns the refusal, or empty once claimed.
     */
    public static Optional<Refusal> select(Colony c, Building hut, int orderId) {
        WorkOrder o = c.work().byId(orderId).orElse(null);
        if (o == null) {
            return Optional.of(Refusal.NOT_FOR_BUILDER); // MC: not an IBuilderWorkOrder, as for a missing one
        }
        Optional<Refusal> refusal = check(hut, o);
        if (refusal.isEmpty()) {
            o.setClaimedBy(hut.position());
            c.markDirty();
        }
        return refusal;
    }
}
