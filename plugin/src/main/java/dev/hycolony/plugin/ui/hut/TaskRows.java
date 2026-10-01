package dev.hycolony.plugin.ui.hut;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.logistics.warehouse.TaskRow;
import dev.hycolony.core.request.model.Crafting;
import dev.hycolony.core.request.model.Delivery;
import dev.hycolony.core.request.model.Pickup;
import dev.hycolony.plugin.ui.ColonyPage;
import dev.hycolony.plugin.ui.RequestsPage;
import dev.hycolony.plugin.ui.request.StackTasks;
import java.util.List;
import java.util.Optional;

/**
 * A task list (MC WindowHutRequestTaskModule.updateElement), for the farmer's, the courier's and the warehouse's Tasks
 * pages: the request's icon, its short detail (a stack task: its prefix then the item and count), dark green while in
 * progress, the courier priority, and "requester -> parent" with both places as tooltip.
 */
final class TaskRows {
    /** MC ChatFormatting.DARK_GREEN, a task in progress. */
    private static final String IN_PROGRESS = "#00aa00";

    private TaskRows() {}

    /** Fills {@code list} with {@code tasks}, or shows {@code empty} when there are none. */
    static void render(UICommandBuilder ui, String list, String empty, List<TaskRow> tasks) {
        ui.set(empty + ".Visible", tasks.isEmpty());
        for (int i = 0; i < tasks.size(); i++) {
            TaskRow t = tasks.get(i);
            String row = list + "[" + i + "]";
            ui.append(list, "Pages/HyColony/Mc/TaskLine.ui");
            icon(ui, row, t);
            detail(ui, row, t);
            if (t.inProgress()) {
                ui.set(row + " #Detail.Style.TextColor", IN_PROGRESS);
            }
            // MC: a priority only for a courier's request (IDeliverymanRequestable).
            if (t.requestable() instanceof Delivery || t.requestable() instanceof Pickup) {
                ui.set(
                        row + " #Priority.Text",
                        Message.translation("hycolony.ui.tasks.priority").param("p0", String.valueOf(t.priority())));
            }
            requester(ui, row, t);
        }
    }

    /** MC getDisplayIcon: a delivery's or pickup's, a crafting's, none for any other request. */
    private static void icon(UICommandBuilder ui, String row, TaskRow t) {
        switch (t.requestable()) {
            case Delivery _, Pickup _ -> ui.set(row + " #Delivery.Visible", true);
            case Crafting _ -> ui.set(row + " #Crafting.Visible", true);
            default -> {}
        }
    }

    /**
     * MC IStackBasedTask: the prefix and the item with its count; any other task its short description. Deviation from
     * MC: the count is a label beside the icon (a Hytale ItemIcon draws none).
     */
    private static void detail(UICommandBuilder ui, String row, TaskRow t) {
        Optional<ItemAmount> stack = StackTasks.stack(t.requestable());
        if (stack.isEmpty()) {
            ui.set(row + " #Detail.TextSpans", RequestsPage.describe(t.requestable()));
            return;
        }
        ui.set(row + " #Detail.Text", StackTasks.prefix(t.requestable()));
        ui.set(row + " #DetailIcon.Visible", true);
        ui.set(row + " #DetailIcon.ItemId", stack.get().item().id());
        ui.set(row + " #DetailCount.Visible", true);
        ui.set(row + " #DetailCount.Text", "x" + stack.get().count());
    }

    /** MC: "requester -> parent requester" with "x, y, z -> x, y, z" as tooltip, or the requester alone. */
    private static void requester(UICommandBuilder ui, String row, TaskRow t) {
        Message requester = ColonyPage.buildingName(t.requester());
        if (t.forRequester().isEmpty()) {
            ui.set(row + " #Requester.TextSpans", requester);
            return;
        }
        ui.set(
                row + " #Requester.TextSpans",
                Message.translation("hycolony.ui.tasks.requesterFor")
                        .param("p0", requester)
                        .param("p1", ColonyPage.buildingName(t.forRequester().get())));
        if (t.requesterPos().isPresent() && t.forPos().isPresent()) {
            ui.set(
                    row + " #Requester.TooltipText",
                    place(t.requesterPos().get()) + " -> " + place(t.forPos().get()));
        }
    }

    /** MC BlockPos.toShortString. */
    private static String place(BlockPos p) {
        return p.x() + ", " + p.y() + ", " + p.z();
    }
}
