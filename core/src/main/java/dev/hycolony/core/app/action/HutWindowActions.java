package dev.hycolony.core.app.action;

import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.app.view.ColonyWindows;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.job.WorkerModule;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.port.Msg;
import java.util.List;
import java.util.UUID;

/**
 * A hut window's frame and main page buttons (MC AbstractBuildingMainWindow, AbstractWindowWorkerModuleBuilding):
 * recall the workers, rename the hut, open its inventory. Each needs MANAGE_HUTS, as MC's building messages; a refusal
 * is told, a missing hut is a silent false.
 */
public final class HutWindowActions {
    /** MC WindowHutNameEntry.MAX_NAME_LENGTH: a longer name is cut to this many characters. */
    static final int NAME_CUT_LENGTH = 15;

    private final ColonyManager manager;
    private final ColonyWindows windows;
    private final LogisticsActions pickup;

    public HutWindowActions(ColonyManager manager, ColonyWindows windows) {
        this.manager = manager;
        this.windows = windows;
        this.pickup = new LogisticsActions(manager, windows);
    }

    /** The main page's pickup priority and "Request Pickup Now" buttons. */
    public LogisticsActions pickup() {
        return pickup;
    }

    /**
     * MC RecallCitizenMessage: every worker of the hut is teleported to it, a worker without a body gets one there; if
     * one could not appear, MC's {@code workerhuts.recallfail}. The hut shows again.
     */
    public boolean recallWorkers(UUID player, BlockPos hutPos) {
        ManagedHut h = ManagedHut.find(manager, player, hutPos).orElse(null);
        if (h == null) {
            return false;
        }
        List<Integer> workers = h.building()
                .module(WorkerModule.class)
                .map(WorkerModule::workers)
                .orElse(List.of());
        boolean failed = false;
        for (int id : workers) {
            failed |= !CitizenRecall.bring(manager, h.colony(), id, hutPos);
        }
        if (failed) {
            manager.context().notifier().send(player, Msg.of("hycolony.hut.recallFail"));
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
     * MC Manage Workers (AbstractWindowWorkerModuleBuilding, allowsAssignment) and Manage Housing (WindowHutLiving,
     * level above 0): whether the hut takes citizens yet; if not, {@code player} is told MC's
     * {@code workerhuts.level0}. MC checks this in the client without a right. False for a missing hut.
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
     * MC WindowHireWorker's mode button then BuildingHiringModeMessage: the hut's next hiring mode, LOCKED skipped (a
     * workplace cannot be locked); the hut shows again. False without the right or a worker module.
     */
    public boolean cycleHiring(UUID player, BlockPos hutPos) {
        ManagedHut h = ManagedHut.find(manager, player, hutPos).orElse(null);
        WorkerModule w =
                h == null ? null : h.building().module(WorkerModule.class).orElse(null);
        if (h == null || w == null) {
            return false;
        }
        w.setHiringMode(w.hiringMode().nextForWorkplace());
        h.colony().markDirty();
        windows.showBuilding(h.colony(), h.building(), player);
        return true;
    }

    /** MC OpenInventoryMessage: whether {@code player} may open the hut block's container (MANAGE_HUTS). */
    public boolean mayOpenInventory(UUID player, BlockPos hutPos) {
        return ManagedHut.find(manager, player, hutPos).isPresent();
    }
}
