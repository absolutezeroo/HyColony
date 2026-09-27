package dev.hycolony.plugin.ui.hut;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import dev.hycolony.core.colony.ui.tab.CourierTasksView;

/**
 * The courier hut's Tasks tab (MC CourierRequestTaskModuleView): the warehouse its courier serves, or that it has
 * none, then its task list.
 */
final class CourierTasksTab implements HutTab {
    private final CourierTasksView courier;

    CourierTasksTab(CourierTasksView courier) {
        this.courier = courier;
    }

    @Override
    public String document() {
        return "Pages/HyColony/CourierTasksTab.ui";
    }

    @Override
    public String labelKey() {
        return "hycolony.ui.building.tab.tasks";
    }

    @Override
    public void render(UICommandBuilder ui, UIEventBuilder events, String root) {
        ui.set(
                root + " #CourierWarehouse.Text",
                courier.warehouse()
                        .map(p -> Message.translation("hycolony.ui.courier.warehouse")
                                .param("p0", String.valueOf(p.x()))
                                .param("p1", String.valueOf(p.y()))
                                .param("p2", String.valueOf(p.z())))
                        .orElse(Message.translation("hycolony.ui.courier.noWarehouse")));
        TaskRows.render(ui, root + " #CourierTasks", root + " #CourierTasksEmpty", courier.tasks());
    }
}
