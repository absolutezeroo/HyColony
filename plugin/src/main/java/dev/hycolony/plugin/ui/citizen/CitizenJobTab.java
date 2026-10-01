package dev.hycolony.plugin.ui.citizen;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import dev.hycolony.core.app.ui.CitizenView.JobSkills;
import dev.hycolony.core.app.ui.CitizenView.SkillShare;
import dev.hycolony.plugin.ui.ColonyPage;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The citizen's Job page (MC job.xml, CitizenWindowUtils.updateJobPage): "Job: X", the explanation, then the primary
 * skill with its complementary and adverse skill, and the same for the secondary, each "icon Skill (n% XP)".
 */
final class CitizenJobTab {
    private static final String PAGE = "#JobPage";

    private CitizenJobTab() {}

    static void render(UICommandBuilder ui, Optional<String> jobId, JobSkills skills) {
        ui.set(
                PAGE + " #JobLabel.TextSpans",
                Message.translation("hycolony.ui.citizen.job").param("p0", ColonyPage.jobName(jobId.orElse(""))));
        lines(ui, PAGE + " #JobPrimary", skills.primary());
        lines(ui, PAGE + " #JobSecondary", skills.secondary());
    }

    private static void lines(UICommandBuilder ui, String list, List<SkillShare> shares) {
        for (int i = 0; i < shares.size(); i++) {
            SkillShare s = shares.get(i);
            String line = list + "[" + i + "]";
            String skill = s.skill().name();
            ui.append(list, "Pages/HyColony/Mc/JobSkillLine.ui");
            ui.set(line + " #Icon #Icons #" + skill + ".Visible", true);
            ui.set(
                    line + " #Name.TextSpans",
                    Message.translation("hycolony.ui.citizen.jobShare")
                            .param("p0", Message.translation("hycolony.ui.skill." + skill.toLowerCase(Locale.ROOT)))
                            .param("p1", String.valueOf(s.xpPercent())));
        }
    }
}
