package dev.hycolony.core.logistics.courier;

import com.google.gson.JsonObject;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.job.Job;
import dev.hycolony.core.job.JobAI;
import dev.hycolony.core.job.JobType;
import dev.hycolony.core.job.TaskQueues;
import dev.hycolony.core.job.WorkerModule;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.logistics.warehouse.CourierAssignmentModule;
import dev.hycolony.core.logistics.warehouse.CourierTaskQueue;
import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.model.Delivery;
import dev.hycolony.core.request.model.Pickup;
import dev.hycolony.core.request.model.RequestState;
import dev.hycolony.core.request.model.RequestToken;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * MC {@code JobDeliveryman}: the courier's own task queue and the deliveries it is carrying, both saved (MC keeps them
 * in a request-system data store). Its next task comes from {@link #currentTask}.
 */
public final class DeliverymanJob extends Job implements CourierTaskQueue {
    public static final JobType TYPE = new JobType(CourierAssignmentModule.COURIER_JOB_ID, DeliverymanJob::new);

    /** MC {@code getInactivityLimit}: 600 citizen-data updates of 60 ticks, so 36,000 ticks without working. */
    public static final int INACTIVITY_LIMIT = 600;
    /** MC JobDeliveryman.getSaturationFactor: a courier gets hungry 20 % faster. */
    static final double SATURATION_FACTOR = 1.2;

    private final List<RequestToken> queue = new ArrayList<>();
    private final Set<RequestToken> ongoing = new LinkedHashSet<>();

    public DeliverymanJob(CitizenData citizen) {
        super(TYPE, citizen);
    }

    /** MC {@code EntityAIWorkDeliveryman}. */
    @Override
    public JobAI createAI(Colony colony, BodyId body) {
        return new DeliverymanAI(colony, this, body);
    }

    /** MC {@code getTaskQueue}: the courier's own tasks, head first, read-only. */
    public List<RequestToken> taskQueue() {
        return Collections.unmodifiableList(queue);
    }

    /** The queue itself, for the task picker. */
    List<RequestToken> mutableQueue() {
        return queue;
    }

    /** MC {@code getCurrentTask}: see {@link CourierTaskPicker}; empty without task or warehouse. */
    public Optional<Request> currentTask(Colony colony) {
        return CourierTaskPicker.currentTask(colony, this);
    }

    /**
     * MC {@code getMaxParallelDeliveries}: {@code 1 + level / 5} of the secondary skill (Adaptability) of the hut's
     * first worker; 1 without hut or worker.
     */
    public int maxParallelDeliveries(Colony colony) {
        Optional<WorkerModule> work = Optional.ofNullable(citizen().workBuilding())
                .flatMap(colony.buildings()::at)
                .flatMap(hut -> hut.module(WorkerModule.class));
        if (work.isEmpty() || work.get().workers().isEmpty()) {
            return 1;
        }
        return colony.citizens()
                .get(work.get().workers().getFirst())
                .map(worker -> 1 + worker.skills().level(work.get().secondary()) / 5)
                .orElse(1);
    }

    /** MC {@code addConcurrentDelivery}: {@code token} was loaded for the delivery under way. */
    public void addConcurrentDelivery(RequestToken token) {
        ongoing.add(token);
    }

    /** MC {@code removeConcurrentDelivery}. */
    public void removeConcurrentDelivery(RequestToken token) {
        ongoing.remove(token);
    }

    /** The deliveries loaded for the delivery under way, read-only. */
    public Set<RequestToken> ongoingDeliveries() {
        return Collections.unmodifiableSet(ongoing);
    }

    /**
     * The head of the courier's own queue, never pulled from the warehouse. Deviation from MC: the working states
     * call {@code getCurrentTask}, which pulls when the queue is empty; here only START_WORKING (every 100 ticks)
     * pulls, since a pull scores the whole warehouse queue. A working state finding its queue emptied (a cancel)
     * restarts, as MC does when the pulled task is not the one it was doing.
     */
    Optional<Request> ownTask(Colony colony) {
        return TaskQueues.head(colony, queue);
    }

    /** MC {@code getTaskListWithSameDestination}: see {@link CourierTasks#withSameDestination}. */
    List<Request> tasksWithSameDestination(Colony colony, Request delivery) {
        return CourierTasks.withSameDestination(colony, queue, delivery);
    }

    /**
     * MC {@code finishRequest}, on the head of the courier's queue: a delivery resolves (or fails) every delivery
     * loaded with it, a pickup only itself; a head whose request is gone is popped. See {@link CourierTasks#finish}.
     */
    public void finishRequest(Colony colony, boolean successful) {
        CourierTasks.finish(colony, queue, ongoing, successful);
    }

    @Override
    public boolean removeTask(RequestToken token) {
        return queue.remove(token);
    }

    @Override
    protected int inactivityLimit() {
        return INACTIVITY_LIMIT;
    }

    /**
     * MC {@code triggerActivityChangeAction}: back at work, the deliveries and pickups nobody carried are assigned
     * again; idle too long, every queued task fails.
     */
    @Override
    protected void onActivityChange(Colony colony, boolean active) {
        if (active) {
            colony.requests()
                    .onColonyUpdate(r -> r.requestable() instanceof Delivery || r.requestable() instanceof Pickup);
        } else {
            cancelAssignedRequests(colony);
        }
    }

    /** MC JobDeliveryman.getSaturationFactor. */
    @Override
    public double saturationFactor() {
        return SATURATION_FACTOR;
    }

    /** MC {@code onRemoval}: the courier's tasks fail, so their requests go back to the request system. */
    @Override
    public void onRemoval(Colony colony) {
        cancelAssignedRequests(colony);
    }

    /** MC {@code cancelAssignedRequests}: each queued task still known goes FAILED; the queue ends empty. */
    private void cancelAssignedRequests(Colony colony) {
        for (RequestToken token : List.copyOf(queue)) {
            if (colony.requests().get(token).isPresent()) {
                colony.requests().updateState(token, RequestState.FAILED);
            }
            queue.remove(token);
        }
        colony.markDirty();
    }

    @Override
    public JsonObject write() {
        JsonObject o = super.write();
        o.add("queue", RequestToken.toJson(queue));
        o.add("ongoing", RequestToken.toJson(ongoing));
        return o;
    }

    @Override
    public void read(JsonObject o) {
        super.read(o);
        queue.clear();
        queue.addAll(RequestToken.fromJson(o.get("queue")));
        ongoing.clear();
        ongoing.addAll(RequestToken.fromJson(o.get("ongoing")));
    }
}
