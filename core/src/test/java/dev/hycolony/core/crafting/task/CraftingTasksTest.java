package dev.hycolony.core.crafting.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.Resolver;
import dev.hycolony.core.request.model.RequestState;
import dev.hycolony.core.request.model.RequestToken;
import dev.hycolony.core.request.resolver.RetryingResolver;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CraftingTasksTest {
    private final TaskRig rig = new TaskRig();

    private String resolverOf(RequestToken token) {
        return rig.requests().resolverOf(token).map(Resolver::resolverId).orElseThrow();
    }

    @Test
    void scheduledThenResolvedTaskMovesToTheQueue() {
        CraftingTasks tasks = new CraftingTasks();
        RequestToken task = RequestToken.random();

        tasks.onTaskBeingScheduled(task);
        assertEquals(List.of(task), tasks.assignedTasks());
        assertEquals(List.of(), tasks.taskQueue());

        tasks.onTaskBeingResolved(task);
        assertEquals(List.of(), tasks.assignedTasks());
        assertEquals(List.of(task), tasks.taskQueue());
    }

    @Test
    void taskWaitingForItsIngredientsStaysScheduled() {
        rig.ask(3, 8);

        assertEquals(1, rig.tasks.taskQueue().size(), "3 runs: the essence is there, the task is queued");
        assertEquals(1, rig.tasks.assignedTasks().size(), "8 runs: waits for its essence");
        assertEquals(2, rig.tasks.load(), "MC: queue plus assigned tasks");
    }

    @Test
    void deletedTaskLeavesEitherList() {
        CraftingTasks tasks = new CraftingTasks();
        RequestToken queued = RequestToken.random();
        RequestToken scheduled = RequestToken.random();
        tasks.onTaskBeingResolved(queued);
        tasks.onTaskBeingScheduled(scheduled);

        assertTrue(tasks.onTaskDeletion(scheduled));
        assertTrue(tasks.onTaskDeletion(queued));
        assertFalse(tasks.onTaskDeletion(queued));
        assertEquals(0, tasks.load());
    }

    @Test
    void deadHeadTokensArePopped() {
        rig.ask(3);
        RequestToken live = rig.tasks.taskQueue().getFirst();
        CraftingTasks tasks = new CraftingTasks();
        tasks.onTaskBeingResolved(RequestToken.random());
        tasks.onTaskBeingResolved(RequestToken.random());
        tasks.onTaskBeingResolved(live);
        rig.colony.clearDirty();

        assertEquals(live, tasks.currentTask(rig.colony).map(Request::token).orElseThrow());
        assertEquals(List.of(live), tasks.taskQueue());
        assertTrue(rig.colony.isDirty());
    }

    @Test
    void queueOfDeadTokensEndsEmpty() {
        CraftingTasks tasks = new CraftingTasks();
        tasks.onTaskBeingResolved(RequestToken.random());

        assertEquals(Optional.empty(), tasks.currentTask(rig.colony), "MC loops forever on an emptied queue");
        assertEquals(List.of(), tasks.taskQueue());
    }

    @Test
    void finishRequestResolvesTheHead() {
        RequestToken parent = rig.ask(3, 2);
        RequestToken head = rig.tasks.taskQueue().getFirst();

        rig.tasks.finishRequest(rig.colony, true);

        assertEquals(List.of(head), rig.production.completed, "RESOLVED: its resolver completes it");
        assertTrue(rig.requests().get(head).isEmpty(), "received by its parent");
        assertEquals(1, rig.requests().get(parent).orElseThrow().children().size());
        assertEquals(1, rig.tasks.taskQueue().size());
        assertFalse(rig.tasks.taskQueue().contains(head));
    }

    @Test
    void failedHeadHandsItsParentBack() {
        RequestToken parent = rig.ask(3);
        RequestToken head = rig.tasks.taskQueue().getFirst();

        rig.tasks.finishRequest(rig.colony, false);

        assertEquals(RequestState.FAILED, rig.production.cancelled.get(head));
        assertTrue(rig.requests().get(head).isEmpty());
        RequestToken again = rig.requests().get(parent).orElseThrow().children().getFirst();
        assertNotEquals(head, again);
        assertEquals(List.of(again), rig.tasks.taskQueue(), "the parent was assigned again");
    }

    @Test
    void finishingADeadHeadPopsIt() {
        CraftingTasks tasks = new CraftingTasks();
        tasks.onTaskBeingResolved(RequestToken.random());

        tasks.finishRequest(rig.colony, true);

        assertEquals(List.of(), tasks.taskQueue(), "MC throws on an unknown token");
    }

    /** Review focus 3: nothing may stay stuck when the crafter leaves in the middle of its tasks. */
    @Test
    void firingTheCrafterFailsItsTasks() {
        RequestToken first = rig.ask(3);
        RequestToken second = rig.ask(8);
        RequestToken queued = rig.tasks.taskQueue().getFirst();
        RequestToken waiting = rig.tasks.assignedTasks().getFirst();
        rig.splitter.open = false; // the hut lost its crafter: its resolver declines from now on

        rig.tasks.cancelAll(rig.colony);

        assertEquals(Map.of(queued, RequestState.FAILED, waiting, RequestState.FAILED), rig.production.cancelled);
        assertEquals(0, rig.tasks.load());
        for (RequestToken parent : List.of(first, second)) {
            assertEquals(List.of(), rig.requests().get(parent).orElseThrow().children());
            assertEquals(RetryingResolver.ID, resolverOf(parent), "handed back to the request system");
        }
        assertEquals(2, rig.requests().all().size(), "the tasks and the essence asked for are gone");
    }

    @Test
    void siblingTaskTakenAlongIsSkipped() {
        RequestToken parent = rig.ask(3, 8);
        rig.splitter.open = false;

        rig.tasks.cancelAll(rig.colony);

        assertEquals(
                1,
                rig.production.cancelled.values().stream()
                        .filter(RequestState.FAILED::equals)
                        .count(),
                "the first failure cancels its sibling; MC throws on the gone token");
        assertEquals(0, rig.tasks.load());
        assertEquals(RetryingResolver.ID, resolverOf(parent));
    }

    @Test
    void countersAreReadIntoTheirOwnFields() {
        CraftingTasks tasks = new CraftingTasks();
        tasks.setMaxCraftingCount(4);
        tasks.setCraftCounter(2);
        tasks.setProgress(7);

        CraftingTasks back = reload(tasks);

        assertEquals(4, back.maxCraftingCount(), "MC deserializeNBT reads it into progress");
        assertEquals(2, back.craftCounter(), "MC deserializeNBT reads it into progress");
        assertEquals(7, back.progress());
    }

    @Test
    void tasksAndSecondaryOutputsSurviveSaveAndLoad() {
        CraftingTasks tasks = new CraftingTasks();
        RequestToken a = RequestToken.random();
        RequestToken b = RequestToken.random();
        RequestToken c = RequestToken.random();
        tasks.onTaskBeingResolved(a);
        tasks.onTaskBeingResolved(b);
        tasks.onTaskBeingScheduled(c);
        tasks.secondaryOutputs().merge(new ItemKey("Container_Bucket"), 3, Integer::sum);

        CraftingTasks back = reload(tasks);

        assertEquals(List.of(a, b), back.taskQueue());
        assertEquals(List.of(c), back.assignedTasks());
        assertEquals(Map.of(new ItemKey("Container_Bucket"), 3), back.secondaryOutputs());
    }

    @Test
    void malformedSaveReadsWhatItCan() {
        RequestToken kept = RequestToken.random();
        CraftingTasks back = new CraftingTasks();
        back.setProgress(9);

        back.read(JsonParser.parseString("""
                        {"queue": ["not-a-token", 5, "%s"], "assignedTasks": 3, "progress": "x",
                         "secondaryOutputs": [{"item": "A"}, {"item": "B", "count": 2}, {"item": "C", "count": 0}, 7]}""".formatted(kept.id())).getAsJsonObject());

        assertEquals(List.of(kept), back.taskQueue());
        assertEquals(List.of(), back.assignedTasks());
        assertEquals(0, back.progress(), "a bad value reads as the default");
        assertEquals(Map.of(new ItemKey("B"), 2), back.secondaryOutputs());
    }

    private static CraftingTasks reload(CraftingTasks tasks) {
        JsonObject saved = JsonParser.parseString(tasks.write().toString()).getAsJsonObject();
        CraftingTasks back = new CraftingTasks();
        back.read(saved);
        return back;
    }
}
