package dev.hycolony.core.app.action;

import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.app.restaurant.RestaurantActions;
import dev.hycolony.core.app.view.ColonyWindows;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.module.HutSettings;
import dev.hycolony.core.job.WorkerModule;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.port.Msg;
import dev.hycolony.core.logistics.warehouse.CourierAssignmentModule;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A hut window's buttons (MC AbstractBuildingMainWindow, AbstractWindowWorkerModuleBuilding, WindowHireWorker,
 * SettingsModuleWindow): recall, rename, the hiring mode, the settings, the inventory, the pickup buttons
 * ({@link #pickup}). Each needs MANAGE_HUTS, as MC's building messages, a refusal told; {@link #mayAssign} needs none,
 * as MC checks it in the client. A missing hut is a silent false.
 */
public final class HutWindowActions {
    /** MC WindowHutNameEntry.MAX_NAME_LENGTH: a longer name is cut to this many characters. */
    static final int NAME_CUT_LENGTH = 15;

    private final ColonyManager manager;
    private final ColonyWindows windows;
    private final LogisticsActions pickup;
    private final RestaurantActions restaurant;

    public HutWindowActions(ColonyManager manager, ColonyWindows windows) {
        this.manager = manager;
        this.windows = windows;
        this.pickup = new LogisticsActions(manager, windows);
        this.restaurant = new RestaurantActions(manager, windows);
    }

    /** The main page's pickup priority and "Request Pickup Now" buttons. */
    public LogisticsActions pickup() {
        return pickup;
    }

    /** A dining hall's Menu and Fuel tabs. */
    public RestaurantActions restaurant() {
        return restaurant;
    }

    /**
     * MC RecallCitizenMessage: every citizen the hut holds (its workers, or a warehouse's couriers, MC
     * getAllAssignedCitizen) is teleported to it, one without a body gets one there; MC's {@code workerhuts.recallfail}
     * for each one who could not appear. The hut shows again.
     */
    public boolean recallWorkers(UUID player, BlockPos hutPos) {
        ManagedHut h = ManagedHut.find(manager, player, hutPos).orElse(null);
        if (h == null) {
            return false;
        }
        List<Integer> assigned = h.building()
                .module(WorkerModule.class)
                .map(WorkerModule::workers)
                .or(() -> h.building().module(CourierAssignmentModule.class).map(CourierAssignmentModule::couriers))
                .orElse(List.of());
        for (int id : assigned) {
            if (!CitizenRecall.bring(manager, h.colony(), id, hutPos)) {
                manager.context().notifier().send(player, Msg.of("hycolony.hut.recallFail"));
            }
        }
        windows.showBuilding(h.colony(), h.building(), player);
        return true;
    }

    /**
     * MC WindowHutNameEntry then HutRenameMessage: the hut takes the typed name, cut to 15 characters with MC's
     * {@code gui.name.toolong} message; an empty name shows the type's name again. The hut shows again.
     */
    public boolean rename(UUID player, BlockPos hutPos, String typed) {
        ManagedHut h = ManagedHut.find(manager, player, hutPos).orElse(null);
        if (h == null) {
            return false;
        }
        String name = typed;
        if (name.length() > NAME_CUT_LENGTH) {
            name = name.substring(0, NAME_CUT_LENGTH);
            manager.context().notifier().send(player, Msg.of("hycolony.gui.name.toolong", name));
        }
        h.building().setCustomName(name);
        h.colony().markDirty();
        windows.showBuilding(h.colony(), h.building(), player);
        return true;
    }

    /**
     * MC Manage Workers (AbstractWindowWorkerModuleBuilding, allowsAssignment; SpecialAssignmentModuleWindow for the
     * warehouse's couriers) and Manage Housing (WindowHutLiving, level above 0): whether the hut takes citizens yet;
     * if not, {@code player} is told MC's {@code workerhuts.level0}. MC checks this in the client without a right.
     * False for a missing hut.
     */
    public boolean mayAssign(UUID player, BlockPos hutPos) {
        Building b =
                manager.colonyAt(hutPos).flatMap(c -> c.buildings().at(hutPos)).orElse(null);
        if (b == null) {
            return false;
        }
        boolean takes =
                b.module(WorkerModule.class).map(w -> w.canAssignCitizens(b)).orElse(b.level() > 0);
        if (!takes) {
            manager.context().notifier().send(player, Msg.of("hycolony.hut.notBuiltYet"));
        }
        return takes;
    }

    /**
     * MC WindowHireWorker's mode button then BuildingHiringModeMessage (or CourierHiringModeMessage for a warehouse):
     * the hut's next hiring mode, LOCKED skipped (only homes lock); the hut shows again. False without the right or
     * an assignment module.
     */
    public boolean cycleHiring(UUID player, BlockPos hutPos) {
        ManagedHut h = ManagedHut.find(manager, player, hutPos).orElse(null);
        if (h == null) {
            return false;
        }
        Optional<WorkerModule> w = h.building().module(WorkerModule.class);
        Optional<CourierAssignmentModule> couriers = h.building().module(CourierAssignmentModule.class);
        if (w.isPresent()) {
            w.get().setHiringMode(w.get().hiringMode().nextForWorkplace());
        } else if (couriers.isPresent()) {
            couriers.get().setHiringMode(couriers.get().hiringMode().nextForWorkplace());
        } else {
            return false;
        }
        h.colony().markDirty();
        windows.showBuilding(h.colony(), h.building(), player);
        return true;
    }

    /**
     * MC TriggerSettingMessage (MANAGE_HUTS): the hut's active BOOL or STRING setting {@code id} turns over or moves
     * on, then the hut shows again. False without the right, for an unknown, inactive or BLOCK setting.
     */
    public boolean triggerSetting(UUID player, BlockPos hutPos, String id) {
        ManagedHut h = ManagedHut.find(manager, player, hutPos).orElse(null);
        if (h == null) {
            return false;
        }
        boolean changed = h.building().modules().values().stream()
                .filter(HutSettings.class::isInstance)
                .map(HutSettings.class::cast)
                .filter(s -> s.settingRows(h.colony()).stream().anyMatch(r -> r.id().equals(id) && r.active()))
                .anyMatch(s -> s.trigger(id));
        if (changed) {
            h.colony().markDirty();
            windows.showBuilding(h.colony(), h.building(), player);
        }
        return changed;
    }

    /** MC OpenInventoryMessage: whether {@code player} may open the hut block's container (MANAGE_HUTS). */
    public boolean mayOpenInventory(UUID player, BlockPos hutPos) {
        return ManagedHut.find(manager, player, hutPos).isPresent();
    }
}
