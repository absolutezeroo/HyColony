package dev.hycolony.core.colony;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.building.BuildingType;
import dev.hycolony.core.colony.ui.BuildingView;
import dev.hycolony.core.colony.ui.tab.BuilderTabs;
import dev.hycolony.core.colony.ui.tab.CourierTabs;
import dev.hycolony.core.colony.ui.tab.ModuleTab;
import dev.hycolony.core.colony.ui.tab.WarehouseTabs;
import dev.hycolony.core.construction.hut.ConstructionBuildingTypes;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.logistics.courier.DeliverymanHut;
import dev.hycolony.core.logistics.warehouse.WarehouseBuilding;
import dev.hycolony.core.testing.TestContexts;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** A hut window's tabs come from its modules (MC BuildingEntry module views), in module order. */
class ModuleTabsTest {
    private final TestContexts t = new TestContexts();
    private final ColonyManager manager = new ColonyManager(t.context());
    private final UUID alice = UUID.randomUUID();
    private final Colony colony;

    ModuleTabsTest() {
        manager.foundation().begin(alice, "Alice", new BlockPos(0, 64, 0), 0);
        colony = manager.foundation().confirm(alice, "A").orElseThrow();
    }

    private BuildingView view(BuildingType type, BlockPos pos) {
        manager.huts().place(colony, type.id(), pos, 0);
        manager.windows().openBuilding(alice, pos);
        return (BuildingView) t.ui.shown.get(alice);
    }

    private static List<Class<?>> kinds(BuildingView v) {
        return v.tabs().stream().<Class<?>>map(ModuleTab::getClass).toList();
    }

    @Test
    void eachHutWindowShowsTheTabsOfItsOwnModules() {
        assertEquals(
                List.of(BuilderTabs.class), kinds(view(ConstructionBuildingTypes.BUILDER, new BlockPos(20, 64, 0))));
        assertEquals(List.of(WarehouseTabs.class), kinds(view(WarehouseBuilding.TYPE, new BlockPos(-20, 64, 0))));
        assertEquals(List.of(CourierTabs.class), kinds(view(DeliverymanHut.TYPE, new BlockPos(0, 64, 20))));
        assertEquals(List.of(), kinds(view(ConstructionBuildingTypes.RESIDENCE, new BlockPos(0, 64, -20))));
    }

    @Test
    void aTabIsFoundByItsKind() {
        BuildingView v = view(WarehouseBuilding.TYPE, new BlockPos(-20, 64, 0));

        assertTrue(v.tab(WarehouseTabs.class).isPresent());
        assertTrue(v.tab(BuilderTabs.class).isEmpty());
    }
}
