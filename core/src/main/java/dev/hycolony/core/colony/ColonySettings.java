package dev.hycolony.core.colony;

/**
 * Colony-wide choices: the town hall's switches (auto-hiring, auto-housing, move-in, construction tape) and the
 * colony's style.
 */
public final class ColonySettings {
    /** The town hall's Settings tab switches (MC BuildingTownHall's BoolSettings), in MC's tab order. */
    public enum Toggle {
        MOVE_IN,
        AUTO_HIRING,
        AUTO_HOUSING,
        CONSTRUCTION_TAPE
    }

    private boolean autoHiring = true;
    private boolean autoHousing = true;
    private boolean moveIn = true;
    private boolean constructionTape = true;

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

    /** MC BuildingTownHall.CONSTRUCTION_TAPE (default true): tape is placed around building sites. */
    public boolean constructionTape() {
        return constructionTape;
    }

    public void setConstructionTape(boolean constructionTape) {
        this.constructionTape = constructionTape;
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
            case CONSTRUCTION_TAPE -> constructionTape;
        };
    }

    /** MC SettingsModule.updateSetting: sets {@code toggle} to {@code value}. */
    public void set(Toggle toggle, boolean value) {
        switch (toggle) {
            case MOVE_IN -> moveIn = value;
            case AUTO_HIRING -> autoHiring = value;
            case AUTO_HOUSING -> autoHousing = value;
            case CONSTRUCTION_TAPE -> constructionTape = value;
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
