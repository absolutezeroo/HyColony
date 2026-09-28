package dev.hycolony.core.farming.field;

/**
 * Where a field is in its cycle (MC FarmField.Stage): EMPTY waits to be hoed, HOED to be planted, PLANTED to be
 * harvested. The farmer advances it by one after every pass, and a harvest brings it back to EMPTY.
 */
public enum FieldStage {
    EMPTY,
    HOED,
    PLANTED;

    /** MC nextState: the following stage, PLANTED wrapping to EMPTY. */
    public FieldStage next() {
        return values()[(ordinal() + 1) % values().length];
    }
}
