package dev.hycolony.core.app.action;

import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.logistics.warehouse.CourierAssignmentModule;

/**
 * The hire window's Hire and Fire on a warehouse (MC HireFireMessage on its CourierAssignmentModule): a courier is
 * attached to or detached from the warehouse; its own job stays.
 */
final class CourierHiring {
    private CourierHiring() {}

    /**
     * MC CourierAssignmentModuleView.canAssign then assignCitizen: an adult courier attached to no other warehouse
     * joins while the warehouse has room (level × 2); true once attached.
     */
    static boolean hire(ManagedHut h, CourierAssignmentModule m, CitizenData citizen) {
        boolean free = CourierAssignmentModule.warehouseOf(h.colony(), citizen.id())
                .map(w -> w.position().equals(h.building().position()))
                .orElse(true);
        boolean attached = !citizen.isChild()
                && CourierAssignmentModule.isCourier(citizen)
                && free
                && m.couriers().size() < CourierAssignmentModule.maxCouriers(h.building())
                && m.attach(citizen.id());
        if (attached) {
            h.colony().markDirty();
        }
        return attached;
    }

    /** MC removeCitizen: the courier leaves the warehouse; false if it was not attached. */
    static boolean fire(ManagedHut h, CourierAssignmentModule m, int citizenId) {
        boolean detached = m.detach(citizenId);
        if (detached) {
            h.colony().markDirty();
        }
        return detached;
    }
}
