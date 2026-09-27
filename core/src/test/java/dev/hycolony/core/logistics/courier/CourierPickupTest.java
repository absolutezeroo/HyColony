package dev.hycolony.core.logistics.courier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.Skill;
import dev.hycolony.core.construction.hut.ConstructionBuildingTypes;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.item.ToolInfo;
import dev.hycolony.core.kernel.item.ToolType;
import dev.hycolony.core.request.model.RequestState;
import dev.hycolony.core.request.model.RequestToken;
import org.junit.jupiter.api.Test;

/** MC EntityAIWorkDeliveryman.pickup. */
class CourierPickupTest extends CourierAITestBase {
    /** MC AbstractBuilding.onRequestedRequestComplete: the hut receives its completed pickup at once. */
    @Test
    void aCompletedPickupIsReceivedByItsHutAndLeavesTheRequestSystem() {
        hire();
        put(target.position(), LOG, 10);
        RequestToken task = pickup(target, 5);

        runUntil(() -> carried(LOG) == 10 && m.all().isEmpty());

        assertEquals(RequestState.RECEIVED, made.get(task).state());
    }

    @Test
    void pickupTakesWhatTheHutDoesNotKeepOneSlotPerStep() {
        hire();
        put(target.position(), LOG, 10);
        put(target.position(), DIRT, 4);
        RequestToken task = pickup(target, 5);

        runUntil(() -> carried(LOG) == 10);
        assertEquals(0, carried(DIRT)); // one slot per step
        runUntil(() -> completed(task));

        assertEquals(4, carried(DIRT));
        assertEquals(awarded(0.05), citizen.skills().experience(Skill.Agility), 1e-9);
        runUntil(() -> stored(warehouse.position(), LOG) == 10); // no task left: stored at the warehouse
    }

    @Test
    void pickupLeavesWhatTheHutKeeps() {
        hire();
        ItemKey pick = new ItemKey("Tool_Pickaxe_Wood");
        t.catalog.tools.put(pick, new ToolInfo(ToolType.PICKAXE, 0, 1f));
        Building builderHut = building(ConstructionBuildingTypes.BUILDER, new BlockPos(15, 64, 0), 1);
        put(builderHut.position(), pick, 3);
        RequestToken task = pickup(builderHut, 5);

        runUntil(() -> completed(task));

        assertEquals(2, carried(pick));
        assertEquals(1, stored(builderHut.position(), pick)); // MC keepX: one tool per type
    }

    @Test
    void aPriorityTenPickupIsDumpedRightAway() {
        hire();
        put(target.position(), LOG, 10);
        RequestToken task = pickup(target, 10);

        runUntil(() -> completed(task));
        run(5);

        assertEquals("DUMPING", ai.stateName());
    }

    @Test
    void pickupStopsAtTheCarryLimitOfTheHutLevel() {
        hut.setLevel(1); // 2 stacks
        hire();
        put(target.position(), LOG, 10);
        put(target.position(), DIRT, 4);
        put(target.position(), STONE, 3);
        pickup(target, 5);

        runUntil(() -> carried(DIRT) == 4);
        runUntil(() -> "DUMPING".equals(ai.stateName()));

        assertEquals(0, carried(STONE));
    }

    @Test
    void aPickupFromAVanishedBuildingFails() {
        hire();
        Building gone = building(ConstructionBuildingTypes.RESIDENCE, new BlockPos(30, 64, 0), 1);
        RequestToken task = pickup(gone, 5);
        colony.buildings().remove(gone.position());

        runUntil(() -> failed(task));
        assertTrue(job.taskQueue().isEmpty());
    }

    @Test
    void aCancelledPickupDoesNotCarryItsKeepRulesToTheNextHut() {
        hire();
        ItemKey pick = new ItemKey("Tool_Pickaxe_Wood");
        t.catalog.tools.put(pick, new ToolInfo(ToolType.PICKAXE, 0, 1f));
        Building builderHut = building(ConstructionBuildingTypes.BUILDER, new BlockPos(15, 64, 0), 1);
        put(builderHut.position(), LOG, 10); // taken first
        put(builderHut.position(), pick, 3); // the builder hut keeps one
        RequestToken first = pickup(builderHut, 5);
        runUntil(() -> carried(LOG) == 10);

        m.updateState(first, RequestState.CANCELLED);
        put(target.position(), pick, 3); // the residence keeps no tool
        RequestToken second = pickup(target, 5);
        runUntil(() -> completed(second));

        assertEquals(3, carried(pick));
        assertEquals(3, stored(builderHut.position(), pick));
    }
}
