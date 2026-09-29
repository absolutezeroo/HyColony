package dev.hycolony.core.job;

import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.model.RequestToken;
import java.util.List;
import java.util.Optional;

/** A job's queue of tasks, as request tokens, head first (MC AbstractJobCrafter and JobDeliveryman getTaskQueue). */
public final class TaskQueues {
    private TaskQueues() {}

    /**
     * MC getCurrentTask: the request at the head of {@code queue}, after popping the heads whose request is gone (the
     * colony is then marked dirty, the queue being saved with the job); empty once the queue ends empty.
     */
    public static Optional<Request> head(Colony colony, List<RequestToken> queue) {
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
}
