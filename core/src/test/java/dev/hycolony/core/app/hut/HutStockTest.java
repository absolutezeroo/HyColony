package dev.hycolony.core.app.hut;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.app.ui.BuildingView;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.construction.hut.ConstructionBuildingTypes;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.testing.TestContexts;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** MC WindowHutAllInventory: every item the hut and its racks hold, with the containers Locate highlights. */
class HutStockTest {
    private static final BlockPos HUT = new BlockPos(30, 64, 0);
    private static final ItemKey STONE = new ItemKey("hytale:stone");
    private static final ItemKey LOG = new ItemKey("hytale:log");
    private final TestContexts t = new TestContexts();
    private final UUID alice = UUID.randomUUID();
    private final ColonyManager manager = t.manager();

    @Test
    void itemsListEveryContainerHoldingThem() {
        manager.foundation().begin(alice, "Alice", new BlockPos(0, 64, 0), 0);
        Colony c = manager.foundation().confirm(alice, "A").orElseThrow();
        manager.huts().place(c, ConstructionBuildingTypes.BUILDER.id(), HUT, 0, alice);
        BlockPos rack = new BlockPos(31, 64, 0);
        c.buildings().at(HUT).orElseThrow().registeredBlocks().addContainer(rack);
        t.containers.containers.put(HUT, new HashMap<>(Map.of(STONE, 3)));
        t.containers.containers.put(rack, new HashMap<>(Map.of(STONE, 70, LOG, 2)));

        manager.windows().openBuilding(alice, HUT);
        List<HutStock> stock = ((BuildingView) t.ui.shown.get(alice)).stock();

        HutStock stone =
                stock.stream().filter(s -> s.item().equals(STONE)).findFirst().orElseThrow();
        assertEquals(73, stone.count());
        assertEquals(List.of(new HutStock.Holder(rack, 70), new HutStock.Holder(HUT, 3)), stone.holders());
        HutStock log =
                stock.stream().filter(s -> s.item().equals(LOG)).findFirst().orElseThrow();
        assertEquals(List.of(new HutStock.Holder(rack, 2)), log.holders());
    }

    @Test
    void countsAbbreviateAsMcUtilsFormat() {
        assertEquals("999", HutStock.abbreviate(999));
        assertEquals("1k", HutStock.abbreviate(1000));
        assertEquals("1.2k", HutStock.abbreviate(1234));
        assertEquals("12k", HutStock.abbreviate(12345), "MC keeps a decimal under 10 only");
        assertEquals("1.5M", HutStock.abbreviate(1_500_000));
    }
}
