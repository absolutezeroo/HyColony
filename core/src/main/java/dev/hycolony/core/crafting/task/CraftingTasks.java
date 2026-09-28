package dev.hycolony.core.crafting.task;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.model.RequestState;
import dev.hycolony.core.request.model.RequestToken;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A crafter's state (MC AbstractJobCrafter): its task queue, the tasks scheduled for it whose ingredients are still on
 * their way, the counters of the recipe under way and the secondary outputs waiting for a warehouse. Saved with the
 * job; MC keeps the two task lists in a request-system data store. A caller changing the task lists through
 * {@link #onTaskBeingScheduled}, {@link #onTaskBeingResolved} or {@link #onTaskDeletion} marks the colony dirty.
 *
 * <p>Deviation from MC: a token whose request is gone never blocks. {@link #currentTask} stops at an emptied queue,
 * where MC loops forever; {@link #finishRequest} pops such a head and {@link #cancelAll} skips such a task, where MC
 * throws on an unknown token.
 */
public final class CraftingTasks {
    private static final String QUEUE = "queue";
    private static final String ASSIGNED = "assignedTasks";
    /** MC NbtTagConstants.TAG_PROGRESS. */
    private static final String PROGRESS = "progress";
    /** MC NbtTagConstants.TAG_MAX_COUNTER. */
    private static final String MAX_COUNTER = "maxCounter";
    /** MC NbtTagConstants.TAG_CRAFT_COUNTER. */
    private static final String CRAFT_COUNTER = "craftCounter";
    /** MC NbtTagConstants.TAG_SECONDARY_OUTPUTS. */
    private static final String SECONDARY_OUTPUTS = "secondaryOutputs";

    private final List<RequestToken> queue = new ArrayList<>();
    private final List<RequestToken> assigned = new ArrayList<>();
    private final Map<ItemKey, Integer> secondaryOutputs = new LinkedHashMap<>();
    private int maxCraftingCount;
    private int craftCounter;
    private int progress;

    /** MC getTaskQueue: the tasks ready to craft, head first; read-only. */
    public List<RequestToken> taskQueue() {
        return Collections.unmodifiableList(queue);
    }

    /** MC getAssignedTasks: the tasks scheduled for this crafter, still waiting for their ingredients; read-only. */
    public List<RequestToken> assignedTasks() {
        return Collections.unmodifiableList(assigned);
    }

    /** How busy the crafter is (MC queue size plus assigned tasks size), to give a task to the least loaded one. */
    public int load() {
        return queue.size() + assigned.size();
    }

    /**
     * MC getCurrentTask: the request at the head of the queue, after popping the heads whose request is gone (marking
     * the colony dirty); empty if the queue ends empty.
     */
    public Optional<Request> currentTask(Colony colony) {
        while (!queue.isEmpty()) {
            Optional<Request> head = colony.requests().get(queue.getFirst());
            if (head.isPresent()) {
                return head;
            }
            queue.removeFirst();
            colony.markDirty();
        }
        return Optional.empty();
    }

    /**
     * MC finishRequest: the head task becomes RESOLVED, or FAILED; its resolver then drops it from these lists
     * ({@link #onTaskDeletion}). A head whose request is gone is popped instead; nothing happens on an empty queue.
     */
    public void finishRequest(Colony colony, boolean successful) {
        if (queue.isEmpty()) {
            return;
        }
        RequestToken current = queue.getFirst();
        if (colony.requests().get(current).isEmpty()) {
            queue.removeFirst();
            colony.markDirty();
            return;
        }
        colony.requests().updateState(current, successful ? RequestState.RESOLVED : RequestState.FAILED);
    }

    /** MC onTaskBeingScheduled: the task was given to this crafter; it waits for its ingredients. */
    public void onTaskBeingScheduled(RequestToken token) {
        assigned.add(token);
    }

    /** MC onTaskBeingResolved: the task's ingredients are there, it joins the end of the queue. */
    public void onTaskBeingResolved(RequestToken token) {
        onTaskDeletion(token);
        queue.add(token);
    }

    /** MC onTaskDeletion: forgets the task, queued or scheduled; returns whether this crafter held it. */
    public boolean onTaskDeletion(RequestToken token) {
        return queue.remove(token) || assigned.remove(token);
    }

    /**
     * MC onRemoval's cancelAssignedRequests, for a crafter losing its job: each queued then scheduled task still known
     * becomes FAILED, which hands its parent back to the request system; both lists end empty and the colony dirty.
     */
    public void cancelAll(Colony colony) {
        List<RequestToken> all = new ArrayList<>(queue);
        all.addAll(assigned);
        for (RequestToken token : all) {
            // A failed task cancels its siblings: a later one may already be gone.
            if (colony.requests().get(token).isPresent()) {
                colony.requests().updateState(token, RequestState.FAILED);
            }
        }
        queue.clear();
        assigned.clear();
        colony.markDirty();
    }

    /** MC getMaxCraftingCount: the runs of the current recipe this crafter may make now. */
    public int maxCraftingCount() {
        return maxCraftingCount;
    }

    public void setMaxCraftingCount(int maxCraftingCount) {
        this.maxCraftingCount = maxCraftingCount;
    }

    /** MC getCraftCounter: the runs of the current recipe already made. */
    public int craftCounter() {
        return craftCounter;
    }

    public void setCraftCounter(int craftCounter) {
        this.craftCounter = craftCounter;
    }

    /** MC getProgress: the hits given towards the current run. */
    public int progress() {
        return progress;
    }

    public void setProgress(int progress) {
        this.progress = progress;
    }

    /** MC getSecondaryOutputs: what the runs gave besides their output, by item, for a warehouse; mutable. */
    public Map<ItemKey, Integer> secondaryOutputs() {
        return secondaryOutputs;
    }

    /** MC serializeNBT, with the two task lists MC saves in its data store. */
    public JsonObject write() {
        JsonObject out = new JsonObject();
        out.add(QUEUE, tokens(queue));
        out.add(ASSIGNED, tokens(assigned));
        out.addProperty(PROGRESS, progress);
        out.addProperty(MAX_COUNTER, maxCraftingCount);
        out.addProperty(CRAFT_COUNTER, craftCounter);
        JsonArray items = new JsonArray();
        secondaryOutputs.forEach((item, count) -> {
            JsonObject stack = new JsonObject();
            stack.addProperty("item", item.id());
            stack.addProperty("count", count);
            items.add(stack);
        });
        out.add(SECONDARY_OUTPUTS, items);
        return out;
    }

    /**
     * MC deserializeNBT, tolerant: a missing or bad value reads as 0 or empty, a bad token or stack is skipped.
     * Deviation from MC: each counter is read into its own field, where MC reads all three into {@code progress}.
     */
    public void read(JsonObject in) {
        readTokens(in.get(QUEUE), queue);
        readTokens(in.get(ASSIGNED), assigned);
        progress = intOr(in.get(PROGRESS));
        maxCraftingCount = intOr(in.get(MAX_COUNTER));
        craftCounter = intOr(in.get(CRAFT_COUNTER));
        secondaryOutputs.clear();
        if (in.get(SECONDARY_OUTPUTS) instanceof JsonArray items) {
            for (JsonElement e : items) {
                if (e instanceof JsonObject stack
                        && stack.get("item") instanceof JsonPrimitive item
                        && item.isString()
                        && intOr(stack.get("count")) > 0) {
                    secondaryOutputs.merge(new ItemKey(item.getAsString()), intOr(stack.get("count")), Integer::sum);
                }
            }
        }
    }

    private static JsonArray tokens(Collection<RequestToken> tokens) {
        JsonArray out = new JsonArray();
        tokens.forEach(t -> out.add(t.id().toString()));
        return out;
    }

    /** A token that is not a UUID is dropped: the request it named cannot be found anyway. */
    private static void readTokens(@Nullable JsonElement saved, List<RequestToken> into) {
        into.clear();
        if (!(saved instanceof JsonArray array)) {
            return;
        }
        for (JsonElement e : array) {
            if (e instanceof JsonPrimitive p && p.isString()) {
                try {
                    into.add(new RequestToken(UUID.fromString(p.getAsString())));
                } catch (IllegalArgumentException _) {
                    // tolerant read (CLAUDE.md § 5)
                }
            }
        }
    }

    private static int intOr(@Nullable JsonElement saved) {
        return saved instanceof JsonPrimitive p && p.isNumber() ? p.getAsInt() : 0;
    }
}
