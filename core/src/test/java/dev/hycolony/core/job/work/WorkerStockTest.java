package dev.hycolony.core.job.work;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.BuildingType;
import dev.hycolony.core.building.module.ModuleProducer;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.inventory.CitizenEquipment;
import dev.hycolony.core.citizen.inventory.HeldItems;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.item.ToolInfo;
import dev.hycolony.core.kernel.item.ToolType;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.logistics.courier.DeliverymanHut;
import dev.hycolony.core.logistics.pickup.KeepToolsModule;
import dev.hycolony.core.testing.TestContexts;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** MC AbstractEntityAIBasic dump, keepX and getMostEfficientTool, for any worker's hut. */
class WorkerStockTest {
    private static final BlockPos HUT = new BlockPos(10, 64, 0);
    private static final ItemKey LOG = new ItemKey("log");
    private static final ItemKey PICK = new ItemKey("pickaxe");
    private static final ItemKey IRON_PICK = new ItemKey("iron_pickaxe");
    private static final ItemKey DIAMOND_PICK = new ItemKey("diamond_pickaxe");
    private static final ItemKey AXE = new ItemKey("axe");

    private final TestContexts t = new TestContexts();
    private final CitizenData citizen = new CitizenData(1);
    private final Colony colony;
    private final Building hut;
    private final WorkerStock stock;

    WorkerStockTest() {
        UUID alice = UUID.randomUUID();
        ColonyManager manager = t.manager();
        manager.foundation().begin(alice, "Alice", new BlockPos(0, 64, 0), 0);
        colony = manager.foundation().confirm(alice, "A").orElseThrow();
        manager.huts().place(colony, DeliverymanHut.TYPE_ID, HUT, 0, UUID.randomUUID());
        hut = colony.buildings().at(HUT).orElseThrow();
        colony.citizens().restore(citizen);
        stock = new WorkerStock(colony, citizen, hut, 32);
        t.catalog.tools.put(PICK, new ToolInfo(ToolType.PICKAXE, 1, 1f));
        t.catalog.tools.put(IRON_PICK, new ToolInfo(ToolType.PICKAXE, 0, 1f));
        t.catalog.tools.put(DIAMOND_PICK, new ToolInfo(ToolType.PICKAXE, 5, 1f));
    }

    @Test
    void dumpingTheHeldSlotReleasesTheHand() {
        assertTrue(colony.citizens().respawnBody(citizen.id()));
        BodyId body = colony.citizens().bodyOf(citizen.id()).orElseThrow();
        citizen.inventory().set(0, Optional.of(new ItemAmount(LOG, 20)));
        HeldItems.holdSlot(citizen, t.bodies, body, 0);

        stock.dumpKeepingHutRules(true);

        assertEquals(0, citizen.inventory().count(LOG));
        assertEquals(CitizenEquipment.NO_SLOT, citizen.equipment().held(CitizenEquipment.Hand.MAIN), "MC dump");
        assertNull(t.bodies.bodies.get(body).held);
    }

    @Test
    void aDumpIsDueAtTheWorkersOwnActionCount() {
        assertFalse(stock.dumpDue(31));
        assertTrue(stock.dumpDue(32));
    }

    @Test
    void theMostEfficientToolIsTheLowestLevelOneTheHutAllows() {
        citizen.inventory().set(0, Optional.of(new ItemAmount(PICK, 1)));
        citizen.inventory().set(1, Optional.of(new ItemAmount(IRON_PICK, 1)));
        citizen.inventory().set(2, Optional.of(new ItemAmount(DIAMOND_PICK, 1)));

        assertEquals(OptionalInt.of(1), stock.toolInInventory(ToolType.PICKAXE));
        assertEquals(OptionalInt.empty(), stock.toolInInventory(ToolType.AXE));
    }

    @Test
    void aDumpByTheHutsKeepRulesKeepsOnlyWhatTheyKeepInTheInventory() {
        BlockPos keeperPos = new BlockPos(30, 64, 0);
        Building keeper = Building.create(
                new BuildingType(
                        "test:keeper",
                        "hut.keeper",
                        1,
                        List.of(new ModuleProducer(
                                "keepTools", () -> new KeepToolsModule(EnumSet.of(ToolType.PICKAXE))))),
                keeperPos,
                0);
        colony.buildings().add(keeper);
        WorkerStock keeperStock = new WorkerStock(colony, citizen, keeper, 32);
        t.catalog.tools.put(AXE, new ToolInfo(ToolType.AXE, 1, 1f));
        citizen.inventory().insert(new ItemAmount(LOG, 20), k -> 64);
        citizen.inventory().set(1, Optional.of(new ItemAmount(PICK, 1)));
        citizen.inventory().set(2, Optional.of(new ItemAmount(IRON_PICK, 1)));
        citizen.inventory().set(3, Optional.of(new ItemAmount(AXE, 1)));

        keeperStock.dumpKeepingHutRules(true);

        assertEquals(1, citizen.inventory().count(PICK), "MC keepX (1, true): one pickaxe stays");
        assertEquals(
                List.of(new ItemAmount(LOG, 20), new ItemAmount(IRON_PICK, 1), new ItemAmount(AXE, 1)),
                t.containers.stacks(keeperPos),
                "no keep rule for logs or axes: they go, whatever the builder keeps");
    }
}
