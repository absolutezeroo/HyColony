package dev.hycolony.plugin.ui.hut;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import dev.hycolony.core.logistics.courier.CourierTasksView;
import dev.hycolony.core.logistics.warehouse.TaskRow;
import java.util.List;
import java.util.Optional;

/**
 * A hut's Tasks page (MC WindowHutRequestTaskModule over a RequestTaskModuleView: the farmer's crafter queue, the
 * courier's queue, the warehouse's queue). The courier hut also says which warehouse its courier serves, or that it
 * has none, so the player sees why nothing moves (HyColony's addition).
 */
final class TasksTab implements HutTab {
    private final List<TaskRow> tasks;
    private final Optional<Message> extra;

    TasksTab(List<TaskRow> tasks, Optional<Message> extra) {
        this.tasks = tasks;
        this.extra = extra;
    }

    /** The courier hut's page: its queue and the warehouse it serves. */
    static TasksTab courier(CourierTasksView courier) {
        return new TasksTab(
                courier.tasks(),
                Optional.of(courier.warehouse()
                        .map(p -> Message.translation("hycolony.ui.courier.warehouse")
                                .param("p0", String.valueOf(p.x()))
                                .param("p1", String.valueOf(p.y()))
                                .param("p2", String.valueOf(p.z())))
                        .orElse(Message.translation("hycolony.ui.courier.noWarehouse"))));
    }

    @Override
    public String document() {
        return "Pages/HyColony/Hut/Tasks.ui";
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
        ui.set(root + " #Desc.Text", Message.translation(descKey()));
        TaskRows.render(ui, root + " #Tasks", root + " #Empty", tasks);
        extra.ifPresent(m -> {
            ui.set(root + " #Extra.Visible", true);
            ui.set(root + " #Extra.Text", m);
        });
    }
}
