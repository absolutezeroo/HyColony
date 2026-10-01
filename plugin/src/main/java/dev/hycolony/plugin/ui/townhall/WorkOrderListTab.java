package dev.hycolony.plugin.ui.townhall;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.app.ui.WorkOrdersView;
import dev.hycolony.core.app.ui.WorkOrdersView.OrderLine;
import dev.hycolony.plugin.ui.ColonyPage;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The town hall Information tab's right page: the colony's work orders in execution order (MC
 * WindowInfoPage.fillWorkOrderList), with up, down and delete for managers. As in MC: no priority number, no up on
 * the first row, no down on the last.
 */
final class WorkOrderListTab {
    private final ColonyManager manager;
    private final UUID player;
    private final int colonyId;
    private final WorkOrdersView view;

    WorkOrderListTab(ColonyManager manager, UUID player, int colonyId, WorkOrdersView view) {
        this.manager = manager;
        this.player = player;
        this.colonyId = colonyId;
        this.view = view;
    }

    void render(UICommandBuilder ui, UIEventBuilder events, String root) {
        List<OrderLine> orders = view.orders();
        if (orders.isEmpty()) {
            ui.set(root + " #OrdersEmpty.Visible", true);
            ui.set(root + " #OrdersEmpty.Text", Message.translation("hycolony.ui.workorders.empty"));
        }
        for (int i = 0; i < orders.size(); i++) {
            OrderLine o = orders.get(i);
            String row = root + " #Orders[" + i + "]";
            ui.append(root + " #Orders", "Pages/HyColony/Mc/OrderRow.ui");
            ui.set(
                    row + " #Title.TextSpans",
                    Message.translation("hycolony.ui.workorders.line")
                            .param(
                                    "p0",
                                    Message.translation("hycolony.ui.workorder.type."
                                            + o.type().name().toLowerCase(Locale.ROOT)))
                            .param("p1", ColonyPage.buildingName(o.buildingName()))
                            .param("p2", String.valueOf(o.targetLevel())));
            // MC's assignee label: the claiming builder's name.
            if (o.builderName().isPresent()) {
                ui.set(row + " #Info.Text", o.builderName().get());
            } else {
                ui.set(row + " #Info.Text", Message.translation("hycolony.ui.building.noBuilder"));
            }
            buttons(ui, events, row, i);
        }
    }

    private void buttons(UICommandBuilder ui, UIEventBuilder events, String row, int i) {
        String up = row + " #UpButton";
        String down = row + " #DownButton";
        String delete = row + " #DeleteButton";
        if (shown(ui, up, view.canManage() && i > 0)) {
            ColonyPage.bind(events, up, "up", i);
        }
        if (shown(ui, down, view.canManage() && i < view.orders().size() - 1)) {
            ColonyPage.bind(events, down, "down", i);
        }
        if (shown(ui, delete, view.canManage())) {
            ColonyPage.bind(events, delete, "delete", i);
        }
    }

    /** Hides {@code button} unless {@code shown}; returns {@code shown}. */
    private static boolean shown(UICommandBuilder ui, String button, boolean shown) {
        if (!shown) {
            ui.set(button + ".Visible", false);
        }
        return shown;
    }

    /** The core checks MANAGE_HUTS, then shows the town hall again. */
    void handle(ColonyPage.Act act) {
        if (act.index() < 0 || act.index() >= view.orders().size()) {
            return;
        }
        int id = view.orders().get(act.index()).id();
        switch (act.action()) {
            case "up" -> manager.workOrders().move(player, colonyId, id, 1);
            case "down" -> manager.workOrders().move(player, colonyId, id, -1);
            case "delete" -> manager.workOrders().delete(player, colonyId, id);
            default -> {}
        }
    }
}
