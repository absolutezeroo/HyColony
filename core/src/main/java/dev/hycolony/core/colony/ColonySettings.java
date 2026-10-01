package dev.hycolony.core.colony;

/** Colony-wide choices: the town hall's switches (auto-hiring, auto-housing, move-in) and the colony's style. */
public final class ColonySettings {
    private boolean autoHiring = true;
    private boolean autoHousing = true;
    private boolean moveIn = true;
    private String style = "";

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

    /** MC BuildingTownHall.MOVE_IN (default true): new citizens move into the colony. */
    public boolean moveIn() {
        return moveIn;
    }

    public void setMoveIn(boolean moveIn) {
        this.moveIn = moveIn;
    }

    /** The colony's structure pack (MC IColony.getStructurePack): the style new huts take; "" for the first one. */
    public String style() {
        return style;
    }

    public void setStyle(String style) {
        this.style = style;
    }
}
