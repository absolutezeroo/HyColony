package dev.hycolony.core.request.model;

/** MineColonies RequestState, same order: MC compares ordinals (and persisted them). */
public enum RequestState {
    CREATED,
    REPORTED,
    ASSIGNING,
    ASSIGNED,
    IN_PROGRESS,
    RESOLVED,
    FOLLOWUP_IN_PROGRESS,
    COMPLETED,
    OVERRULED,
    CANCELLED,
    RECEIVED,
    FINALIZING,
    FAILED;

    /** Whether this state comes before {@code other}: MC's {@code state.ordinal() < other.ordinal()}. */
    public boolean isBefore(RequestState other) {
        return compareTo(other) < 0;
    }
}
