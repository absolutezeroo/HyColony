package dev.hycolony.plugin.ui.hut;

import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import dev.hycolony.core.logistics.warehouse.TaskRow;
import java.util.List;

/** The warehouse's Tasks tab (MC WarehouseRequestTaskModuleView): the tasks waiting for a courier. */
final class WarehouseTasksTab implements HutTab {
    private final List<TaskRow> queue;

    WarehouseTasksTab(List<TaskRow> queue) {
        this.queue = queue;
    }

    @Override
    public String document() {
        return "Pages/HyColony/WarehouseTasksTab.ui";
    }

    @Override
    public String icon() {
        return "info";
    }

    @Override
    public String descKey() {
        return "hycolony.ui.building.tab.tasks";
    }

    @Override
    public void render(UICommandBuilder ui, UIEventBuilder events, String root) {
        TaskRows.render(ui, root + " #WarehouseTasks", root + " #WarehouseTasksEmpty", queue);
    }
}
