package dev.hycolony.core.job;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.Skill;
import dev.hycolony.core.citizen.Skills;
import dev.hycolony.core.colony.Colony;
import java.util.Optional;

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

    /** The levels of {@code c}'s work building ({@code workLevel}) and of its home in {@code colony}. */
    public static Levels levels(Colony colony, CitizenData c, int workLevel) {
        int home = Optional.ofNullable(c.homeBuilding())
                .flatMap(colony.buildings()::at)
                .map(Building::level)
                .orElse(0);
        return new Levels(workLevel, home);
    }

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
        Skill complementary = skill.complementary();
        if (complementary != null) {
            skills.addXp(complementary, dependencyXp, homeLevel, Skills.MAX_BUILDING_LEVEL);
        }
        Skill adverse = skill.adverse();
        if (adverse != null) {
            skills.removeXp(adverse, dependencyXp);
        }
    }
}
