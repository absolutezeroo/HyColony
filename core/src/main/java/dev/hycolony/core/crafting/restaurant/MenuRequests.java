package dev.hycolony.core.crafting.restaurant;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.kernel.catalog.ItemCatalog;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.RequestManager;
import dev.hycolony.core.request.model.RequestState;
import dev.hycolony.core.request.model.StackRequest;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * The dining hall's stock requests for its menu (MC RestaurantMenuModule.onColonyTick, canCook true): for each dish, a
 * {@link StackRequest} up to its stack size times the hall's level, then one for the raw item that cooks into it; both
 * cancelled once the hall holds enough. Deviation from MC: MC asks a MinimumStack, which HyColony lacks; a
 * StackRequest of minimum 1 that the hall may not resolve itself acts alike.
 */
final class MenuRequests {
    /** MC Constants.STACKSIZE: the most one request asks. */
    static final int STACKSIZE = 64;

    private MenuRequests() {}

    /** One pass over {@code menu} for {@code hall}; nothing while its block is not loaded. */
    static void update(Colony colony, Building hall, Collection<ItemKey> menu) {
        if (!colony.context().worldQuery().isLoaded(hall.position())) {
            return;
        }
        List<Request> open = open(colony, hall);
        for (ItemKey dish : menu) {
            update(colony, hall, dish, open);
        }
    }

    private static void update(Colony colony, Building hall, ItemKey dish, List<Request> open) {
        ItemCatalog catalog = colony.context().ports().catalog();
        Optional<ItemKey> raw = colony.context().ports().cooking().catalog().rawFor(dish);
        int target = catalog.maxStack(dish) * expectedStock(hall);
        int count = Math.min(target, held(colony, hall, dish))
                + raw.map(r -> Math.min(target, held(colony, hall, r))).orElse(0);
        int delta = target - count;
        Optional<Request> request = matching(open, dish);
        Optional<Request> rawRequest = raw.flatMap(r -> matching(open, r));
        RequestManager requests = colony.requests();
        if (delta > 0) {
            int qty = Math.min(STACKSIZE, Math.min(catalog.maxStack(dish), delta));
            if (request.isEmpty()) {
                requests.createAsync(hall, new StackRequest(dish, qty, 1, false));
            } else if (raw.isPresent()
                    && rawRequest.isEmpty()
                    && request.get().state().compareTo(RequestState.IN_PROGRESS) <= 0) {
                requests.createAsync(hall, new StackRequest(raw.get(), qty, 1, false));
            }
        } else {
            request.ifPresent(r -> requests.updateState(r.token(), RequestState.CANCELLED));
            rawRequest.ifPresent(r -> requests.updateState(r.token(), RequestState.CANCELLED));
        }
    }

    /**
     * MC removeMenuItem: cancels the hall's open request of {@code dish}, if any. Deviation from MC: MC looks it up
     * under the Stack type while its requests are MinimumStacks, so it never finds one (a MC bug); here it is
     * cancelled, so a dish off the menu is not delivered any more.
     */
    static void cancel(Colony colony, Building hall, ItemKey dish) {
        matching(open(colony, hall), dish)
                .ifPresent(r -> colony.requests().updateState(r.token(), RequestState.CANCELLED));
    }

    /** MC RestaurantMenuModule.expectedStock for a cooking hall: its level. */
    static int expectedStock(Building hall) {
        return hall.level();
    }

    /** How many of {@code item} the hall's containers hold (MC hasBuildingEnoughElseCount). */
    private static int held(Colony colony, Building hall, ItemKey item) {
        return colony.context().ports().containers().count(hall.containers(), item);
    }

    /**
     * The hall's requests still open (MC getOpenRequestsByRequestableType): not yet completed. MC drops a request from
     * the open ones once it completes; a completed one waits here for the waiter to receive it, and must not stop the
     * hall from asking again.
     */
    private static List<Request> open(Colony colony, Building hall) {
        return colony.requests().byRequester(hall.requesterId()).stream()
                .filter(r -> r.state().isBefore(RequestState.COMPLETED))
                .toList();
    }

    /** MC getMatchingRequest: the open stack request of {@code item}. */
    private static Optional<Request> matching(List<Request> open, ItemKey item) {
        return open.stream()
                .filter(r ->
                        r.requestable() instanceof StackRequest s && s.item().equals(item))
                .findFirst();
    }
}
