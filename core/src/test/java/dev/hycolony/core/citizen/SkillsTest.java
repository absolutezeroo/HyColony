package dev.hycolony.core.citizen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;

class SkillsTest {
    @Test
    void xpCurveMatchesMineColonies() {
        assertEquals(1.0, Experience.xpNeededForNextLevel(0));
        assertEquals(6.005, Experience.xpNeededForNextLevel(1), 1e-9);
        assertEquals(56.0, Experience.xpNeededForNextLevel(10), 1e-9);
    }

    @Test
    void relationsMatchMineColonies() {
        assertEquals(Skill.Strength, Skill.Athletics.complementary());
        assertEquals(Skill.Dexterity, Skill.Athletics.adverse());
        assertEquals(Skill.Creativity, Skill.Adaptability.complementary());
        assertNull(Skill.Intelligence.complementary());
    }

    @Test
    void initRandomStaysWithinCap() {
        Skills s = Skills.initRandom(10, new Random(42));
        for (Skill skill : Skill.values()) {
            assertTrue(s.level(skill) >= 1 && s.level(skill) <= 9, skill + "=" + s.level(skill));
        }
        Skills low = Skills.initRandom(1, new Random(42));
        for (Skill skill : Skill.values()) {
            assertEquals(1, low.level(skill));
        }
    }

    @Test
    void addXpLevelsUpAndKeepsRemainder() {
        Skills s = Skills.initRandom(1, new Random(1)); // all level 1
        assertTrue(s.addXp(Skill.Focus, 6.005 + 2.0, 0, 5));
        assertEquals(2, s.level(Skill.Focus));
        assertEquals(2.0, s.experience(Skill.Focus), 1e-9);
    }

    @Test
    void homeLevelCapsSkillGrowth() {
        Skills s = Skills.initRandom(1, new Random(1));
        s.set(Skill.Focus, 10, 0);
        assertFalse(s.addXp(Skill.Focus, 1000, 0, 5)); // (0+1)*10 <= 10 -> blocked
        assertEquals(10, s.level(Skill.Focus));
        assertTrue(s.addXp(Skill.Focus, 60, 1, 5));  // home level 1 allows up to 20
    }

    @Test
    void maxLevelBlocksGrowth() {
        Skills s = Skills.initRandom(1, new Random(1));
        s.set(Skill.Mana, Skills.MAX_CITIZEN_LEVEL, 0);
        assertFalse(s.addXp(Skill.Mana, 1e9, 5, 5));
    }
}
