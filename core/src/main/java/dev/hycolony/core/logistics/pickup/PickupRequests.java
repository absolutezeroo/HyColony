package dev.hycolony.core.logistics.pickup;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.request.model.Pickup;
import dev.hycolony.core.request.model.RequestState;

/** Creates the pickup requests that send a courier to empty a hut (MC {@code AbstractBuilding.createPickupRequest}). */
public final class PickupRequests {
    /** MC {@code qty / 16}: every full 16 items brings the pickup one day earlier. */
    private static final int ITEMS_PER_DAY_EARLIER = 16;

    private PickupRequests() {}

    /**
     * Asks for a courier to collect about {@code qty} items from {@code building}, at priority 10 when {@code force},
     * else the building's pickup priority; due {@code day + max(0, (10 - pickupPriority) - qty / 16)} (MC uses the
     * building's priority there even when forced). Returns false, creating nothing, if the building already has an
     * open pickup or, unforced, its priority is 0 (MC checks that in the callers: the dump and the full-hut path).
     *
     * <p>MC's branch that merges {@code qty} into the open pickup never runs ({@code token instanceof Pickup} is
     * always false), so an open pickup simply makes this return false, as in MC.
     */
    public static boolean createPickupRequest(Colony colony, Building building, int qty, boolean force) {
        if (!force && building.pickupPriority() == 0) {
            return false;
        }
        boolean open = colony.requests().byRequester(building.requesterId()).stream()
                .anyMatch(r ->
                        r.requestable() instanceof Pickup && r.state().ordinal() < RequestState.COMPLETED.ordinal());
        if (open) {
            return false;
        }
        int priority = force ? Pickup.MAX_BUILDING_PRIORITY : building.pickupPriority();
        int delay =
                Math.max(0, (Pickup.MAX_BUILDING_PRIORITY - building.pickupPriority()) - qty / ITEMS_PER_DAY_EARLIER);
        colony.requests().createAndAssign(building, new Pickup(priority, colony.day() + delay, qty), -1);
        return true;
    }
}
