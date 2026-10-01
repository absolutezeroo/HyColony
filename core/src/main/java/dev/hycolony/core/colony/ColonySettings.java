package dev.hycolony.core.colony;

/** Colony-wide toggles, e.g. the town hall's auto-hiring switch. */
public final class ColonySettings {
    private boolean autoHiring = true;
    private boolean autoHousing = true;

    public boolean autoHiring() {
        return autoHiring;
    }

    public void setAutoHiring(boolean autoHiring) {
        this.autoHiring = autoHiring;
    }

    /** MC BuildingTownHall.AUTO_HOUSING_MODE (default true): DEFAULT residences take the homeless. */
    public boolean autoHousing() {
        return autoHousing;
    }

    public void setAutoHousing(boolean autoHousing) {
        this.autoHousing = autoHousing;
    }
}
