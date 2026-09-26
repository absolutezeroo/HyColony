package dev.hycolony.core.colony;

/** Colony-wide toggles, e.g. the town hall's auto-hiring switch. */
public final class ColonySettings {
    private boolean autoHiring = true;

    public boolean autoHiring() {
        return autoHiring;
    }

    public void setAutoHiring(boolean autoHiring) {
        this.autoHiring = autoHiring;
    }
}
