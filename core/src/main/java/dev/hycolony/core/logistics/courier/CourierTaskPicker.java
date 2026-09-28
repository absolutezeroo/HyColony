package dev.hycolony.core.logistics.courier;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.logistics.warehouse.CourierAssignmentModule;
import dev.hycolony.core.logistics.warehouse.RequesterLocation;
import dev.hycolony.core.logistics.warehouse.WarehouseRequestQueue;
import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.model.Deliverable;
import dev.hycolony.core.request.model.Delivery;
import dev.hycolony.core.request.model.Pickup;
import dev.hycolony.core.request.model.RequestToken;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * MC {@code JobDeliveryman.getCurrentTask}: a courier's next task. Its own queue comes first; otherwise it pulls the
 * best-scored task of its warehouse queue, with the tasks going to the same place up to its parallel count, and the
 * tasks it skipped over age.
 */
final class CourierTaskPicker {
    /** MC {@code getRequestPriority}: what a pickup not due yet loses. */
    static final int NOT_DUE_MALUS = 100;

    private final Colony colony;
    private final DeliverymanJob job;
    private final BlockPos warehouse;
    private final List<RequestToken> shared;
    private final List<RequestToken> toRemove = new ArrayList<>();

    private CourierTaskPicker(Colony colony, DeliverymanJob job, BlockPos warehouse, List<RequestToken> shared) {
        this.colony = colony;
        this.job = job;
        this.warehouse = warehouse;
        this.shared = shared;
    }

    /** The courier's current task, pulled from its warehouse when its own queue is empty; empty if none. */
    static Optional<Request> currentTask(Colony colony, DeliverymanJob job) {
        Optional<Request> own = ownHead(colony, job.mutableQueue());
        if (own.isPresent()) {
            return own;
        }
        Optional<Building> warehouse =
                CourierAssignmentModule.warehouseOf(colony, job.citizen().id());
        return warehouse.flatMap(w -> w.module(WarehouseRequestQueue.class)
                .flatMap(q -> new CourierTaskPicker(colony, job, w.position(), q.tokens()).pull()));
    }

    /**
     * The head of the courier's own queue. Deviation from MC: a head whose request is gone (a stale token after a
     * load) is dropped; MC returns null for it, forever, as nothing else pops it.
     */
    static Optional<Request> ownHead(Colony colony, List<RequestToken> queue) {
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
     * Takes the best-scored task out of the warehouse queue (the first on a tie), with its companions. Tokens whose
     * request is gone are dropped; MC drops them only when it finds a task, but they can never be served.
     */
    private Optional<Request> pull() {
        Request best = null;
        int bestIndex = -1;
        int bestScore = Integer.MIN_VALUE;
        for (int i = 0; i < shared.size(); i++) {
            Optional<Request> r = colony.requests().get(shared.get(i));
            if (r.isEmpty()) {
                toRemove.add(shared.get(i));
                continue;
            }
            int score = score(r.get(), i);
            if (score > bestScore) {
                best = r.get();
                bestIndex = i;
                bestScore = score;
            }
        }
        if (best != null) {
            take(best, bestIndex);
        }
        if (!toRemove.isEmpty()) {
            shared.removeAll(toRemove);
            colony.markDirty();
        }
        return Optional.ofNullable(best);
    }

    /** A delivery heads the courier queue before its companions; a pickup goes after them. */
    private void take(Request best, int bestIndex) {
        toRemove.add(best.token());
        if (best.requestable() instanceof Delivery) {
            job.mutableQueue().add(best.token());
        }
        groupAndAge(best, bestIndex);
        if (best.requestable() instanceof Pickup) {
            job.mutableQueue().add(best.token());
        }
    }

    /**
     * MC: each entry before the chosen one ages; any other entry going to the same place joins the courier queue,
     * until the courier carries its parallel count.
     */
    private void groupAndAge(Request best, int bestIndex) {
        Optional<BlockPos> target = target(best);
        int max = job.maxParallelDeliveries(colony);
        int extended = 1;
        for (int index = 0; index < shared.size(); index++) {
            RequestToken token = shared.get(index);
            Optional<Request> r = colony.requests().get(token);
            if (r.isEmpty() || index == bestIndex) {
                continue;
            }
            if (index < bestIndex) {
                r.get().incrementPriorityDueToAging();
            }
            if (target.isPresent() && target.equals(target(r.get()))) {
                job.mutableQueue().add(token);
                extended++;
                toRemove.add(token);
            }
            if (extended >= max) {
                break;
            }
        }
    }

    /**
     * MC {@code getRequestPriority}: the task's priority, minus {@link #NOT_DUE_MALUS} for a pickup not due yet, plus
     * its age in the queue, minus the square root of the Manhattan distance from source to target. MC's -1000 for an
     * unloaded target is not ported: MC overwrites it with the priority for every courier task, so it never applies.
     */
    private int score(Request r, int index) {
        int priority = switch (r.requestable()) {
            case Delivery d -> d.priority();
            case Pickup p -> p.day() > colony.day() ? p.priority() - NOT_DUE_MALUS : p.priority();
            case Deliverable _ -> 1;
        };
        priority += shared.size() - index;
        return priority - distanceMalus(r);
    }

    /** Deviation from MC: 0 for an unknown source or target (MC would throw), so the task stays pickable and fails. */
    private int distanceMalus(Request r) {
        Optional<BlockPos> from = source(r);
        Optional<BlockPos> to = target(r);
        if (from.isEmpty() || to.isEmpty()) {
            return 0;
        }
        BlockPos a = from.get();
        BlockPos b = to.get();
        long manhattan =
                Math.abs((long) a.x() - b.x()) + Math.abs((long) a.y() - b.y()) + Math.abs((long) a.z() - b.z());
        return (int) Math.sqrt((double) manhattan);
    }

    /** MC {@code getSource}: a delivery's rack; the warehouse for a pickup, which runs the other way. */
    private Optional<BlockPos> source(Request r) {
        return switch (r.requestable()) {
            case Delivery d -> Optional.of(d.start());
            case Pickup _ -> Optional.of(warehouse);
            case Deliverable _ -> Optional.empty();
        };
    }

    /** MC {@code getTarget}: where a delivery goes, or the building a pickup empties. */
    private Optional<BlockPos> target(Request r) {
        return switch (r.requestable()) {
            case Delivery d -> RequesterLocation.of(colony, d.target());
            case Pickup _ -> RequesterLocation.of(colony, r.requester());
            case Deliverable _ -> Optional.empty();
        };
    }
}
