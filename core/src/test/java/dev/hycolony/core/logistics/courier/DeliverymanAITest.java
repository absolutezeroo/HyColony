package dev.hycolony.core.logistics.courier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.citizen.CitizenAI;
import dev.hycolony.core.citizen.CitizenState;
import dev.hycolony.core.citizen.Skill;
import dev.hycolony.core.citizen.vitals.EndedWalk;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.item.BlockKey;
import dev.hycolony.core.kernel.item.BlockState;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.nav.WalkEnd;
import dev.hycolony.core.kernel.port.NavStatus;
import dev.hycolony.core.request.model.RequestToken;
import dev.hycolony.core.testing.FakeBodies;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** MC EntityAIWorkDeliveryman: decide, dump, working state, speed and finishRequest; the rain rule through CitizenAI. */
class DeliverymanAITest extends CourierAITestBase {
    @Test
    void withoutTaskItWaitsAtTheWarehouse() {
        hire();

        run(300);

        assertEquals("START_WORKING", ai.stateName());
        assertTrue(Vec3.center(warehouse.position()).distance(t.bodies.bodies.get(body).position) < 3);
    }

    @Test
    void courierStoresFromInsideByTheWarehouseNotThroughItsRoof() {
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                t.blocks.blocks.put(new BlockPos(x, 63, z), new BlockState(new BlockKey("Soil_Dirt"), 0));
            }
        }
        hire();
        citizen.inventory().insert(new ItemAmount(LOG, 4), i -> 64);
        t.bodies.instant = false;
        Vec3 roof = new Vec3(0.5, 69, 0.5);

        for (int i = 0; i < 1_000 && carried(LOG) > 0; i++) {
            run(1);
            FakeBodies.Body b = t.bodies.bodies.get(body);
            if (b.status == NavStatus.MOVING) { // every walk ends on the roof, as a partial path would
                b.position = roof;
                b.status = NavStatus.ARRIVED;
            }
            if (i == 100) {
                assertEquals(4, carried(LOG), "nothing stored through the roof");
            }
        }

        Vec3 inside = Vec3.center(new BlockPos(0, 64, -1));
        assertEquals(inside, t.bodies.moves.getFirst(), "walks to the cell beside the hut block");
        assertEquals(List.of(inside), t.bodies.teleports, "stuck on the roof, it is brought inside as MC does");
        assertEquals(
                Optional.of(WalkEnd.TELEPORTED),
                citizen.vitals().lastWalkEnd().map(EndedWalk::how),
                "its walker reports to its vital signs");
        assertEquals(4, t.containers.count(warehouse.containers(), LOG));
    }

    @Test
    void aFailingMachineIsCountedAsTheCouriersFailures() {
        hire();
        citizen.inventory().insert(new ItemAmount(LOG, 4), i -> 64);
        t.bodies.instant = false;
        t.bodies.failNav = true;

        runUntil(() -> ai.failures() > 0);
    }

    @Test
    void withoutTaskItStoresWhatItCarriesAtTheWarehouse() {
        hire();
        citizen.inventory().insert(new ItemAmount(LOG, 4), i -> 64);

        runUntil(() -> carried(LOG) == 0);

        assertEquals(4, t.containers.count(warehouse.containers(), LOG));
    }

    @Test
    void aDeliveryStartsWithAnEmptyInventory() {
        hire();
        citizen.inventory().insert(new ItemAmount(LOG, 4), i -> 64);
        put(RACK, STONE, 5);
        RequestToken task = delivery(RACK, STONE, 5);

        runUntil(() -> completed(task));

        assertEquals(4, t.containers.count(warehouse.containers(), LOG));
        assertEquals(5, stored(target.position(), STONE));
    }

    @Test
    void withoutWarehouseItDoesNotWork() {
        hire();
        run(DeliverymanAI.DECISION_DELAY + 20);
        assertTrue(job.isWorking());

        colony.buildings().remove(warehouse.position());
        colony.buildings().onColonyTick(colony);
        run(200);

        assertFalse(job.isWorking());
    }

    @Test
    void courierStopsInTheRain() {
        hire();
        hut.setLevel(1); // below max level: MC WorkerBuildingModule.canWorkDuringTheRain is false
        CitizenAI citizenAI = new CitizenAI(colony, citizen, body);
        assertTrue(tickUntil(citizenAI, CitizenState.WORKING, 40));

        t.world.raining = true;

        assertTrue(tickUntil(citizenAI, CitizenState.IDLE, 10));
        assertFalse(tickUntil(citizenAI, CitizenState.WORKING, 420));

        hut.setLevel(DeliverymanHut.MAX_LEVEL);

        assertTrue(tickUntil(citizenAI, CitizenState.WORKING, 420), "a max-level hut works in the rain");
    }

    private boolean tickUntil(CitizenAI citizenAI, CitizenState state, int max) {
        for (int i = 0; i < max && citizenAI.state() != state; i++) {
            t.clock.tick++;
            citizenAI.tick();
        }
        return citizenAI.state() == state;
    }

    @Test
    void speedGrowsWithAgility() {
        hire(50, 0);

        assertEquals(1.5, t.bodies.bodies.get(body).speed, 1e-9); // (0.3 + 50 x 0.003) / 0.3

        citizen.skills().set(Skill.Agility, 10, 0);
        run(200);

        assertEquals(1.1, t.bodies.bodies.get(body).speed, 1e-9);
    }

    @Test
    void finishingAFailedHeadWithNothingLoadedStillPopsIt() {
        hire();
        RequestToken task = delivery(RACK, STONE, 5);
        assertEquals(Optional.of(task), job.currentTask(colony).map(r -> r.token()));

        job.finishRequest(colony, false);

        assertTrue(failed(task));
        assertEquals(List.of(), job.taskQueue());
    }
}
