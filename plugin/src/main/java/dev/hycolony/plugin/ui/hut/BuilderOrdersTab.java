package dev.hycolony.plugin.ui.hut;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.app.action.WorkOrderActions;
import dev.hycolony.core.construction.hut.WorkOrderListView;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.plugin.ui.ColonyPage;
import java.util.List;
import java.util.UUID;

/**
 * The builder hut's Work orders tab (MC WorkOrderModuleWindow): name, distance, the current order framed in green,
 * Cancel on orders claimed here, Select on the others in MANUAL mode (disabled with the reason as tooltip).
 */
final class BuilderOrdersTab implements HutTab {
    /** MC's workOrderBox colour for the current order: (0, 170, 0). */
    private static final String CURRENT_FRAME = "#00aa00";

    private final ColonyManager manager;
    private final UUID player;
    private final BlockPos hut;
    private final WorkOrderListView tabs;
    private final boolean canManage;

    BuilderOrdersTab(ColonyManager manager, UUID player, BlockPos hut, WorkOrderListView tabs, boolean canManage) {
        this.manager = manager;
        this.player = player;
        this.hut = hut;
        this.tabs = tabs;
        this.canManage = canManage;
    }

    @Override
    public String document() {
        return "Pages/HyColony/BuilderOrdersTab.ui";
    }

    @Override
    public String icon() {
        return "info";
    }

    @Override
    public String descKey() {
        return "hycolony.ui.building.tab.orders";
    }

    @Override
    public void render(UICommandBuilder ui, UIEventBuilder events, String root) {
        List<WorkOrderListView.OrderLine> lines = tabs.orders();
        if (lines.isEmpty()) {
            ui.set(root + " #OrdersEmpty.Visible", true);
            ui.set(root + " #OrdersEmpty.Text", Message.translation("hycolony.ui.workorders.empty"));
        }
        for (int i = 0; i < lines.size(); i++) {
            WorkOrderListView.OrderLine o = lines.get(i);
            String row = root + " #BuilderOrders[" + i + "]";
            ui.append(root + " #BuilderOrders", "Pages/HyColony/BuilderOrderRow.ui");
            if (o.current()) {
                ui.set(row + ".Background", CURRENT_FRAME);
            }
            ui.set(
                    row + " #Title.TextSpans",
                    Message.translation("hycolony.ui.workorders.line")
                            .param("p0", ColonyPage.workOrderTypeName(o.type()))
                            .param("p1", ColonyPage.buildingName(o.buildingName()))
                            .param("p2", String.valueOf(o.targetLevel())));
            ui.set(
                    row + " #Distance.Text",
                    Message.translation("hycolony.ui.builder.orders.distance")
                            .param("p0", String.valueOf(o.distance())));
            button(ui, events, row + " #Button", o, i);
        }
    }

    private void button(
            UICommandBuilder ui, UIEventBuilder events, String button, WorkOrderListView.OrderLine o, int i) {
        if (!canManage || !tabs.selectable(o)) {
            ui.set(button + ".Visible", false);
            return;
        }
        if (o.claimedHere()) {
            ui.set(button + ".Text", Message.translation("hycolony.ui.builder.orders.cancel"));
            ColonyPage.bind(events, button, "orderCancel", i);
            return;
        }
        ui.set(button + ".Text", Message.translation("hycolony.ui.builder.orders.select"));
        if (o.selectRefusal().isEmpty()) {
            ColonyPage.bind(events, button, "select", i);
        } else {
            ui.set(button + ".Disabled", true);
            ui.set(
                    button + ".TooltipText",
                    Message.translation(
                            WorkOrderActions.selectRefusalKey(o.selectRefusal().get())));
        }
    }

    /** Select and Cancel go to the core, which checks MANAGE_HUTS and shows the window again. */
    @Override
    public void handle(ColonyPage.Act act) {
        List<WorkOrderListView.OrderLine> lines = tabs.orders();
        if (act.index() < 0 || act.index() >= lines.size()) {
            return;
        }
        int orderId = lines.get(act.index()).id();
        switch (act.action()) {
            case "select" -> manager.workOrders().select(player, hut, orderId);
            case "orderCancel" -> manager.workOrders().cancelFromBuilder(player, hut, orderId);
            default -> {}
        }
    }
}
