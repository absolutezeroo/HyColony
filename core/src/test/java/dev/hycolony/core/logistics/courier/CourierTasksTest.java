package dev.hycolony.core.logistics.courier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.BuildingType;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.permission.Permissions;
import dev.hycolony.core.colony.territory.TerritoryIndex;
import dev.hycolony.core.construction.hut.ConstructionBuildingTypes;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.logistics.warehouse.WarehouseBuilding;
import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.RequestManager;
import dev.hycolony.core.request.model.Delivery;
import dev.hycolony.core.request.model.RequestToken;
import dev.hycolony.core.testing.TestContexts;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** MC {@code haveTasksSameSourceAndDest}, via {@link CourierTasks#withSameDestination}. */
class CourierTasksTest {
    private static final ItemKey STONE = new ItemKey("Rock_Stone");
    private final TestContexts t = new TestContexts();
    private final Colony colony = new Colony(
            t.context(),
            new TerritoryIndex(),
            new Colony.Founding(1, "T", new BlockPos(0, 64, 0), Permissions.createDefault(UUID.randomUUID(), "A")));
    private final RequestManager m = colony.requests();

    /**
     * A second warehouse type built with {@link WarehouseBuilding#TYPE}'s own fields: equal to it (a {@link
     * BuildingType} is a record), like a second registry or a reload would produce, but not the same instance.
     */
    private final BuildingType duplicateWarehouseType = new BuildingType(
            WarehouseBuilding.TYPE.id(),
            WarehouseBuilding.TYPE.hutBlockKey(),
            WarehouseBuilding.TYPE.maxLevel(),
            WarehouseBuilding.TYPE.modules());

    @Test
    void differentContainersOfTheSameWarehouseJoinEvenWhenItsTypeIsAnEqualButDistinctInstance() {
        assertNotSame(WarehouseBuilding.TYPE, duplicateWarehouseType);
        assertEquals(WarehouseBuilding.TYPE, duplicateWarehouseType);

        Building warehouse = Building.create(duplicateWarehouseType, new BlockPos(0, 64, 0), 0);
        colony.buildings().add(warehouse);
        BlockPos rackA = new BlockPos(1, 64, 0);
        BlockPos rackB = new BlockPos(2, 64, 0);
        warehouse.registeredBlocks().addContainer(rackA);
        warehouse.registeredBlocks().addContainer(rackB);

        Building target = Building.create(ConstructionBuildingTypes.RESIDENCE, new BlockPos(10, 64, 0), 0);
        colony.buildings().add(target);

        RequestToken tokenA = m.createAndAssign(
                warehouse, new Delivery(rackA, target.requesterId(), new ItemAmount(STONE, 1), 13), -1);
        RequestToken tokenB = m.createAndAssign(
                warehouse, new Delivery(rackB, target.requesterId(), new ItemAmount(STONE, 1), 13), -1);
        Request delivery = m.get(tokenA).orElseThrow();

        List<Request> joined = CourierTasks.withSameDestination(colony, List.of(tokenA, tokenB), delivery);

        assertEquals(
                List.of(tokenA, tokenB), joined.stream().map(Request::token).toList());
    }
}
