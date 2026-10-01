package dev.hycolony.core.citizen.food.hall;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.kernel.BlockPos;
import java.util.Optional;

/** Finds the colony's dining halls (MC getBestBuilding(pos, BuildingCook.class)). */
public final class DiningHalls {
    private DiningHalls() {}

    /**
     * MC getBestBuilding: the built (level > 0), loaded dining hall closest to {@code from}, among those with a waiter
     * if {@code staffed}; empty without one.
     */
    public static Optional<Building> closest(Colony colony, BlockPos from, boolean staffed) {
        Building best = null;
        long bestDistance = Long.MAX_VALUE;
        for (Building b : colony.buildings().all()) {
            DiningHall hall = b.module(DiningHall.class).orElse(null);
            if (hall == null
                    || b.level() <= 0
                    || !colony.context().worldQuery().isLoaded(b.position())
                    || (staffed && !hall.hasWaiter(colony, b))) {
                continue;
            }
            long distance = b.position().distSq(from);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = b;
            }
        }
        return Optional.ofNullable(best);
    }

    /** The dining hall module of {@code b}; empty when it is no dining hall. */
    public static Optional<DiningHall> of(Building b) {
        return b.module(DiningHall.class);
    }
}
