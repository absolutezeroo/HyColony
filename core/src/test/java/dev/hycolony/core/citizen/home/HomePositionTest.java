package dev.hycolony.core.citizen.home;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.BuildingTypes;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.permission.Permissions;
import dev.hycolony.core.colony.territory.TerritoryIndex;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.testing.TestContexts;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** MC CitizenData.getHomePosition, without the tavern nor the colony centre. */
class HomePositionTest {
    private final TestContexts t = new TestContexts();
    private final Colony c = new Colony(
            t.context(),
            new TerritoryIndex(),
            new Colony.Founding(1, "T", new BlockPos(0, 64, 0), Permissions.createDefault(UUID.randomUUID(), "A")));
    private final CitizenData d = new CitizenData(1);

    @Test
    void homeElseTownHallElseNone() {
        assertEquals(Optional.empty(), HomePosition.of(c, d));

        Building hall = Building.create(BuildingTypes.TOWN_HALL, new BlockPos(5, 64, 5), 0);
        c.buildings().add(hall);
        assertEquals(Optional.of(hall.position()), HomePosition.of(c, d));

        d.setHomeBuilding(new BlockPos(20, 64, 0));
        assertEquals(Optional.of(new BlockPos(20, 64, 0)), HomePosition.of(c, d));
    }
}
