package dev.hycolony.core.job;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.Skill;
import org.junit.jupiter.api.Test;

class JobXpTest {
    @Test
    void jobXpSplitMatchesMineColonies() {
        CitizenData c = new CitizenData(1);
        // level term = 1 + (0+0)/10 = 1; intelligence term = 1 + 1/100 = 1.01; localXp = 10 * 1.01 = 10.1
        JobXp.award(c, Skill.Adaptability, Skill.Athletics, 10.0, 0, 0);

        assertEquals(2, c.skills().level(Skill.Adaptability)); // primary, 100%: 10.1 xp, levels 1->2
        assertEquals(4.095, c.skills().experience(Skill.Adaptability), 1e-9);

        assertEquals(1, c.skills().level(Skill.Creativity)); // primary's complementary, +10%
        assertEquals(1.01, c.skills().experience(Skill.Creativity), 1e-9);

        assertEquals(1, c.skills().level(Skill.Focus)); // primary's adverse, -10%
        assertEquals(-1.01, c.skills().experience(Skill.Focus), 1e-9);

        assertEquals(1, c.skills().level(Skill.Athletics)); // secondary, 50%
        assertEquals(5.05, c.skills().experience(Skill.Athletics), 1e-9);

        assertEquals(1, c.skills().level(Skill.Strength)); // secondary's complementary, +5%
        assertEquals(0.505, c.skills().experience(Skill.Strength), 1e-9);

        assertEquals(1, c.skills().level(Skill.Dexterity)); // secondary's adverse, -5%
        assertEquals(-0.505, c.skills().experience(Skill.Dexterity), 1e-9);

        // untouched skill stays at its default
        assertEquals(1, c.skills().level(Skill.Mana));
        assertEquals(0.0, c.skills().experience(Skill.Mana), 1e-9);
    }

    @Test
    void noXpWhenStarving() {
        CitizenData c = new CitizenData(1);
        c.setSaturation(0);
        JobXp.award(c, Skill.Adaptability, Skill.Athletics, 100.0, 5, 5);
        for (Skill s : Skill.values()) {
            assertEquals(1, c.skills().level(s), s.toString());
            assertEquals(0.0, c.skills().experience(s), 1e-9, s.toString());
        }
    }
}
