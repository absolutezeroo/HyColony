package dev.hycolony.plugin.ui.citizen;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import dev.hycolony.core.colony.ui.CitizenView;
import dev.hycolony.core.colony.ui.CitizenView.SkillRow;
import dev.hycolony.plugin.adapter.HytaleNotifier;
import dev.hycolony.plugin.ui.ColonyPage;
import dev.hycolony.plugin.ui.RequestsPage;
import java.util.List;

/**
 * The citizen's Main tab (MC MainWindowCitizen): name, then the skills.
 *
 * <p>Deviation from MC: the job, workplace and activity lines, the XP bars and the job's skills first are kept at the
 * user's request (see SkillRows).
 */
final class CitizenMainTab {
    private CitizenMainTab() {}

    static void render(UICommandBuilder ui, CitizenView view, SkillRowRenderer skillRows) {
        Message none = Message.translation("hycolony.ui.citizen.none");
        ui.set("#Name.Text", view.name());
        ui.set(
                "#Job.TextSpans",
                Message.translation("hycolony.ui.citizen.job")
                        .param("p0", view.jobId().map(ColonyPage::jobName).orElse(none)));
        ui.set(
                "#Workplace.TextSpans",
                Message.translation("hycolony.ui.citizen.workplace")
                        .param(
                                "p0",
                                view.workBuilding()
                                        .map(ColonyPage::buildingName)
                                        .orElse(none)));
        if (view.waitingFor().isPresent()) {
            ui.set(
                    "#Activity.TextSpans",
                    Message.translation("hycolony.ui.citizen.waitingFor")
                            .param("p0", RequestsPage.describe(view.waitingFor().get())));
        } else if (view.jobActivity().isPresent()) {
            ui.set(
                    "#Activity.TextSpans",
                    HytaleNotifier.toMessage(view.jobActivity().get()));
        } else {
            ui.set("#Activity.Text", Message.translation("hycolony.status." + view.activity()));
        }
        List<SkillRow> skills = view.skills();
        for (int i = 0; i < skills.size(); i++) {
            skillRows.append(ui, "#Skills", i, skills.get(i));
        }
    }
}
