package dev.hycolony.core.citizen.wander;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.kernel.BlockPos;
import java.util.Optional;
import java.util.random.RandomGenerator;

/** Where a citizen goes for leisure (MC EntityAICitizenWander.decide and RegisteredStructureManager). */
final class LeisureSites {
    /** MC getRandomLeisureSite: the town hall is a leisure site from that level. */
    private static final int TOWN_HALL_LEISURE_LEVEL = 3;
    /** MC getRandomLeisureSite: RANDOM.nextInt(4), the town hall's turn under 1. */
    private static final int SITE_DRAW_BOUND = 4;

    private LeisureSites() {}

    /**
     * MC decide: the colony's random leisure site, else the citizen's home, else the colony's centre.
     *
     * <p>Deviation from MC: getRandomLeisureSite also picks a mystical site, library, university, tavern or leisure
     * decoration, none of which HyColony has yet; and it reads the world's rain, here the rain at the colony's centre
     * (Hytale weather is per zone).
     */
    static BlockPos pick(Colony colony, CitizenData citizen, RandomGenerator random) {
        return randomLeisureSite(colony, random)
                .or(() -> Optional.ofNullable(citizen.homeBuilding()))
                .orElse(colony.center());
    }

    /** MC getRandomLeisureSite: a level 3 town hall one time in 4, the town hall in the rain; else none. */
    private static Optional<BlockPos> randomLeisureSite(Colony colony, RandomGenerator random) {
        Optional<Building> townHall = colony.buildings().townHall();
        if (random.nextInt(SITE_DRAW_BOUND) < 1
                && townHall.filter(b -> b.level() >= TOWN_HALL_LEISURE_LEVEL).isPresent()) {
            return townHall.map(Building::position);
        }
        if (colony.context().worldQuery().isRainingAt(colony.center())) {
            return townHall.map(Building::position);
        }
        return Optional.empty();
    }
}
