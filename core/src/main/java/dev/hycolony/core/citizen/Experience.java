package dev.hycolony.core.citizen;

/** Port of MineColonies' ExperienceUtils. */
public final class Experience {
    private static final double EXPERIENCE_MULTIPLIER = 1D;

    private Experience() {}

    public static double xpNeededForNextLevel(int currentLevel) {
        if (currentLevel <= 0) {
            return 1;
        }
        return Math.max(1, 1 + EXPERIENCE_MULTIPLIER * 5 * currentLevel
                + 0.005 * ((double) currentLevel * currentLevel * currentLevel));
    }
}
