package dev.hycolony.core.crafting.restaurant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.module.BuildingEventsModule;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.food.FoodRules;
import dev.hycolony.core.citizen.food.hall.DiningHalls;
import dev.hycolony.core.farming.hut.FarmerHut;
import dev.hycolony.core.job.WorkerModule;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.BlockKey;
import dev.hycolony.core.kernel.item.BlockState;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** MC BuildingCook: its seats (here seat blocks), its customers, and no food kept. */
class DiningRoomModuleTest extends DiningHallFixture {
    private static final BlockKey CHAIR = new BlockKey("chair");
    private static final BlockKey CAMPFIRE = new BlockKey("campfire");
    private static final BlockPos SEAT = new BlockPos(1, 64, 1);

    DiningRoomModuleTest() {
        t.catalog.seats.add(CHAIR);
        t.cooking.stationBlocks.add(CAMPFIRE);
    }

    @Test
    void seatsAndCampfiresOfThePlanOrPlacedByAPlayerJoinTheHall() {
        BuildingEventsModule.blockPlaced(colony, hall, SEAT, CHAIR);
        BuildingEventsModule.blockPlacedByPlayer(colony, hall, new BlockPos(2, 64, 2), CHAIR);
        BuildingEventsModule.blockPlacedByPlayer(colony, hall, STATION, CAMPFIRE);
        BuildingEventsModule.blockPlacedByPlayer(colony, hall, new BlockPos(3, 64, 3), new BlockKey("stone"));

        assertEquals(List.of(SEAT, new BlockPos(2, 64, 2)), room().seats());
        assertEquals(List.of(STATION), furnaces().stations());
    }

    @Test
    void aFreeSeatIsDrawnAndATakenOneSkipped() {
        room().addSeat(SEAT);
        t.bodies.seats.add(SEAT);
        assertEquals(Optional.of(SEAT), menu().nextSeat(colony, hall));
        t.bodies.takenSeats.add(SEAT);
        assertEquals(Optional.empty(), menu().nextSeat(colony, hall)); // MC: 3 draws, all taken
    }

    @Test
    void theFirstCustomerFillsEachHallWithTheWorkersOfTheHutsClosestToIt() {
        Building farm = Building.create(FarmerHut.TYPE, new BlockPos(10, 64, 0), 0);
        farm.setLevel(1);
        farm.setBuilt(true);
        colony.buildings().add(farm);
        CitizenData farmer = new CitizenData(2);
        colony.citizens().restore(farmer);
        assertTrue(farm.module(WorkerModule.class).orElseThrow().hire(colony, farm, farmer));
        Building other = Building.create(DiningHallHut.TYPE, new BlockPos(100, 64, 0), 0);
        colony.buildings().add(other);

        menu().storeCustomer(colony, hall, 7);

        assertEquals(Set.of(2, 7), room().customers());
        DiningHalls.of(other).orElseThrow().storeCustomer(colony, other, 7);
        assertEquals(Set.of(2), room().customers()); // a customer of one hall only
    }

    @Test
    void aWorkerGoesToTheHallClosestToItsHut() {
        Building other = Building.create(DiningHallHut.TYPE, new BlockPos(100, 64, 0), 0);
        colony.buildings().add(other);
        Building farm = Building.create(FarmerHut.TYPE, new BlockPos(90, 64, 0), 0);
        farm.setLevel(1);
        farm.setBuilt(true);
        colony.buildings().add(farm);
        CitizenData farmer = new CitizenData(2);
        colony.citizens().restore(farmer);
        assertTrue(farm.module(WorkerModule.class).orElseThrow().hire(colony, farm, farmer));

        menu().storeCustomer(colony, hall, 7);

        assertEquals(Set.of(7), room().customers());
        assertEquals(
                Set.of(2), other.module(DiningRoomModule.class).orElseThrow().customers());
    }

    @Test
    void aSeatIsDrawnUpToThreeTimes() {
        BlockPos taken = new BlockPos(2, 64, 2);
        room().addSeat(SEAT);
        room().addSeat(taken);
        t.bodies.seats.addAll(List.of(SEAT, taken));
        t.bodies.takenSeats.add(taken);
        int found = 0;
        for (int i = 0; i < 100; i++) {
            found += menu().nextSeat(colony, hall).isPresent() ? 1 : 0;
        }
        assertTrue(found > 75, "found " + found); // 3 draws: 7 in 8; a single draw would find 1 in 2
    }

    @Test
    void aDrawnSeatWhoseBlockIsNoSeatAnyMoreIsForgotten() {
        BlockPos kept = new BlockPos(2, 64, 2);
        room().addSeat(SEAT);
        room().addSeat(kept);
        t.bodies.seats.add(kept);
        t.blocks.blocks.put(SEAT, new BlockState(new BlockKey("stone"), 0)); // broken, or a placement that failed
        t.blocks.blocks.put(kept, new BlockState(CHAIR, 0));
        for (int i = 0; i < 20; i++) {
            menu().nextSeat(colony, hall);
        }
        assertEquals(List.of(kept), room().seats());
    }

    @Test
    void theHallKeepsNoFoodForItsWaiter() {
        assertFalse(FoodRules.keepsFood(hall));
        assertFalse(room().keepsFood());
    }

    @Test
    void seatsAndCustomersAreSaved() {
        room().addSeat(SEAT);
        menu().storeCustomer(colony, hall, 3);
        JsonObject saved = new JsonObject();
        room().write(saved);
        DiningRoomModule back = new DiningRoomModule();
        back.read(saved);
        assertEquals(List.of(SEAT), back.seats());
        assertEquals(Set.of(3), back.customers());
    }
}
