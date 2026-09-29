package dev.hycolony.plugin.ui.hut;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import dev.hycolony.core.logistics.warehouse.TaskRow;
import dev.hycolony.plugin.ui.ColonyPage;
import dev.hycolony.plugin.ui.RequestsPage;
import java.util.List;

/**
 * A courier task list (MC WindowHutRequestTaskModule): what is carried, "requester -> for whom", and the priority; the
 * task under way gets the green frame of the builder's current work order (MC draws it in green).
 */
final class TaskRows {
    private static final String IN_PROGRESS_FRAME = "#00aa00";

    private TaskRows() {}

    /** Fills {@code list}, or shows {@code empty} when there is no task. */
    static void render(UICommandBuilder ui, String list, String empty, List<TaskRow> tasks) {
        if (tasks.isEmpty()) {
            ui.set(empty + ".Visible", true);
            ui.set(empty + ".Text", Message.translation("hycolony.ui.tasks.empty"));
        }
        for (int i = 0; i < tasks.size(); i++) {
            TaskRow t = tasks.get(i);
            String row = list + "[" + i + "]";
            ui.append(list, "Pages/HyColony/TaskRow.ui");
            if (t.inProgress()) {
                ui.set(row + ".Background", IN_PROGRESS_FRAME);
            }
            ui.set(row + " #Title.TextSpans", RequestsPage.describe(t.requestable()));
            ui.set(row + " #Requester.TextSpans", requester(t));
            ui.set(
                    row + " #Priority.Text",
                    Message.translation("hycolony.ui.tasks.priority").param("p0", String.valueOf(t.priority())));
        }
    }

    /** "From Warehouse", or "Warehouse -> Builder's Hut" when the task serves a parent request elsewhere. */
    private static Message requester(TaskRow t) {
        Message requester = ColonyPage.buildingName(t.requester());
        return t.forRequester()
                .map(f -> Message.translation("hycolony.ui.tasks.requesterFor")
                        .param("p0", requester)
                        .param("p1", ColonyPage.buildingName(f)))
                .orElse(Message.translation("hycolony.ui.requests.from").param("p0", requester));
    }
}
