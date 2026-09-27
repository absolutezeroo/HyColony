package dev.hycolony.plugin.ui;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import dev.hycolony.core.colony.ColonyManager;
import dev.hycolony.core.colony.ui.BuildingView;
import dev.hycolony.core.job.HiringMode;
import dev.hycolony.plugin.ui.logistics.PickupPanel;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The hut window's Main tab (MC AbstractBuildingMainWindow): level, state, work order, the single build button, hiring
 * and workers, pickup priority, storage, inventory summary; and its Build options and inventory summary sub-views.
 */
final class BuildingMainTab {
    private final ColonyManager manager;
    private final UUID player;
    private final BuildingView view;
    private final BuildOptionsPanel options;
    private final PickupPanel pickup;
    private final HutStockPanel stock;

    BuildingMainTab(ColonyManager manager, UUID player, BuildingView view) {
        this.manager = manager;
        this.player = player;
        this.view = view;
        this.options = new BuildOptionsPanel(manager, player, view);
        this.pickup = new PickupPanel(manager, player, view);
        this.stock = new HutStockPanel(view.stock());
    }

    /** Takes over {@code previous}'s local state (the Build options and inventory summary sub-views), for a refresh. */
    void keepStateOf(BuildingMainTab previous) {
        options.keepStateOf(previous.options);
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
        ui.set("#MainActions.Visible", !options.isOpen() && !stock.isOpen());
        ui.set("#BuildOptions.Visible", options.isOpen());
        ui.set("#StockView.Visible", stock.isOpen());
        if (options.isOpen()) {
            options.render(ui, events);
        }
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
     * MC updateButtonBuild: "Build options" opens the sub-view; with an order it becomes "Cancel build / upgrade /
     * repair / deconstruction" (a full key per variant) and cancels it (MANAGE_HUTS, else disabled).
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
            if (view.canManage()) {
                ui.set(row + " #Button.Text", Message.translation("hycolony.ui.building." + action));
                ColonyPage.bind(events, row + " #Button", action, i);
            } else {
                ui.set(row + " #Button.Visible", false);
            }
        }
    }

    /** Answers this tab's buttons; returns true if the page must be drawn again (local state changed). */
    boolean handle(ColonyPage.Act act) {
        int i = act.index;
        switch (act.action) {
            case "cancel" -> manager.workOrders().cancel(player, view.pos());
            case "hiring" ->
                view.hiringMode()
                        .ifPresent(m -> manager.huts()
                                .setHiring(
                                        player,
                                        view.pos(),
                                        HiringMode.values()[(m.ordinal() + 1) % HiringMode.values().length]));
            case "fire" -> {
                if (i >= 0 && i < view.workers().size()) {
                    manager.huts()
                            .fire(player, view.pos(), view.workers().get(i).citizenId());
                }
            }
            case "hire" -> {
                if (i >= 0 && i < view.hireable().size()) {
                    manager.huts()
                            .hire(player, view.pos(), view.hireable().get(i).citizenId());
                }
            }
            default -> {
                return !pickup.handle(act) && (stock.handle(act) || options.handle(act));
            }
        }
        return false;
    }
}
