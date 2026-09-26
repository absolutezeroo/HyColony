package dev.hycolony.plugin.ui;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import dev.hycolony.core.colony.ColonyManager;
import dev.hycolony.core.colony.ui.BuildingView;
import dev.hycolony.core.construction.workorder.WorkOrderType;
import java.util.Locale;
import java.util.UUID;

/**
 * The hut window's "Build options" sub-view (MC WindowBuildBuilding): style {@code <} {@code >}, then Repair,
 * Build/Upgrade, Deconstruct and Pick up as the core view allows them. Open/closed and the style shown are page state.
 */
final class BuildOptionsPanel {
    private final ColonyManager manager;
    private final UUID player;
    private final BuildingView view;
    private boolean open;
    /** Local choice sent with the next order; the core has no "set style" action. */
    private int styleIndex;

    BuildOptionsPanel(ColonyManager manager, UUID player, BuildingView view) {
        this.manager = manager;
        this.player = player;
        this.view = view;
        this.styleIndex = Math.max(0, view.styles().indexOf(view.style()));
    }

    boolean isOpen() {
        return open;
    }

    private String style() {
        return view.styles().isEmpty() ? view.style() : view.styles().get(styleIndex);
    }

    /**
     * WindowBuildBuilding's buttons: Build at level 0 or Upgrade below the max level (else hidden); Repair, labelled
     * Build once deconstructed; Deconstruct; Pick up. Each shows only if the core allows it.
     */
    void render(UICommandBuilder ui, UIEventBuilder events) {
        if (view.styles().size() < 2) {
            ui.set("#StyleRow.Visible", false);
        } else {
            ui.set("#StyleName.Text", style());
            ColonyPage.bind(events, "#StylePrevButton", "stylePrev");
            ColonyPage.bind(events, "#StyleNextButton", "styleNext");
        }
        WorkOrderType build =
                view.allowed().contains(WorkOrderType.BUILD) ? WorkOrderType.BUILD : WorkOrderType.UPGRADE;
        orderButton(ui, events, "#OptBuildButton", build, typeKey(build));
        orderButton(
                ui,
                events,
                "#OptRepairButton",
                WorkOrderType.REPAIR,
                view.deconstructed() ? typeKey(WorkOrderType.BUILD) : typeKey(WorkOrderType.REPAIR));
        orderButton(ui, events, "#OptRemoveButton", WorkOrderType.REMOVE, typeKey(WorkOrderType.REMOVE));
        if (view.canPickUp()) {
            ColonyPage.bind(events, "#OptPickUpButton", "pickUp");
        } else {
            ui.set("#OptPickUpButton.Visible", false);
        }
        ColonyPage.bind(events, "#OptBackButton", "back");
    }

    private static String typeKey(WorkOrderType type) {
        return "hycolony.ui.workorder.type." + type.name().toLowerCase(Locale.ROOT);
    }

    private void orderButton(
            UICommandBuilder ui, UIEventBuilder events, String button, WorkOrderType type, String key) {
        if (view.allowed().contains(type)) {
            ui.set(button + ".Text", Message.translation(key));
            ColonyPage.bind(
                    events, button, "order", type.ordinal()); // the core refuses (with a message) if not allowed
        } else {
            ui.set(button + ".Visible", false);
        }
    }

    /**
     * Answers this sub-view's buttons and "build" (opens it). Returns true if the page must be drawn again (local
     * state changed), false otherwise; an order re-shows the window through the core.
     */
    boolean handle(ColonyPage.Act act) {
        return switch (act.action) {
            case "build" -> show(true);
            case "back" -> show(false);
            case "stylePrev" -> cycleStyle(-1);
            case "styleNext" -> cycleStyle(1);
            case "order" -> order(act.index);
            default -> false;
        };
    }

    private boolean show(boolean shown) {
        open = shown;
        return true;
    }

    /** False for a hut with fewer than two styles: the buttons are hidden then, so the event is forged. */
    private boolean cycleStyle(int step) {
        int n = view.styles().size();
        if (n < 2) {
            return false;
        }
        styleIndex = Math.floorMod(styleIndex + step, n);
        return true;
    }

    /** A refusal is already sent to the player by the core (hycolony.workorder.refused.*). */
    private boolean order(int typeIndex) {
        if (typeIndex >= 0 && typeIndex < WorkOrderType.values().length) {
            manager.workOrders().order(player, view.pos(), WorkOrderType.values()[typeIndex], style());
        }
        return false;
    }
}
