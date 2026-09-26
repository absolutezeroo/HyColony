package dev.hycolony.plugin.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.core.colony.ColonyManager;
import dev.hycolony.core.colony.ui.WorkOrdersView;
import dev.hycolony.core.colony.ui.WorkOrdersView.OrderLine;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nonnull;

/** The colony's work orders in execution order; managers can reorder and delete them. */
public final class WorkOrdersPage extends ColonyPage {
    private final WorkOrdersView view;

    public WorkOrdersPage(PlayerRef playerRef, WorkOrdersView view, ColonyManager manager) {
        super(playerRef, manager);
        this.view = view;
    }

    @Override
    public void build(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder ui,
            @Nonnull UIEventBuilder events,
            @Nonnull Store<EntityStore> store) {
        ui.append("Pages/HyColony/WorkOrders.ui");
        List<OrderLine> orders = view.orders();
        if (orders.isEmpty()) {
            ui.set("#Empty.Visible", true);
            ui.set("#Empty.Text", Message.translation("hycolony.ui.workorders.empty"));
        }
        for (int i = 0; i < orders.size(); i++) {
            OrderLine o = orders.get(i);
            String row = "#Orders[" + i + "]";
            ui.append("#Orders", "Pages/HyColony/OrderRow.ui");
            ui.set(
                    row + " #Title.TextSpans",
                    Message.translation("hycolony.ui.workorders.line")
                            .param(
                                    "p0",
                                    Message.translation("hycolony.ui.workorder.type."
                                            + o.type().name().toLowerCase(Locale.ROOT)))
                            .param("p1", buildingName(o.buildingName()))
                            .param("p2", String.valueOf(o.targetLevel())));
            ui.set(
                    row + " #Info.TextSpans",
                    Message.translation("hycolony.ui.workorders.info")
                            .param("p0", String.valueOf(o.priority()))
                            .param(
                                    "p1",
                                    o.builderName()
                                            .map(Message::raw)
                                            .orElse(Message.translation("hycolony.ui.building.noBuilder"))));
            if (view.canManage()) {
                bind(events, row + " #UpButton", "up", i);
                bind(events, row + " #DownButton", "down", i);
                bind(events, row + " #DeleteButton", "delete", i);
            } else {
                ui.set(row + " #UpButton.Visible", false);
                ui.set(row + " #DownButton.Visible", false);
                ui.set(row + " #DeleteButton.Visible", false);
            }
        }
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull Act act) {
        if (act.index < 0 || act.index >= view.orders().size()) {
            return;
        }
        int id = view.orders().get(act.index).id();
        switch (act.action) {
            case "up" -> manager.workOrders().move(player, view.colonyId(), id, 1);
            case "down" -> manager.workOrders().move(player, view.colonyId(), id, -1);
            case "delete" -> manager.workOrders().delete(player, view.colonyId(), id);
            default -> {}
        }
    }
}
