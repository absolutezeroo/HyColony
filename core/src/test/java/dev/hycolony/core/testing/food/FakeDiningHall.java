package dev.hycolony.core.testing.food;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.food.hall.DiningHall;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemKey;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** A dining hall's module as a test sets it up: its menu, waiter and seats; it notes its customers. */
public final class FakeDiningHall implements DiningHall {
    public final Set<ItemKey> menu = new HashSet<>();
    public final Set<Integer> customers = new HashSet<>();
    public final List<BlockPos> seats = new ArrayList<>();
    public boolean waiter;

    @Override
    public Set<ItemKey> menu() {
        return menu;
    }

    @Override
    public boolean hasWaiter(Colony colony, Building hall) {
        return waiter;
    }

    /** The first of {@link #seats} nobody sits on. */
    @Override
    public Optional<BlockPos> nextSeat(Colony colony, Building hall) {
        return seats.stream()
                .filter(s -> !colony.context().seats().isSeatTaken(s))
                .findFirst();
    }

    @Override
    public void storeCustomer(Colony colony, Building hall, int citizenId) {
        customers.add(citizenId);
    }
}
