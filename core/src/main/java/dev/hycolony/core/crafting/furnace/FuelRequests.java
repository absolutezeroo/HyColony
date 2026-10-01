package dev.hycolony.core.crafting.furnace;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.model.RequestState;
import dev.hycolony.core.request.model.StackList;
import java.util.List;

/** The fuel a furnace user's hut asks for (MC AbstractEntityAIUsesFurnace.startWorking's burnable request). */
public final class FuelRequests {
    /** MC REQUESTS_TYPE_BURNABLE: the fuel request's description (the Fuel resource type's id). */
    public static final String FUEL = "Fuel";
    /** MC STACKSIZE: fuel asked for each station. */
    static final int STACKSIZE = 64;

    private FuelRequests() {}

    /**
     * MC hasWorkerOpenRequestsFiltered for REQUESTS_TYPE_BURNABLE: a fuel request of {@code hut} still open (MC drops
     * a request from the open ones once it completes).
     */
    public static boolean open(Colony colony, Building hut) {
        for (Request r : colony.requests().byRequester(hut.requesterId())) {
            if (r.requestable() instanceof StackList l
                    && l.description().equals(FUEL)
                    && r.state().isBefore(RequestState.COMPLETED)) {
                return true;
            }
        }
        return false;
    }

    /**
     * MC: {@code StackList(allowed fuels, "Fuel", 64 x stations, 1)}, a request no one waits for. Deviation from MC:
     * with no fuel allowed, MC still asks an empty list (and complains, an interaction); a StackList needs one item,
     * so nothing is asked.
     */
    public static void ask(Colony colony, Building hut, List<ItemKey> allowed, int stations) {
        if (!allowed.isEmpty()) {
            colony.requests().createAsync(hut, new StackList(allowed, FUEL, STACKSIZE * stations, 1));
        }
    }
}
