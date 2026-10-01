package dev.hycolony.core.citizen.food;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.BuildingType;
import dev.hycolony.core.building.module.ModuleProducer;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.permission.Permissions;
import dev.hycolony.core.colony.territory.TerritoryIndex;
import dev.hycolony.core.construction.hut.ConstructionBuildingTypes;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.testing.TestContexts;
import dev.hycolony.core.testing.food.FakeDiningHall;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** MC FoodUtils.getBestFoodForCitizen. */
class FoodChoiceTest {
    private final TestContexts t = new TestContexts();
    private final ItemKey apple = t.catalog.food("apple", 4, 0);
    private final CitizenData citizen = new CitizenData(1);
    private final Colony colony = new Colony(
            t.context(),
            new TerritoryIndex(),
            new Colony.Founding(1, "T", new BlockPos(0, 64, 0), Permissions.createDefault(UUID.randomUUID(), "A")));

    FoodChoiceTest() {
        colony.citizens().restore(citizen);
        Building home = Building.create(ConstructionBuildingTypes.RESIDENCE, new BlockPos(10, 64, 0), 0);
        home.setLevel(2);
        colony.buildings().add(home);
        citizen.setHomeBuilding(home.position());
        for (int i = 0; i < FoodHistory.SIZE; i++) {
            citizen.hunger().history().add(apple); // a full history of one food: diversity 1, below level 2
        }
        citizen.inventory().insert(new ItemAmount(apple, 5), _ -> 64);
    }

    @Test
    void aCitizenBoredOfWhatItCarriesWouldRatherGoToADiningHall() {
        assertEquals(0, new FoodChoice(colony, citizen).bestSlot(citizen.inventory(), null));

        Building hall = Building.create(
                new BuildingType("test:hall", "test:hall", 5, List.of(new ModuleProducer("hall", FakeDiningHall::new))),
                new BlockPos(20, 64, 0),
                0);
        hall.setLevel(1);
        colony.buildings().add(hall);

        assertEquals(-1, new FoodChoice(colony, citizen).bestSlot(citizen.inventory(), null));
        assertEquals(0, new FoodChoice(colony, citizen).bestSlot(citizen.inventory(), Set.of(apple)));
    }
}
