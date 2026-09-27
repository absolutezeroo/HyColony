package dev.hycolony.core.logistics.warehouse;

import dev.hycolony.core.request.model.RequestToken;

/**
 * A courier job's own task queue, as the warehouse's courier resolvers see it (MC {@code JobDeliveryman.getTaskQueue},
 * {@code onTaskDeletion}). The courier job implements it.
 */
public interface CourierTaskQueue {
    /** MC {@code onTaskDeletion}: drops {@code token} from this courier's queue; whether the queue held it. */
    boolean removeTask(RequestToken token);
}
