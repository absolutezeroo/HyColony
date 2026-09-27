package dev.hycolony.core.colony.view;

import dev.hycolony.core.citizen.Skill;
import dev.hycolony.core.colony.ui.CitizenView.JobSkills;
import dev.hycolony.core.colony.ui.CitizenView.SkillShare;
import dev.hycolony.core.job.JobXp;
import java.util.ArrayList;
import java.util.List;

/** The citizen Job tab's skill lines (MC CitizenWindowUtils.updateJobPage), with the shares JobXp.award applies. */
final class JobSkillShares {
    private JobSkillShares() {}

    static JobSkills of(Skill primary, Skill secondary) {
        return new JobSkills(
                lines(primary, JobXp.PRIMARY_SHARE, JobXp.PRIMARY_DEPENDENCY_SHARE),
                lines(secondary, JobXp.SECONDARY_SHARE, JobXp.SECONDARY_DEPENDENCY_SHARE));
    }

    private static List<SkillShare> lines(Skill skill, double share, double dependencyShare) {
        List<SkillShare> lines = new ArrayList<>(3);
        lines.add(new SkillShare(skill, percent(share)));
        // MC shows the dependency lines only when the skill has both (every skill but Intelligence).
        Skill complementary = skill.complementary();
        Skill adverse = skill.adverse();
        if (complementary != null && adverse != null) {
            lines.add(new SkillShare(complementary, percent(dependencyShare)));
            lines.add(new SkillShare(adverse, -percent(dependencyShare)));
        }
        return lines;
    }

    private static int percent(double share) {
        return (int) Math.round(share * 100);
    }
}
