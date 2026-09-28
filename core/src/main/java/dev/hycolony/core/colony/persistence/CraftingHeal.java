package dev.hycolony.core.colony.persistence;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.crafting.module.CraftingModule;
import dev.hycolony.core.crafting.module.CraftingModules;
import dev.hycolony.core.crafting.task.Crafter;
import dev.hycolony.core.crafting.task.CraftingTasks;
import dev.hycolony.core.request.model.RequestToken;
import java.util.ArrayList;
import java.util.List;

/**
 * Repairs the crafting state a loaded save left dangling: drops a hut's learnt recipe the registry lost (a Hytale
 * recipe the game no longer has, a malformed entry), as MC AbstractCraftingBuildingModule.serializeToView does, and a
 * crafter's task whose request is gone. Deviation from MC: MC keeps such a task, and a scheduled one then counts in
 * the crafter's load forever.
 *
 * <p>The huts' registered benches are not checked: the load has no world, where an unloaded chunk and a missing bench
 * look alike.
 */
final class CraftingHeal {
    private CraftingHeal() {}

    /** Heals every crafting module and every crafter of the colony; returns whether anything changed. */
    static boolean heal(Colony c) {
        boolean changed = false;
        for (Building b : c.buildings().all()) {
            for (CraftingModule module : CraftingModules.of(b)) {
                changed |= module.retainRecipes(id -> c.recipes().get(id).isPresent());
            }
        }
        for (CitizenData d : c.citizens().all()) {
            if (d.job().orElse(null) instanceof Crafter crafter) {
                changed |= dropTasksOfGoneRequests(c, crafter.craftingTasks());
            }
        }
        return changed;
    }

    /**
     * Drops the queued and scheduled tasks whose request the colony no longer has (MC onTaskDeletion). A dead queue
     * head would be popped by {@link CraftingTasks#currentTask}, but a dead scheduled task would stay forever and count
     * in the crafter's load.
     */
    private static boolean dropTasksOfGoneRequests(Colony c, CraftingTasks tasks) {
        List<RequestToken> all = new ArrayList<>(tasks.taskQueue());
        all.addAll(tasks.assignedTasks());
        boolean changed = false;
        for (RequestToken token : all) {
            if (c.requests().get(token).isEmpty()) {
                changed |= tasks.onTaskDeletion(token);
            }
        }
        return changed;
    }
}
