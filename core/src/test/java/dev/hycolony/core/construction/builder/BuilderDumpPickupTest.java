package dev.hycolony.core.construction.builder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ColonyManager;
import dev.hycolony.core.construction.hut.ConstructionBuildingTypes;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.item.ToolInfo;
import dev.hycolony.core.kernel.item.ToolType;
import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.model.Pickup;
import dev.hycolony.core.testing.TestContexts;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** MC AbstractEntityAIBasic.dumpInventory: a worker's dump asks a courier to empty its hut. */
class BuilderDumpPickupTest {
    private static final BlockPos HUT = new BlockPos(10, 64, 0);
    private static final ItemKey LOG = new ItemKey("log");
    private static final ItemKey DIRT = new ItemKey("dirt");

    private final TestContexts t = new TestContexts();
    private final Colony colony;
    private final Building hut;
    private final CitizenData citizen = new CitizenData(1);
    private final BuilderStock stock;

    BuilderDumpPickupTest() {
        UUID alice = UUID.randomUUID();
        ColonyManager manager = new ColonyManager(t.context());
        manager.foundation().begin(alice, "Alice", new BlockPos(0, 64, 0), 0);
        colony = manager.foundation().confirm(alice, "A").orElseThrow();
        manager.huts().place(colony, ConstructionBuildingTypes.BUILDER.id(), HUT, 0);
        hut = colony.buildings().at(HUT).orElseThrow();
        colony.citizens().restore(citizen);
        stock = new BuilderStock(colony, citizen, hut);
    }

    private List<Pickup> pickups() {
        return colony.requests().byRequester(hut.requesterId()).stream()
                .map(Request::requestable)
                .filter(Pickup.class::isInstance)
                .map(Pickup.class::cast)
                .toList();
    }

    private void give(ItemKey item, int count) {
        citizen.inventory().insert(new ItemAmount(item, count), k -> 64);
    }

    @Test
    void builderDumpCreatesAPickup() {
        give(LOG, 20);

        stock.dump(Map.of());

        assertEquals(1, pickups().size());
        Pickup pickup = pickups().get(0);
        assertEquals(5, pickup.priority()); // the hut's, unforced
        assertEquals(20, pickup.quantity());
        assertEquals(colony.day() + 4, pickup.day()); // 10 - 5 - 20 / 16 days away
    }

    @Test
    void anEmptyDumpCreatesNoPickup() {
        stock.dump(Map.of());

        assertEquals(List.of(), pickups());
    }

    @Test
    void fullHutForcesAPickup() {
        t.containers.slots.put(HUT, 1);
        give(LOG, 20);
        give(DIRT, 5);

        stock.dump(Map.of());

        assertEquals(1, pickups().size());
        assertEquals(Pickup.MAX_BUILDING_PRIORITY, pickups().get(0).priority());
        assertEquals(5, citizen.inventory().count(DIRT)); // the dump ends at the full hut
    }

    @Test
    void priorityZeroFullHutCreatesNoPickup() {
        t.containers.slots.put(HUT, 1);
        hut.pickupPriority().set(0);
        give(LOG, 20);
        give(DIRT, 5);

        stock.dump(Map.of());

        assertEquals(List.of(), pickups());
    }

    /** MC: the damage is on the stack, so a tool left in the hut comes back as worn as it went. */
    @Test
    void aWornToolStoredInTheHutAndTakenBackKeepsItsWear() {
        ItemKey pick = new ItemKey("pickaxe");
        t.catalog.tools.put(pick, new ToolInfo(ToolType.PICKAXE, 0, 1f));
        t.catalog.maxStacks.put(pick, 1);
        citizen.inventory().set(0, Optional.of(new ItemAmount(pick, 1)));
        citizen.inventory().set(1, Optional.of(new ItemAmount(pick, 1, 7)));
        citizen.inventory().set(2, Optional.of(new ItemAmount(pick, 1)));

        stock.dump(Map.of()); // MC keepX: the first tool of each type stays

        assertEquals(List.of(new ItemAmount(pick, 1)), citizen.inventory().contents());
        assertTrue(t.containers.stacks(HUT).contains(new ItemAmount(pick, 1, 7)));
        citizen.inventory().set(0, Optional.empty());

        assertEquals(2, stock.take(pick, 2));

        assertTrue(citizen.inventory().contents().contains(new ItemAmount(pick, 1, 7)));
        assertTrue(citizen.inventory().contents().contains(new ItemAmount(pick, 1)));
    }
}
