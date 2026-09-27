package dev.hycolony.core.logistics.pickup;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.port.ContainerAccess;
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
        int hutPriority = building.pickupPriority().value();
        if (!force && hutPriority == 0) {
            return false;
        }
        boolean open = colony.requests().byRequester(building.requesterId()).stream()
                .anyMatch(r -> r.requestable() instanceof Pickup && r.state().isBefore(RequestState.COMPLETED));
        if (open) {
            return false;
        }
        int priority = force ? Pickup.MAX_BUILDING_PRIORITY : hutPriority;
        int delay = Math.max(0, (Pickup.MAX_BUILDING_PRIORITY - hutPriority) - qty / ITEMS_PER_DAY_EARLIER);
        colony.requests().createAndAssign(building, new Pickup(priority, colony.day() + delay, qty), -1);
        return true;
    }

    /**
     * The pickup a worker's dump into {@code hut} asks for (MC {@code AbstractEntityAIBasic.dumpInventory}): forced
     * when the hut is full ({@link #isFull}), else unforced when {@code dumped > 0}; nothing at pickup priority 0.
     *
     * <p>Deviation from MC: our dump stores the whole inventory in one pass, so fullness is checked once after it.
     * MC checks it before each slot; a hut filled by the very last slot then gets an unforced pickup, here a forced
     * one. No "inventory full chest" chat line (no interaction system yet).
     */
    public static void afterDump(Colony colony, Building hut, int dumped) {
        if (hut.pickupPriority().value() <= 0) {
            return;
        }
        if (isFull(colony, hut)) {
            createPickupRequest(colony, hut, dumped, true);
        } else if (dumped > 0) {
            createPickupRequest(colony, hut, dumped, false);
        }
    }

    /** MC {@code InventoryUtils.isBuildingFull}: no container of the hut has a free slot (unloaded ones count full). */
    private static boolean isFull(Colony colony, Building hut) {
        ContainerAccess containers = colony.context().ports().containers();
        for (BlockPos pos : hut.containers()) {
            if (containers.freeSlots(pos) > 0) {
                return false;
            }
        }
        return true;
    }
}
