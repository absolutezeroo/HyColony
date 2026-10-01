package dev.hycolony.core.citizen.food.hall;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.module.BuildingModule;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemKey;
import java.util.Optional;
import java.util.Set;

/**
 * What a hungry citizen needs from a dining hall (MC BuildingCook and its RestaurantMenuModule), declared here as a
 * port of the core: the hall is a crafting hut, which the citizen package may not see. Its menu module implements it.
 */
public interface DiningHall extends BuildingModule {
    /** MC RestaurantMenuModule.getMenu: the foods served there. */
    Set<ItemKey> menu();

    /** MC STAFFED_RESTAURANTS: whether the hall has a waiter. */
    boolean hasWaiter(Colony colony, Building hall);

    /** MC BuildingCook.getNextSittingPosition: a free seat after at most 3 random draws; empty without one. */
    Optional<BlockPos> nextSeat(Colony colony, Building hall);

    /** MC BuildingCook.storeCustomer: the hall serves {@code citizenId} from now on, and no other hall does. */
    void storeCustomer(Colony colony, Building hall, int citizenId);
}
