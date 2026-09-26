package dev.hycolony.core.request;

/** MineColonies RequestState, same order: ordinals are compared (and were persisted by MineColonies). */
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
    FAILED
}
