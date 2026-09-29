package dev.hycolony.plugin.ui.hut;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import dev.hycolony.core.logistics.warehouse.CourierAssignmentView;

/**
 * The warehouse's Couriers tab (MC CourierAssignmentModuleView): how many are attached out of the maximum, and their
 * names.
 *
 * <p>Deviation from MC: the couriers list only shows; MC's assignment window also lets the player attach or detach a
 * courier and cycle the hiring mode, here the core attaches every courier automatically.
 */
final class WarehouseCouriersTab implements HutTab {
    private final CourierAssignmentView assignment;

    WarehouseCouriersTab(CourierAssignmentView assignment) {
        this.assignment = assignment;
    }

    @Override
    public String document() {
        return "Pages/HyColony/WarehouseCouriersTab.ui";
    }

    @Override
    public String labelKey() {
        return "hycolony.ui.building.tab.couriers";
    }

    @Override
    public void render(UICommandBuilder ui, UIEventBuilder events, String root) {
        ui.set(
                root + " #CouriersCount.Text",
                Message.translation("hycolony.ui.warehouse.couriers")
                        .param("p0", String.valueOf(assignment.couriers().size()))
                        .param("p1", String.valueOf(assignment.maxCouriers())));
        if (assignment.couriers().isEmpty()) {
            ui.set(root + " #CouriersEmpty.Visible", true);
            ui.set(root + " #CouriersEmpty.Text", Message.translation("hycolony.ui.warehouse.noCouriers"));
        }
        String list = root + " #Couriers";
        for (int i = 0; i < assignment.couriers().size(); i++) {
            String row = list + "[" + i + "]";
            ui.append(list, "Pages/HyColony/WorkerRow.ui");
            ui.set(row + " #Name.Text", assignment.couriers().get(i));
            ui.set(row + " #Button.Visible", false);
        }
    }
}
