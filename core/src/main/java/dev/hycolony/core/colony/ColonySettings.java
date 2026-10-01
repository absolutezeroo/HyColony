package dev.hycolony.core.colony;

/** Colony-wide choices: the town hall's switches (auto-hiring, auto-housing, move-in) and the colony's style. */
public final class ColonySettings {
    /** The town hall's Settings tab switches (MC BuildingTownHall's BoolSettings), in MC's tab order. */
    public enum Toggle {
        MOVE_IN,
        AUTO_HIRING,
        AUTO_HOUSING
    }

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

    /** The value of {@code toggle}. */
    public boolean get(Toggle toggle) {
        return switch (toggle) {
            case MOVE_IN -> moveIn;
            case AUTO_HIRING -> autoHiring;
            case AUTO_HOUSING -> autoHousing;
        };
    }

    /** MC BoolSetting.trigger: turns {@code toggle} over. */
    public void flip(Toggle toggle) {
        switch (toggle) {
            case MOVE_IN -> moveIn = !moveIn;
            case AUTO_HIRING -> autoHiring = !autoHiring;
            case AUTO_HOUSING -> autoHousing = !autoHousing;
        }
    }

    /** The colony's structure pack (MC IColony.getStructurePack): the style new huts take; "" for the first one. */
    public String style() {
        return style;
    }

    public void setStyle(String style) {
        this.style = style;
    }
}
