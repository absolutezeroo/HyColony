package dev.hycolony.core.logistics.warehouse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.permission.Permissions;
import dev.hycolony.core.colony.territory.TerritoryIndex;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.Inventory;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.port.Msg;
import dev.hycolony.core.testing.FakeNotifier;
import dev.hycolony.core.testing.TestContexts;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WarehouseStorageTest {
    private static final ItemKey STONE = new ItemKey("Rock_Stone");
    private static final ItemKey DIRT = new ItemKey("Soil_Dirt");
    private static final ItemKey SAND = new ItemKey("Soil_Sand");
    private static final ItemKey PLANKS = new ItemKey("Wood_Planks");
    private static final ItemKey LOG = new ItemKey("Wood_Log");

    private final TestContexts t = new TestContexts();
    private final UUID owner = UUID.randomUUID();
    private final Colony colony = new Colony(
            t.context(),
            new TerritoryIndex(),
            new Colony.Founding(1, "T", new BlockPos(0, 64, 0), Permissions.createDefault(owner, "A")));
    private final BlockPos hut = new BlockPos(10, 64, 10);
    private final BlockPos rack1 = new BlockPos(11, 64, 10);
    private final BlockPos rack2 = new BlockPos(12, 64, 10);
    private final Building warehouse = Building.create(WarehouseBuilding.TYPE, hut, 0);

    WarehouseStorageTest() {
        warehouse.setLevel(1);
        warehouse.setBuilt(true);
        warehouse.addContainer(rack1);
        warehouse.addContainer(rack2);
        colony.buildings().add(warehouse);
    }

    private void rack(BlockPos pos, int slots, ItemKey... held) {
        t.containers.slots.put(pos, slots);
        Map<ItemKey, Integer> content = new LinkedHashMap<>();
        for (ItemKey item : held) {
            content.put(item, 1);
        }
        t.containers.containers.put(pos, content);
    }

    private static Inventory carrying(ItemKey item, int count) {
        Inventory inv = new Inventory(3);
        inv.set(0, Optional.of(new ItemAmount(item, count)));
        return inv;
    }

    private void store(Inventory inv) {
        warehouse.module(WarehouseStorage.class).orElseThrow().store(colony, warehouse, inv);
    }

    private int in(BlockPos pos, ItemKey item) {
        return t.containers.count(List.of(pos), item);
    }

    private List<Msg> messages() {
        return t.notifier.sent.stream().map(FakeNotifier.Sent::msg).toList();
    }

    @Test
    void storesIntoTheRackAlreadyHoldingTheItem() {
        rack(hut, 5, DIRT);
        rack(rack1, 5);
        rack(rack2, 5, STONE);
        Inventory inv = carrying(STONE, 10);

        store(inv);

        assertEquals(11, in(rack2, STONE));
        assertEquals(0, in(rack1, STONE));
        assertTrue(inv.contents().isEmpty());
    }

    @Test
    void thenIntoAnEmptyRackThenTheFreest() {
        rack(hut, 3, DIRT);
        rack(rack1, 5, SAND);
        rack(rack2, 4);

        store(carrying(PLANKS, 4));
        assertEquals(4, in(rack2, PLANKS));

        store(carrying(LOG, 2)); // hut 2 free, rack1 4 free, rack2 3 free
        assertEquals(2, in(rack1, LOG));
    }

    @Test
    void fullWarehouseSendsAMessageAtMostEveryFiveMinutes() {
        rack(hut, 1, DIRT);
        rack(rack1, 1, SAND);
        rack(rack2, 1, PLANKS);
        Inventory inv = carrying(STONE, 10);
        t.clock.tick = 10_000;

        store(inv);
        assertEquals(List.of(Msg.of("hycolony.warehouse.full")), messages());
        assertEquals(10, inv.count(STONE));

        t.clock.tick += WarehouseStorage.TICKS_FIVE_MIN;
        store(inv);
        assertEquals(1, messages().size());

        t.clock.tick++;
        warehouse.setLevel(WarehouseBuilding.MAX_LEVEL);
        store(inv);
        assertEquals(List.of(Msg.of("hycolony.warehouse.full"), Msg.of("hycolony.warehouse.fullMax")), messages());
        assertEquals(owner, t.notifier.sent.get(0).player());
    }
}
