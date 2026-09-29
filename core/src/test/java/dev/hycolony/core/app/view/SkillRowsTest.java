package dev.hycolony.core.app.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.app.ui.CitizenView.SkillRow;
import dev.hycolony.core.citizen.Experience;
import dev.hycolony.core.citizen.Skill;
import dev.hycolony.core.citizen.Skills;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class SkillRowsTest {
    @Test
    void rowCarriesLevelXpAndTheFormulaXpForTheNextLevel() {
        Skills skills = Skills.empty();
        skills.set(Skill.Strength, 5, 12.7);

        SkillRow row = SkillRows.of(skills, List.of()).get(Skill.Strength.ordinal());

        assertEquals(Skill.Strength, row.skill());
        assertEquals(5, row.level());
        assertEquals(12, row.xp(), "whole XP earned, rounded down");
        assertEquals(27, row.xpForNextLevel(), "1 + 5*5 + 0.005*125 = 26.625, rounded");
        assertEquals(Math.round(Experience.xpNeededForNextLevel(5)), row.xpForNextLevel());
        assertEquals(12f / 27f, row.progress(), 1e-6);
        assertFalse(row.jobSkill());
    }

    @Test
    void progressNeverOverflowsTheBar() {
        Skills skills = Skills.empty();
        skills.set(Skill.Mana, 1, 6.004); // needs 6.005: rounds to 6 / 6 but still below the level-up
        SkillRow row = SkillRows.of(skills, List.of()).get(Skill.Mana.ordinal());

        assertEquals(6, row.xp());
        assertEquals(6, row.xpForNextLevel());
        assertEquals(1f, row.progress(), 1e-6);
    }

    @Test
    void maxLevelShowsAFullBar() {
        Skills skills = Skills.empty();
        skills.set(Skill.Focus, Skills.MAX_CITIZEN_LEVEL, 0);

        SkillRow row = SkillRows.of(skills, List.of()).get(Skill.Focus.ordinal());

        assertTrue(row.maxed());
        assertEquals(1f, row.progress(), 1e-6);
    }

    @Test
    void withoutJobSkillsTheOrderIsMineColonies() {
        List<Skill> order = SkillRows.of(Skills.empty(), List.of()).stream()
                .map(SkillRow::skill)
                .toList();

        assertEquals(Arrays.asList(Skill.values()), order);
    }

    @Test
    void jobPrimaryThenSecondaryComeFirstAndAreHighlighted() {
        List<SkillRow> rows = SkillRows.of(Skills.empty(), List.of(Skill.Adaptability, Skill.Athletics));

        assertEquals(Skill.values().length, rows.size());
        assertEquals(Skill.Adaptability, rows.get(0).skill());
        assertEquals(Skill.Athletics, rows.get(1).skill());
        assertEquals(Skill.Dexterity, rows.get(2).skill(), "the rest keep MineColonies order");
        assertTrue(rows.get(0).jobSkill());
        assertTrue(rows.get(1).jobSkill());
        assertEquals(2, rows.stream().filter(SkillRow::jobSkill).count());
    }
}
