package dev.hycolony.core.colony.action;

import dev.hycolony.core.colony.ColonyManager;
import dev.hycolony.core.colony.view.ColonyWindows;
import dev.hycolony.core.job.WorkerModule;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.port.Msg;
import dev.hycolony.core.logistics.pickup.PickupRequests;
import java.util.UUID;

/**
 * A hut window's pickup buttons (MC AbstractWindowWorkerModuleBuilding): the pickup priority ± and "force pickup".
 * Each needs MANAGE_HUTS and re-shows the hut's window.
 */
public final class LogisticsActions {
    /** MC Constants.STACKSIZE: the quantity a forced pickup announces. */
    static final int FORCED_PICKUP_QUANTITY = 64;

    private final ColonyManager manager;
    private final ColonyWindows windows;

    public LogisticsActions(ColonyManager manager, ColonyWindows windows) {
        this.manager = manager;
        this.windows = windows;
    }

    /**
     * MC ChangeDeliveryPriorityMessage: moves the hut's pickup priority one step up or down, within 0 to 10. False
     * without MANAGE_HUTS or for a hut without workers (MC changes it only for a WorkerBuildingModule).
     */
    public boolean alterPickupPriority(UUID player, BlockPos hutPos, boolean up) {
        ManagedHut h = ManagedHut.find(manager, player, hutPos)
                .filter(m -> m.building().module(WorkerModule.class).isPresent())
                .orElse(null);
        if (h == null) {
            return false;
        }
        h.building().pickupPriority().alter(up ? 1 : -1);
        h.colony().markDirty();
        windows.showBuilding(h.colony(), h.building(), player);
        return true;
    }

    /**
     * MC ForcePickupMessage: asks a courier to empty the hut now ({@code createPickupRequest(64, true)}) and tells the
     * player whether it was asked or one is already open. False without MANAGE_HUTS or when none was created.
     */
    public boolean forcePickup(UUID player, BlockPos hutPos) {
        ManagedHut h = ManagedHut.find(manager, player, hutPos).orElse(null);
        if (h == null) {
            return false;
        }
        boolean created = PickupRequests.createPickupRequest(h.colony(), h.building(), FORCED_PICKUP_QUANTITY, true);
        manager.context()
                .notifier()
                .send(player, Msg.of(created ? "hycolony.pickup.forced" : "hycolony.pickup.forceFailed"));
        if (created) {
            h.colony().markDirty();
        }
        windows.showBuilding(h.colony(), h.building(), player);
        return created;
    }
}
