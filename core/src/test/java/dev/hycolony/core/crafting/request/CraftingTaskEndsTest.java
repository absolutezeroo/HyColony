package dev.hycolony.core.crafting.request;

import static dev.hycolony.core.crafting.request.CraftingRig.ESSENCE;
import static dev.hycolony.core.crafting.request.CraftingRig.SEEDS;
import static dev.hycolony.core.crafting.request.CraftingRig.tasksOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.job.WorkerModule;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.RequestManager;
import dev.hycolony.core.request.model.Crafting;
import dev.hycolony.core.request.resolver.PlayerResolver;
import dev.hycolony.core.request.resolver.RetryingResolver;
import dev.hycolony.core.testing.TestJobs;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** How a crafting task ends when its crafter goes away, and that none of these ends loops. */
class CraftingTaskEndsTest {
    /** Far more requests than any of these stories makes: past it, the request system is looping. */
    private static final int ENDLESS = 50;

    private final CraftingRig rig = new CraftingRig();
    private int created;

    @BeforeEach
    void countRequests() {
        m().setCreationListener(_ -> assertTrue(++created < ENDLESS, "the request system loops"));
    }

    private RequestManager m() {
        return rig.m();
    }

    /** Review focus 3, with the real resolvers: the parent goes back to the request system, not to this hut. */
    @Test
    void firingTheCrafterFailsItsTasksAndTheParentLeavesTheHut() {
        CitizenData crafter = rig.benchSeedsWithACrafter();
        Request parent = rig.ask(rig.other, SEEDS, 10, 10);
        Request task = rig.task(parent);

        rig.h.hut.module(WorkerModule.class).orElseThrow().fire(rig.h.colony, rig.h.hut, crafter.id());

        assertTrue(m().get(task.token()).isEmpty(), "failed with its crafter");
        assertEquals(RetryingResolver.ID, rig.resolverOf(parent), "no worker left, the hut does not take it again");
        assertTrue(m().all().stream().noneMatch(r -> r.requestable() instanceof Crafting));
    }

    /**
     * MC canBuildingCraftStack: without a crafter, the public production resolver does not take the task, which then
     * waits for the player; it is never cancelled and asked again in a loop.
     */
    @Test
    void workerWhoIsNoCrafterLeavesTheTaskWaitingWithoutLooping() {
        CitizenData worker = rig.benchSeedsWithACrafter();
        worker.setJob(TestJobs.TYPE.factory().apply(worker));

        Request parent = rig.ask(rig.other, SEEDS, 10, 10);
        Request task = rig.task(parent);
        for (int tick = 0; tick < 3000; tick++) {
            rig.h.t.clock.tick++;
            rig.h.colony.tick();
        }

        assertEquals(PlayerResolver.ID, rig.resolverOf(task));
        assertEquals(List.of(task.token()), parent.children());
        assertEquals(2, m().all().size());
    }

    /**
     * MC resolveForBuilding: a task no crafter holds any more once its ingredients are there is cancelled; its parent
     * asks again and the new task goes to the crafter.
     */
    @Test
    void taskNoCrafterHoldsIsCancelledAndItsParentAsksAgain() {
        CitizenData crafter = rig.benchSeedsWithACrafter();
        Request parent = rig.ask(rig.other, SEEDS, 10, 10);
        Request task = rig.task(parent);
        tasksOf(crafter).onTaskDeletion(task.token());

        m().overrule(task.children().getFirst(), List.of(new ItemAmount(ESSENCE, 20)), false);

        assertTrue(m().get(task.token()).isEmpty(), "cancelled");
        Request again = rig.task(parent);
        assertNotEquals(task.token(), again.token());
        assertEquals(List.of(again.token()), tasksOf(crafter).assignedTasks());
    }
}
