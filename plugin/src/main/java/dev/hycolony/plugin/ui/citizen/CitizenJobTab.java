package dev.hycolony.plugin.ui.citizen;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import dev.hycolony.core.colony.ui.CitizenView.JobSkills;
import dev.hycolony.core.colony.ui.CitizenView.SkillShare;
import dev.hycolony.plugin.IdMap;
import dev.hycolony.plugin.ui.ColonyPage;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The citizen's Job tab (MC job.xml, CitizenWindowUtils.updateJobPage): "Job: X", the explanation, then the primary
 * skill with its complementary and adverse skill, and the same for the secondary, each "icon skill (n% XP)".
 */
final class CitizenJobTab {
    private CitizenJobTab() {}

    static void render(UICommandBuilder ui, IdMap ids, Optional<String> jobId, JobSkills skills) {
        ui.set(
                "#JobLabel.TextSpans",
                Message.translation("hycolony.ui.citizen.job").param("p0", ColonyPage.jobName(jobId.orElse(""))));
        rows(ui, ids, "#JobPrimary", skills.primary());
        rows(ui, ids, "#JobSecondary", skills.secondary());
    }

    private static void rows(UICommandBuilder ui, IdMap ids, String list, List<SkillShare> shares) {
        for (int i = 0; i < shares.size(); i++) {
            SkillShare s = shares.get(i);
            String row = list + "[" + i + "]";
            ui.append(list, "Pages/HyColony/JobSkillRow.ui");
            ui.set(row + " #Icon.ItemId", ids.skillIcon(s.skill()));
            ui.set(
                    row + " #Name.TextSpans",
                    Message.translation("hycolony.ui.citizen.jobShare")
                            .param(
                                    "p0",
                                    Message.translation("hycolony.ui.skill."
                                            + s.skill().name().toLowerCase(Locale.ROOT)))
                            .param("p1", String.valueOf(s.xpPercent())));
        }
    }
}
