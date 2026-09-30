package dev.hycolony.core.app.action;

import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ColonyAccess;
import dev.hycolony.core.colony.permission.Action;
import dev.hycolony.core.kernel.item.Inventory;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.port.Msg;
import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.RequestManager;
import dev.hycolony.core.request.model.RequestState;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A player in a citizen's inventory (MC OpenInventoryMessage.doCitizenInventory and ContainerCitizenInventory): opening
 * it, and what a stack they put there does to the citizen's requests.
 */
public final class CitizenInventoryActions {
    private final ColonyManager manager;

    public CitizenInventoryActions(ColonyManager manager) {
        this.manager = manager;
    }

    /**
     * Opens the citizen's inventory window if {@code player} may MANAGE_HUTS (MC AbstractColonyServerMessage default
     * permission), else tells them. An unknown colony or citizen is ignored.
     *
     * <p>Deviation from MC: opens even when the citizen's body is not loaded; MC needs the entity, here the
     * inventory lives in the core.
     */
    public void open(UUID player, int colonyId, int citizenId) {
        Colony c = manager.byId(colonyId).orElse(null);
        if (c == null || c.citizens().get(citizenId).isEmpty()) {
            return;
        }
        if (!ColonyAccess.allows(c, player, Action.MANAGE_HUTS)) {
            manager.context().notifier().send(player, Msg.of("hycolony.permission.denied", c.name()));
            return;
        }
        manager.windows().ui().openCitizenInventory(player, colonyId, citizenId);
    }

    /**
     * A player's move changed the citizen's inventory from {@code before}: each slot that was empty or now holds another
     * item is a stack the player put there (MC ContainerCitizenInventory slot {@code set}), offered to the citizen's
     * workplace. A stack only topped up is not (MC {@code moveItemStackTo} grows it and calls {@code setChanged}, not
     * {@code set}). Marks the colony to save; a citizen gone does nothing, one without workplace overrules nothing.
     *
     * <p>Deviation from MC: Hytale has no per-slot {@code set} callback, so a put is read from the slot's content; MC
     * also calls {@code set} with the rest of a stack shift-clicked out, which overrules nothing here.
     */
    public void onPlayerEdit(int colonyId, int citizenId, Inventory before) {
        Colony c = manager.byId(colonyId).orElse(null);
        if (c == null) {
            return;
        }
        CitizenData d = c.citizens().get(citizenId).orElse(null);
        if (d == null) {
            return;
        }
        c.markDirty();
        Building work =
                Optional.ofNullable(d.workBuilding()).flatMap(c.buildings()::at).orElse(null);
        if (work == null) {
            return;
        }
        Inventory now = d.inventory();
        for (int i = 0; i < now.size() && i < before.size(); i++) {
            Optional<ItemAmount> is = now.slot(i);
            if (is.isPresent() && placed(before.slot(i), is.get())) {
                overruleNextOpenRequestOfCitizenWithStack(c, work, d, is.get());
            }
        }
    }

    /** Whether a slot that held {@code was} now holding {@code is} means the player put a stack there. */
    private static boolean placed(Optional<ItemAmount> was, ItemAmount is) {
        return was.isEmpty() || !was.get().item().equals(is.item());
    }

    /**
     * MC AbstractBuilding.overruleNextOpenRequestOfCitizenWithStack: the first IN_PROGRESS request {@code work} made for
     * the citizen that {@code stack} matches is overruled with it, as delivered to the citizen. Only the building
     * itself makes requests for a citizen here, so MC's "requester is the building or one of its resolvers" is the
     * building alone.
     *
     * <p>Deviation from MC: no crafter fallback (a crafter's task children); there is no crafter job yet.
     */
    private void overruleNextOpenRequestOfCitizenWithStack(Colony c, Building work, CitizenData d, ItemAmount stack) {
        RequestManager m = c.requests();
        for (Request r : m.byRequester(work.requesterId())) {
            if (r.citizenId() == d.id()
                    && r.state() == RequestState.IN_PROGRESS
                    && r.deliverable()
                            .filter(wanted -> wanted.matches(stack, m.catalog())) // never a broken tool
                            .isPresent()) {
                m.overrule(r.token(), List.of(stack), true);
                return;
            }
        }
    }
}
