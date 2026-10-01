package dev.hycolony.core.citizen.home;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.kernel.BlockPos;
import java.util.Optional;

/**
 * Where a citizen goes to bed: MC CitizenData.getHomePosition. Deviation from MC: no tavern fallback (no tavern) and
 * no colony-centre fallback (the town hall stands for it in HyColony).
 */
public final class HomePosition {
    private HomePosition() {}

    /** Its home, else the town hall, else empty. */
    public static Optional<BlockPos> of(Colony colony, CitizenData citizen) {
        BlockPos home = citizen.homeBuilding();
        return home != null ? Optional.of(home) : colony.buildings().townHall().map(Building::position);
    }
}
