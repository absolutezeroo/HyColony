package dev.hycolony.core.colony.action;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ColonyManager;
import dev.hycolony.core.colony.permission.Action;
import dev.hycolony.core.job.Job;
import dev.hycolony.core.kernel.item.Inventory;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.port.Msg;
import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.RequestManager;
import dev.hycolony.core.request.model.RequestState;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
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
        if (!c.permissions().hasPermission(player, Action.MANAGE_HUTS)) {
            manager.context().notifier().send(player, Msg.of("hycolony.permission.denied", c.name()));
            return;
        }
        manager.context().ui().openCitizenInventory(player, colonyId, citizenId);
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
        CitizenData d = c == null ? null : c.citizens().get(citizenId).orElse(null);
        if (d == null) {
            return;
        }
        c.markDirty();
        Building work = d.workBuilding() == null
                ? null
                : c.buildings().at(d.workBuilding()).orElse(null);
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

    /**
     * Share of {@code tool}'s durability the citizen's job has left on it, 1 new to 0 worn out, for the window to show
     * the real wear. Empty for a gone citizen, a job that does not wear tools, or an unbreakable item.
     */
    public OptionalDouble toolCondition(int colonyId, int citizenId, ItemKey tool) {
        int durability = manager.context().ports().catalog().durability(tool);
        Optional<Job> job = job(colonyId, citizenId);
        if (durability <= 0 || job.isEmpty()) {
            return OptionalDouble.empty();
        }
        return job.get().toolUses(tool).stream()
                .mapToDouble(uses -> Math.max(0, 1 - (double) uses / durability))
                .findFirst();
    }

    /**
     * The player put {@code tool} with {@code condition} (as {@link #toolCondition}) in the citizen's inventory: its
     * job's use count follows it, a partly used step counting as a whole so it is never repaired. The core counts
     * wear per item kind, so when the citizen holds another of that kind the worst wear stays. A gone citizen, a job
     * that does not wear tools or an unbreakable item does nothing.
     */
    public void toolPutIn(int colonyId, int citizenId, ItemKey tool, double condition) {
        int durability = manager.context().ports().catalog().durability(tool);
        Job job = job(colonyId, citizenId).orElse(null);
        if (durability <= 0 || job == null) {
            return;
        }
        // Tolerance: a stack read back from toolCondition must give its uses again, not one more.
        int uses = (int) Math.ceil((1 - condition) * durability - 1e-6);
        int held = job.citizen().inventory().count(tool);
        int kept = job.toolUses(tool).orElse(0);
        job.setToolUses(tool, held > 1 ? Math.max(kept, uses) : Math.max(0, uses));
    }

    private Optional<Job> job(int colonyId, int citizenId) {
        return manager.byId(colonyId).flatMap(c -> c.citizens().get(citizenId)).flatMap(CitizenData::job);
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
                            .filter(wanted -> wanted.matches(stack.item(), m.catalog()))
                            .isPresent()) {
                m.overrule(r.token(), List.of(stack), true);
                return;
            }
        }
    }
}
