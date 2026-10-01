package dev.hycolony.plugin.ui;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.app.ui.BuildingView;
import dev.hycolony.plugin.ui.logistics.PickupPanel;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * The hut window's Main tab (MC AbstractBuildingMainWindow): level, state, work order, the single build button, hiring
 * and workers, pickup priority, storage, inventory summary; and its inventory summary sub-view.
 */
final class BuildingMainTab {
    private final ColonyManager manager;
    private final UUID player;
    private final BuildingView view;
    private final PickupPanel pickup;
    private final HutStockPanel stock;

    BuildingMainTab(ColonyManager manager, UUID player, BuildingView view) {
        this.manager = manager;
        this.player = player;
        this.view = view;
        this.pickup = new PickupPanel(manager, player, view);
        this.stock = new HutStockPanel(view.stock());
    }

    /** Takes over {@code previous}'s local state (the inventory summary sub-view), for a refresh. */
    void keepStateOf(BuildingMainTab previous) {
        stock.keepStateOf(previous.stock);
    }

    /** Fills the tab; {@code canOpenStorage} shows the Storage button. */
    void render(UICommandBuilder ui, UIEventBuilder events, boolean canOpenStorage) {
        ui.set("#TypeName.Text", ColonyPage.buildingName(view.typeId()));
        ui.set(
                "#Level.Text",
                Message.translation("hycolony.ui.building.level")
                        .param("p0", String.valueOf(view.level()))
                        .param("p1", String.valueOf(view.maxLevel())));
        String state = view.deconstructed() ? "deconstructed" : view.built() ? "built" : "notBuilt";
        ui.set("#State.Text", Message.translation("hycolony.ui.building.state." + state));
        orderInfo(ui);
        ui.set("#MainActions.Visible", !stock.isOpen());
        ui.set("#StockView.Visible", stock.isOpen());
        stock.render(ui, events);
        buildButton(ui, events);
        staff(ui, events);
        pickup.render(ui, events);
        if (canOpenStorage) {
            ColonyPage.bind(events, "#StorageButton", "storage");
        } else {
            ui.set("#StorageButton.Visible", false);
        }
    }

    private void orderInfo(UICommandBuilder ui) {
        if (view.order().isEmpty()) {
            ui.set("#OrderInfo.Text", Message.translation("hycolony.ui.building.noOrder"));
            return;
        }
        BuildingView.OrderRow o = view.order().get();
        ui.set(
                "#OrderInfo.TextSpans",
                Message.translation("hycolony.ui.building.order")
                        .param("p0", ColonyPage.workOrderTypeName(o.type()))
                        .param("p1", String.valueOf(o.targetLevel()))
                        .param(
                                "p2",
                                o.builderName()
                                        .map(Message::raw)
                                        .orElse(Message.translation("hycolony.ui.building.noBuilder")))
                        .param("p3", String.valueOf(o.percent())));
    }

    /**
     * MC updateButtonBuild: "Build options" opens the build options window; with an order it becomes "Cancel build /
     * upgrade / repair / deconstruction" (a full key per variant) and cancels it (MANAGE_HUTS, else disabled).
     */
    private void buildButton(UICommandBuilder ui, UIEventBuilder events) {
        if (view.order().isEmpty()) {
            ui.set("#BuildButton.Text", Message.translation("hycolony.ui.building.buildOptions"));
            ColonyPage.bind(events, "#BuildButton", "build");
            return;
        }
        String type = view.order().get().type().name().toLowerCase(Locale.ROOT);
        ui.set("#BuildButton.Text", Message.translation("hycolony.ui.building.cancel." + type));
        if (view.canManage()) {
            ColonyPage.bind(events, "#BuildButton", "cancel");
        } else {
            ui.set("#BuildButton.Disabled", true);
        }
    }

    private void staff(UICommandBuilder ui, UIEventBuilder events) {
        if (view.hiringMode().isEmpty()) {
            ui.set("#HiringButton.Visible", false);
            ui.set("#WorkerSection.Visible", false);
            ui.set("#HireSection.Visible", false);
            return;
        }
        // A button's Text renders no nested message: one full key per mode.
        ui.set(
                "#HiringButton.Text",
                Message.translation("hycolony.ui.building.hiring."
                        + view.hiringMode().get().name().toLowerCase(Locale.ROOT)));
        if (view.canManage()) {
            ColonyPage.bind(events, "#HiringButton", "hiring");
        } else {
            ui.set("#HiringButton.Disabled", true);
        }
        rows(ui, events, "#Workers", view.workers(), "fire");
        if (view.canManage()) {
            rows(ui, events, "#Hireable", view.hireable(), "hire");
        } else {
            ui.set("#HireSection.Visible", false);
        }
    }

    private void rows(
            UICommandBuilder ui, UIEventBuilder events, String list, List<BuildingView.WorkerRow> rows, String action) {
        for (int i = 0; i < rows.size(); i++) {
            String row = list + "[" + i + "]";
            ui.append(list, "Pages/HyColony/WorkerRow.ui");
            ui.set(row + " #Name.Text", rows.get(i).name());
            ui.set(row + " #Home.Text", homeLine(rows.get(i)));
            if (view.canManage()) {
                ui.set(row + " #Button.Text", Message.translation("hycolony.ui.building." + action));
                ColonyPage.bind(events, row + " #Button", action, i);
            } else {
                ui.set(row + " #Button.Visible", false);
            }
        }
    }

    /** MC WindowHireWorker's distance label: homeless, lives here, lives at its workplace, or N blocks away. */
    private static Message homeLine(BuildingView.WorkerRow row) {
        return switch (row.home()) {
            case HOMELESS -> Message.translation("hycolony.ui.hiring.homeless");
            case LIVES_HERE -> Message.translation("hycolony.ui.hiring.livesHere");
            case LIVES_AT_WORK -> Message.translation("hycolony.ui.hiring.livesAtWork");
            case DISTANCE ->
                Message.translation("hycolony.ui.hiring.distance").param("p0", String.valueOf(row.homeDistance()));
        };
    }

    /** The citizen of row {@code i} of {@code rows}; empty for an index out of the list. */
    private static Optional<Integer> row(List<BuildingView.WorkerRow> rows, int i) {
        return i >= 0 && i < rows.size() ? Optional.of(rows.get(i).citizenId()) : Optional.empty();
    }

    /** Answers this tab's buttons; returns true if the page must be drawn again (local state changed). */
    boolean handle(ColonyPage.Act act) {
        int i = act.index;
        switch (act.action) {
            case "cancel" -> manager.workOrders().cancel(player, view.pos());
            case "build" -> manager.windows().openBuildOptions(player, view.pos());
            case "hiring" -> view.hiringMode().ifPresent(m -> manager.huts().setHiring(player, view.pos(), m.next()));
            case "fire" -> row(view.workers(), i).ifPresent(id -> manager.huts().fire(player, view.pos(), id));
            case "hire" ->
                row(view.hireable(), i).ifPresent(id -> manager.huts().hire(player, view.pos(), id));
            default -> {
                return !pickup.handle(act) && stock.handle(act);
            }
        }
        return false;
    }
}
