package dev.hycolony.core.crafting.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.app.ui.BuildingView;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.module.ModuleTab;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.farming.hut.FarmerHut;
import dev.hycolony.core.farming.job.FarmerJob;
import dev.hycolony.core.job.WorkerModule;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.logistics.warehouse.TaskRow;
import dev.hycolony.core.request.model.RequestToken;
import dev.hycolony.core.request.model.StackRequest;
import dev.hycolony.core.testing.TestContexts;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** MC CrafterRequestTaskModuleView: the farmer hut's Tasks tab lists its crafters' task queues. */
class CrafterTasksViewTest {
    private static final BlockPos FARM = new BlockPos(30, 64, 0);
    private final TestContexts t = new TestContexts();
    private final UUID alice = UUID.randomUUID();
    private final ColonyManager manager = t.manager();

    @Test
    void theTasksTabListsTheCraftersQueueAfterTheSettings() {
        manager.foundation().begin(alice, "Alice", new BlockPos(0, 64, 0), 0);
        Colony c = manager.foundation().confirm(alice, "A").orElseThrow();
        manager.huts().place(c, FarmerHut.TYPE_ID, FARM, 0, alice);
        Building farm = c.buildings().at(FARM).orElseThrow();
        farm.setLevel(1);
        farm.setBuilt(true);
        CitizenData fred = new CitizenData(1);
        c.citizens().restore(fred);
        assertTrue(farm.module(WorkerModule.class).orElseThrow().hire(c, farm, fred));
        RequestToken token =
                c.requests().createAndAssign(farm, new StackRequest(new ItemKey("hytale:wheat"), 4, 4, true), -1);
        ((FarmerJob) fred.job().orElseThrow()).craftingTasks().onTaskBeingResolved(token);

        manager.windows().openBuilding(alice, FARM);
        BuildingView v = (BuildingView) t.ui.shown.get(alice);

        List<TaskRow> rows = v.tab(CrafterTasksView.class).orElseThrow().tasks();
        assertEquals(List.of(token), rows.stream().map(TaskRow::token).toList());
        List<ModuleTab> tabs = v.tabs();
        assertTrue(tabs.getLast() instanceof CrafterTasksView, "MC's farmer: recipes, fields, settings, then tasks");
    }
}
