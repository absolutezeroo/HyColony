package dev.hycolony.core.colony.view;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.hycolony.core.citizen.Skill;
import dev.hycolony.core.colony.ui.CitizenView.JobSkills;
import dev.hycolony.core.colony.ui.CitizenView.SkillShare;
import java.util.List;
import org.junit.jupiter.api.Test;

class JobSkillSharesTest {
    @Test
    void primaryAndSecondaryCarryTheirComplementaryAndAdverseShares() {
        JobSkills j = JobSkillShares.of(Skill.Adaptability, Skill.Athletics);

        assertEquals(
                List.of(
                        new SkillShare(Skill.Adaptability, 100),
                        new SkillShare(Skill.Creativity, 10),
                        new SkillShare(Skill.Focus, -10)),
                j.primary());
        assertEquals(
                List.of(
                        new SkillShare(Skill.Athletics, 50),
                        new SkillShare(Skill.Strength, 5),
                        new SkillShare(Skill.Dexterity, -5)),
                j.secondary());
    }

    @Test
    void intelligenceHasNoComplementaryNorAdverseLine() {
        JobSkills j = JobSkillShares.of(Skill.Intelligence, Skill.Knowledge);

        assertEquals(List.of(new SkillShare(Skill.Intelligence, 100)), j.primary());
        assertEquals(3, j.secondary().size());
    }
}
