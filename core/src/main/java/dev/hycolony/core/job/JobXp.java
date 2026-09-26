package dev.hycolony.core.job;

import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.Skill;
import dev.hycolony.core.citizen.Skills;

/** Port of MineColonies' CitizenExperienceHandler.addExperience skill split. */
public final class JobXp {
    /** Share of the job's XP given to its primary skill. */
    public static final double PRIMARY_SHARE = 1.0;
    /** Share of the job's XP given to its secondary skill (MC: {@code localXp / 2.0}). */
    public static final double SECONDARY_SHARE = 0.5;
    /** Share of the job's XP added to the primary's complementary skill and taken from its adverse one. */
    public static final double PRIMARY_DEPENDENCY_SHARE = 0.10;
    /** Share of the job's XP added to the secondary's complementary skill and taken from its adverse one. */
    public static final double SECONDARY_DEPENDENCY_SHARE = 0.05;

    /** The levels of the citizen's work building and home building (0 without one). */
    public record Levels(int work, int home) {}

    private JobXp() {}

    public static void award(CitizenData c, Skill primary, Skill secondary, double xp, Levels levels) {
        if (c.saturation() <= 0) {
            return;
        }
        int intelligence = c.skills().level(Skill.Intelligence);
        double localXp = xp * (1 + (levels.work() + levels.home()) / 10.0) * (1 + intelligence / 100.0);
        addWithDependencies(
                c.skills(), primary, localXp * PRIMARY_SHARE, localXp * PRIMARY_DEPENDENCY_SHARE, levels.home());
        addWithDependencies(
                c.skills(), secondary, localXp * SECONDARY_SHARE, localXp * SECONDARY_DEPENDENCY_SHARE, levels.home());
    }

    /** {@code xp} to the skill, {@code dependencyXp} to its complementary, and {@code dependencyXp} off its adverse. */
    private static void addWithDependencies(Skills skills, Skill skill, double xp, double dependencyXp, int homeLevel) {
        skills.addXp(skill, xp, homeLevel, Skills.MAX_BUILDING_LEVEL);
        if (skill.complementary() != null) {
            skills.addXp(skill.complementary(), dependencyXp, homeLevel, Skills.MAX_BUILDING_LEVEL);
        }
        if (skill.adverse() != null) {
            skills.removeXp(skill.adverse(), dependencyXp);
        }
    }
}
