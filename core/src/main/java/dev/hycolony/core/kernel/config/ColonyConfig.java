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

    public static ColonyConfig defaults() {
        return new ColonyConfig(4, 250, 4, 8, 20, true, 5);
    }
}
