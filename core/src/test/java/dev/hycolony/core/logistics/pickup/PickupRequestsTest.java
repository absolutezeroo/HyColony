package dev.hycolony.core.logistics.pickup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ColonyManager;
import dev.hycolony.core.colony.persistence.ColonySerializer;
import dev.hycolony.core.colony.territory.TerritoryIndex;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.model.Pickup;
import dev.hycolony.core.testing.TestContexts;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PickupRequestsTest {
    private final TestContexts t = new TestContexts();
    private final BlockPos hall = new BlockPos(0, 64, 0);
    private final Colony colony;
    private final Building hut;

    PickupRequestsTest() {
        ColonyManager manager = new ColonyManager(t.context());
        UUID alice = UUID.randomUUID();
        manager.foundation().begin(alice, "Alice", hall, 0);
        colony = manager.foundation().confirm(alice, "A").orElseThrow();
        hut = colony.buildings().at(hall).orElseThrow();
        colony.setDay(3);
    }

    private List<Pickup> pickups() {
        return colony.requests().byRequester(hut.requesterId()).stream()
                .map(Request::requestable)
                .filter(Pickup.class::isInstance)
                .map(Pickup.class::cast)
                .toList();
    }

    @Test
    void defaultPickupPriorityIsFive() {
        assertEquals(5, hut.pickupPriority());
    }

    @Test
    void priorityIsClampedToZeroAndTen() {
        for (int i = 0; i < 8; i++) {
            hut.alterPickupPriority(1);
        }
        assertEquals(10, hut.pickupPriority());
        for (int i = 0; i < 12; i++) {
            hut.alterPickupPriority(-1);
        }
        assertEquals(0, hut.pickupPriority());
    }

    @Test
    void pickupPriorityIsSavedAndReadBack() {
        hut.alterPickupPriority(-2);

        Colony loaded = ColonySerializer.read(ColonySerializer.write(colony), t.context(), new TerritoryIndex());

        assertEquals(3, loaded.buildings().at(hall).orElseThrow().pickupPriority());
    }

    @Test
    void atMostOneOpenPickupPerBuilding() {
        assertTrue(PickupRequests.createPickupRequest(colony, hut, 10, false));
        assertFalse(PickupRequests.createPickupRequest(colony, hut, 10, false));
        assertFalse(PickupRequests.createPickupRequest(colony, hut, 64, true));

        assertEquals(1, pickups().size());
    }

    @Test
    void pickupDayFollowsMcFormula() {
        PickupRequests.createPickupRequest(colony, hut, 20, false);

        // day + max(0, (10 - 5) - 20 / 16) = 3 + 4
        Pickup p = pickups().get(0);
        assertEquals(7, p.day());
        assertEquals(5, p.priority());
        assertEquals(20, p.quantity());
    }

    @Test
    void largePickupIsDueToday() {
        PickupRequests.createPickupRequest(colony, hut, 80, false);

        assertEquals(3, pickups().get(0).day());
    }

    @Test
    void forcedPickupHasPriorityTen() {
        hut.alterPickupPriority(-3);

        PickupRequests.createPickupRequest(colony, hut, 64, true);

        // MC keeps the hut's own priority (2) in the day formula even when forced: 3 + max(0, 8 - 4)
        Pickup p = pickups().get(0);
        assertEquals(Pickup.MAX_BUILDING_PRIORITY, p.priority());
        assertEquals(7, p.day());
    }

    @Test
    void priorityZeroNeverCreatesAPickup() {
        hut.alterPickupPriority(-5);

        assertFalse(PickupRequests.createPickupRequest(colony, hut, 64, false));
        assertTrue(pickups().isEmpty());
        assertTrue(PickupRequests.createPickupRequest(colony, hut, 64, true), "the player's force pickup still works");
    }
}
