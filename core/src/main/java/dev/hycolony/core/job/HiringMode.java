package dev.hycolony.core.job;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.colony.Colony;

/** How a building module takes citizens (MC {@code HiringMode}). */
public enum HiringMode {
    DEFAULT,
    AUTO,
    MANUAL,
    LOCKED;

    /** The mode after this one, back to the first after the last (MC's hiring mode button cycles them in order). */
    public HiringMode next() {
        HiringMode[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    /** MC WindowHireWorker.switchHiringMode: the next mode of a workplace, which skips LOCKED (homes only). */
    public HiringMode nextForWorkplace() {
        HiringMode next = next();
        return next == LOCKED ? LOCKED.next() : next;
    }

    /**
     * MC {@code AbstractBuilding.canAssignCitizens}: built and above level 0, or always for a building that allows
     * level 0 (MC {@code BuildingBuilder} overrides it to true).
     */
    public static boolean canAssignCitizens(Building building, boolean assignableAtLevel0) {
        return assignableAtLevel0 || (building.level() > 0 && building.isBuilt());
    }

    /**
     * MC {@code BuildingUtils.canAutoHire}: citizens can be assigned, and the mode is AUTO or DEFAULT while the colony
     * auto-hires. MANUAL and LOCKED never auto-hire.
     */
    public boolean canAutoHire(Colony colony, Building building, boolean assignableAtLevel0) {
        return canAssignCitizens(building, assignableAtLevel0)
                && (this == AUTO || (this == DEFAULT && colony.settings().autoHiring()));
    }
}
