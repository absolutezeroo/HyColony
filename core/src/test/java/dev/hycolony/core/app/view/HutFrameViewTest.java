package dev.hycolony.core.app.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.app.ui.BuildingView;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.construction.hut.ConstructionBuildingTypes;
import dev.hycolony.core.job.WorkerModule;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.logistics.warehouse.WarehouseBuilding;
import dev.hycolony.core.testing.TestContexts;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** MC AbstractBuildingMainWindow: the hut's title, its main page kind and its workers' "Job: Name" lines. */
class HutFrameViewTest {
    private static final BlockPos HUT = new BlockPos(30, 64, 0);
    private final TestContexts t = new TestContexts();
    private final UUID alice = UUID.randomUUID();
    private final ColonyManager manager = t.manager();
    private final Colony colony;

    HutFrameViewTest() {
        manager.foundation().begin(alice, "Alice", new BlockPos(0, 64, 0), 0);
        colony = manager.foundation().confirm(alice, "A").orElseThrow();
    }

    private Building place(String typeId, BlockPos pos) {
        manager.huts().place(colony, typeId, pos, 0, alice);
        return colony.buildings().at(pos).orElseThrow();
    }

    private BuildingView view(BlockPos pos) {
        manager.windows().openBuilding(alice, pos);
        return (BuildingView) t.ui.shown.get(alice);
    }

    @Test
    void openingAHutSaysWhetherItsWindowShowed() {
        place(ConstructionBuildingTypes.BUILDER.id(), HUT);
        assertTrue(manager.windows().openBuilding(alice, HUT));
        assertFalse(manager.windows().openBuilding(alice, new BlockPos(90, 64, 0)), "no hut there");
        assertFalse(manager.windows().openBuilding(UUID.randomUUID(), HUT), "a stranger may not look");
    }

    @Test
    void theTitleIsTheCustomNameElseEmpty() {
        Building b = place(ConstructionBuildingTypes.BUILDER.id(), HUT);
        assertEquals("", view(HUT).customName());
        b.setCustomName("Bob's");
        assertEquals("Bob's", view(HUT).customName());
    }

    @Test
    void theMainPageKindFollowsTheModulesAsMc() {
        place(ConstructionBuildingTypes.BUILDER.id(), HUT);
        BlockPos home = new BlockPos(60, 64, 0);
        place(ConstructionBuildingTypes.RESIDENCE.id(), home);
        BlockPos store = new BlockPos(0, 64, 30);
        place(WarehouseBuilding.TYPE.id(), store);
        assertEquals(BuildingView.MainKind.WORKERS, view(HUT).mainKind());
        assertEquals(BuildingView.MainKind.LIVING, view(home).mainKind());
        assertEquals(BuildingView.MainKind.SIMPLE, view(store).mainKind());
    }

    @Test
    void workerLinesCarryTheirJob() {
        Building b = place(ConstructionBuildingTypes.BUILDER.id(), HUT);
        CitizenData ann = new CitizenData(1);
        ann.setName("Ann");
        colony.citizens().restore(ann);
        assertTrue(b.module(WorkerModule.class).orElseThrow().hire(colony, b, ann));
        assertEquals(
                List.of(new BuildingView.WorkerLine(1, "Ann", "hycolony:builder")),
                view(HUT).workers());
    }
}
