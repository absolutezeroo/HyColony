package dev.hycolony.core.kernel.config;

/** Server configuration relevant to colonies. Defaults mirror MineColonies. */
public record ColonyConfig(
        int initialCitizenAmount,
        int maxCitizenPerColony,
        int initialColonySize,
        int minColonyDistance,
        int maxColonySize,
        boolean enableColonyProtection,
        int autosaveIntervalMinutes) {

    public ColonyConfig {
        initialCitizenAmount = clamp(initialCitizenAmount, 1, 10);
        maxCitizenPerColony = clamp(maxCitizenPerColony, 25, 500);
        initialColonySize = clamp(initialColonySize, 1, 15);
        minColonyDistance = clamp(minColonyDistance, 1, 200);
        maxColonySize = clamp(maxColonySize, 1, 250);
        autosaveIntervalMinutes = clamp(autosaveIntervalMinutes, 1, 60);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public static ColonyConfig defaults() {
        return new ColonyConfig(4, 250, 4, 8, 20, true, 5);
    }
}
