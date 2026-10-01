package dev.hycolony.core.app.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.app.ui.BuildingView;
import dev.hycolony.core.building.BuildingType;
import dev.hycolony.core.building.module.ModuleProducer;
import dev.hycolony.core.building.module.ModuleTab;
import dev.hycolony.core.citizen.home.ResidentsView;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.construction.hut.ConstructionBuildingTypes;
import dev.hycolony.core.construction.hut.WorkOrderListView;
import dev.hycolony.core.construction.resources.BuilderResourcesView;
import dev.hycolony.core.construction.resources.BuildingResourcesModule;
import dev.hycolony.core.construction.shared.BuilderSettingsView;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.logistics.courier.CourierTasksView;
import dev.hycolony.core.logistics.courier.DeliverymanHut;
import dev.hycolony.core.logistics.warehouse.CourierAssignmentView;
import dev.hycolony.core.logistics.warehouse.WarehouseBuilding;
import dev.hycolony.core.logistics.warehouse.WarehouseTasksView;
import dev.hycolony.core.testing.TestContexts;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * A hut window's tabs come from its modules, one per MC module view (MC BuildingModules producers, in the order of
 * ModBuildingsInitializer); every hut shows its inventory summary (MC WindowHutAllInventory).
 */
class ModuleTabsTest {
    /** A pack hut with the builder's resources module only. */
    private static final BuildingType RESOURCES_ONLY = new BuildingType(
            "test:resources", "hut.test", 1, List.of(new ModuleProducer("resources", BuildingResourcesModule::new)));

    private final TestContexts t = new TestContexts();
    private final ColonyManager manager;
    private final UUID alice = UUID.randomUUID();
    private final Colony colony;

    ModuleTabsTest() {
        t.extraBuildingTypes.add(RESOURCES_ONLY);
        manager = t.manager();
        manager.foundation().begin(alice, "Alice", new BlockPos(0, 64, 0), 0);
        colony = manager.foundation().confirm(alice, "A").orElseThrow();
    }

    private BuildingView view(BuildingType type, BlockPos pos) {
        manager.huts().place(colony, type.id(), pos, 0, UUID.randomUUID());
        manager.windows().openBuilding(alice, pos);
        return (BuildingView) t.ui.shown.get(alice);
    }

    private static List<Class<?>> kinds(BuildingView v) {
        return v.tabs().stream().<Class<?>>map(ModuleTab::getClass).toList();
    }

    @Test
    void eachHutShowsTheTabsOfItsMcModuleViewsInOrder() {
        assertEquals(
                List.of(BuilderResourcesView.class, BuilderSettingsView.class, WorkOrderListView.class),
                kinds(view(ConstructionBuildingTypes.BUILDER, new BlockPos(20, 64, 0))));
        assertEquals(
                List.of(CourierAssignmentView.class, WarehouseTasksView.class),
                kinds(view(WarehouseBuilding.TYPE, new BlockPos(-20, 64, 0))));
        assertEquals(List.of(CourierTasksView.class), kinds(view(DeliverymanHut.TYPE, new BlockPos(0, 64, 20))));
        assertEquals(
                List.of(ResidentsView.class),
                kinds(view(ConstructionBuildingTypes.RESIDENCE, new BlockPos(0, 64, -20))));
    }

    @Test
    void aHutWithOnlyTheResourcesModuleShowsOnlyResources() {
        assertEquals(List.of(BuilderResourcesView.class), kinds(view(RESOURCES_ONLY, new BlockPos(20, 64, 20))));
    }

    @Test
    void aTabIsFoundByItsKind() {
        BuildingView v = view(WarehouseBuilding.TYPE, new BlockPos(-20, 64, 0));

        assertTrue(v.tab(CourierAssignmentView.class).isPresent());
        assertTrue(v.tab(BuilderResourcesView.class).isEmpty());
    }

    @Test
    void everyHutShowsItsStockMostHeldFirst() {
        BlockPos pos = new BlockPos(0, 64, -20);
        t.containers.insert(List.of(pos), new ItemAmount(new ItemKey("Wood_Oak_Trunk"), 3));
        t.containers.insert(List.of(pos), new ItemAmount(new ItemKey("Rock_Stone"), 40));

        assertEquals(
                List.of(
                        new ItemAmount(new ItemKey("Rock_Stone"), 40),
                        new ItemAmount(new ItemKey("Wood_Oak_Trunk"), 3)),
                view(ConstructionBuildingTypes.RESIDENCE, pos).stock());
    }
}
