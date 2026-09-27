package dev.hycolony.plugin.ui.logistics;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import dev.hycolony.core.colony.ui.tab.CourierTabs;

/**
 * The courier hut's Tasks tab (MC CourierRequestTaskModuleView): the warehouse its courier serves, or that it has
 * none, then its task list.
 */
public final class CourierTasksRenderer {
    private CourierTasksRenderer() {}

    /** Fills the Tasks tab's group of {@code Building.ui}. */
    public static void render(UICommandBuilder ui, CourierTabs c) {
        ui.set(
                "#CourierWarehouse.Text",
                c.warehouse()
                        .map(p -> Message.translation("hycolony.ui.courier.warehouse")
                                .param("p0", String.valueOf(p.x()))
                                .param("p1", String.valueOf(p.y()))
                                .param("p2", String.valueOf(p.z())))
                        .orElse(Message.translation("hycolony.ui.courier.noWarehouse")));
        TaskRows.render(ui, "#CourierTasks", "#CourierTasksEmpty", c.tasks());
    }
}
