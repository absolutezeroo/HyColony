package dev.hycolony.core.logistics.courier;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.logistics.warehouse.WarehouseBuilding;
import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.model.Delivery;
import dev.hycolony.core.request.model.RequestState;
import dev.hycolony.core.request.model.RequestToken;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** The courier queue's rules outside task selection (MC JobDeliveryman.finishRequest, getTaskListWithSameDestination). */
final class CourierTasks {
    private CourierTasks() {}

    /**
     * MC {@code finishRequest}, on the head of {@code queue}: a delivery resolves (or fails) every delivery of
     * {@code ongoing}, a pickup only itself; a head whose request is gone is popped. Deviation from MC: a delivery
     * head with nothing loaded settles the head alone (MC settles nothing and keeps the head, forever).
     */
    static void finish(Colony colony, List<RequestToken> queue, Set<RequestToken> ongoing, boolean successful) {
        if (queue.isEmpty()) {
            return;
        }
        RequestToken current = queue.getFirst();
        Optional<Request> request = colony.requests().get(current);
        RequestState state = successful ? RequestState.RESOLVED : RequestState.FAILED;
        if (request.isEmpty()) {
            queue.removeFirst();
        } else if (request.get().requestable() instanceof Delivery) {
            List<RequestToken> done = ongoing.isEmpty() ? List.of(current) : List.copyOf(ongoing);
            for (RequestToken token : done) {
                queue.remove(token);
                ongoing.remove(token);
                if (colony.requests()
                        .get(token)
                        .filter(r -> r.state() == RequestState.IN_PROGRESS)
                        .isPresent()) {
                    colony.requests().updateState(token, state);
                }
            }
        } else {
            queue.remove(current);
            colony.requests().updateState(current, state);
        }
        colony.markDirty();
    }

    /**
     * MC {@code getTaskListWithSameDestination}: {@code delivery}, then each delivery of {@code queue} with its target
     * and its start, or a start in the same warehouse.
     */
    static List<Request> withSameDestination(Colony colony, List<RequestToken> queue, Request delivery) {
        List<Request> out = new ArrayList<>();
        out.add(delivery);
        Delivery first = (Delivery) delivery.requestable();
        for (RequestToken token : queue) {
            if (!token.equals(delivery.token())) {
                colony.requests()
                        .get(token)
                        .filter(r -> r.requestable() instanceof Delivery d && sameSourceAndTarget(colony, d, first))
                        .ifPresent(out::add);
            }
        }
        return out;
    }

    /** MC {@code haveTasksSameSourceAndDest}. */
    private static boolean sameSourceAndTarget(Colony colony, Delivery a, Delivery b) {
        if (!a.target().equals(b.target())) {
            return false;
        }
        if (a.start().equals(b.start())) {
            return true;
        }
        for (Building building : colony.buildings().all()) {
            if (building.type().equals(WarehouseBuilding.TYPE)
                    && building.containers().contains(a.start())
                    && building.containers().contains(b.start())) {
                return true;
            }
        }
        return false;
    }
}
