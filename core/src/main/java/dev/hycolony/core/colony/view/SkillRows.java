package dev.hycolony.core.colony.view;

import dev.hycolony.core.citizen.Experience;
import dev.hycolony.core.citizen.Skill;
import dev.hycolony.core.citizen.Skills;
import dev.hycolony.core.colony.ui.CitizenView.SkillRow;
import java.util.ArrayList;
import java.util.List;

/** The citizen window's skill list (MC WindowCitizen skills): job skills first, each with its XP toward next level. */
final class SkillRows {
    private SkillRows() {}

    /** {@code jobSkills}: the job's primary then secondary skill, or empty for a citizen without a workplace. */
    static List<SkillRow> of(Skills skills, List<Skill> jobSkills) {
        List<SkillRow> rows = new ArrayList<>(Skill.values().length);
        for (Skill s : jobSkills) {
            rows.add(row(skills, s, true));
        }
        for (Skill s : Skill.values()) {
            if (!jobSkills.contains(s)) {
                rows.add(row(skills, s, false));
            }
        }
        return rows;
    }

    private static SkillRow row(Skills skills, Skill s, boolean jobSkill) {
        int level = skills.level(s);
        if (level >= Skills.MAX_CITIZEN_LEVEL) {
            return new SkillRow(s, level, 0, 0, jobSkill);
        }
        int xp = (int) Math.floor(skills.experience(s));
        int needed = (int) Math.round(Experience.xpNeededForNextLevel(level));
        return new SkillRow(s, level, xp, needed, jobSkill);
    }
}
