package dev.hycolony.plugin.ui;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import dev.hycolony.core.colony.ColonyManager;
import dev.hycolony.core.colony.action.WorkOrderActions;
import dev.hycolony.core.colony.ui.BuilderTabs;
import dev.hycolony.core.construction.shared.BuilderSettingsModule.Mode;
import dev.hycolony.core.kernel.BlockPos;
import java.util.List;
import java.util.UUID;

/**
 * The builder hut's Work orders tab (MC WorkOrderModuleWindow): name, distance, the current order framed in green,
 * Cancel on orders claimed here, Select on the others in MANUAL mode (disabled with the reason as tooltip).
 */
final class BuilderOrdersTab {
    /** MC's workOrderBox colour for the current order: (0, 170, 0). */
    private static final String CURRENT_FRAME = "#00aa00";

    private final ColonyManager manager;
    private final UUID player;
    private final BlockPos hut;
    private final BuilderTabs tabs;
    private final boolean canManage;

    BuilderOrdersTab(ColonyManager manager, UUID player, BlockPos hut, BuilderTabs tabs, boolean canManage) {
        this.manager = manager;
        this.player = player;
        this.hut = hut;
        this.tabs = tabs;
        this.canManage = canManage;
    }

    void render(UICommandBuilder ui, UIEventBuilder events) {
        List<BuilderTabs.OrderLine> lines = tabs.orders();
        if (lines.isEmpty()) {
            ui.set("#OrdersEmpty.Visible", true);
            ui.set("#OrdersEmpty.Text", Message.translation("hycolony.ui.workorders.empty"));
        }
        for (int i = 0; i < lines.size(); i++) {
            BuilderTabs.OrderLine o = lines.get(i);
            String row = "#BuilderOrders[" + i + "]";
            ui.append("#BuilderOrders", "Pages/HyColony/BuilderOrderRow.ui");
            if (o.current()) {
                ui.set(row + ".Background", CURRENT_FRAME);
            }
            ui.set(
                    row + " #Title.TextSpans",
                    Message.translation("hycolony.ui.workorders.line")
                            .param("p0", BuildingMainTab.typeName(o.type()))
                            .param("p1", ColonyPage.buildingName(o.buildingName()))
                            .param("p2", String.valueOf(o.targetLevel())));
            ui.set(
                    row + " #Distance.Text",
                    Message.translation("hycolony.ui.builder.orders.distance")
                            .param("p0", String.valueOf(o.distance())));
            button(ui, events, row + " #Button", o, i);
        }
    }

    private void button(UICommandBuilder ui, UIEventBuilder events, String button, BuilderTabs.OrderLine o, int i) {
        if (!canManage || (!o.claimedHere() && tabs.mode() != Mode.MANUAL)) {
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
    void handle(ColonyPage.Act act) {
        List<BuilderTabs.OrderLine> lines = tabs.orders();
        if (act.index < 0 || act.index >= lines.size()) {
            return;
        }
        int orderId = lines.get(act.index).id();
        switch (act.action) {
            case "select" -> manager.workOrders().select(player, hut, orderId);
            case "orderCancel" -> manager.workOrders().cancelFromBuilder(player, hut, orderId);
            default -> {}
        }
    }
}
