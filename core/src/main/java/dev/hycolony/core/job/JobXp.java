package dev.hycolony.core.job;

import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.Skill;
import dev.hycolony.core.citizen.Skills;

/** Port of MineColonies' CitizenExperienceHandler.addExperience skill split. */
public final class JobXp {
    private static final double PRIMARY_DEPENDENCY_SHARE = 0.10;
    private static final double SECONDARY_DEPENDENCY_SHARE = 0.05;

    private JobXp() {}

    public static void award(CitizenData c, Skill primary, Skill secondary, double xp, int workLevel, int homeLevel) {
        if (c.saturation() <= 0) {
            return;
        }
        int intelligence = c.skills().level(Skill.Intelligence);
        double localXp = xp * (1 + (workLevel + homeLevel) / 10.0) * (1 + intelligence / 100.0);
        int homeMaxLevel = Skills.MAX_BUILDING_LEVEL;

        c.skills().addXp(primary, localXp, homeLevel, homeMaxLevel);
        if (primary.complementary() != null) {
            c.skills().addXp(primary.complementary(), localXp * PRIMARY_DEPENDENCY_SHARE, homeLevel, homeMaxLevel);
        }
        if (primary.adverse() != null) {
            c.skills().removeXp(primary.adverse(), localXp * PRIMARY_DEPENDENCY_SHARE);
        }

        c.skills().addXp(secondary, localXp / 2.0, homeLevel, homeMaxLevel);
        if (secondary.complementary() != null) {
            c.skills().addXp(secondary.complementary(), localXp * SECONDARY_DEPENDENCY_SHARE, homeLevel, homeMaxLevel);
        }
        if (secondary.adverse() != null) {
            c.skills().removeXp(secondary.adverse(), localXp * SECONDARY_DEPENDENCY_SHARE);
        }
    }
}
