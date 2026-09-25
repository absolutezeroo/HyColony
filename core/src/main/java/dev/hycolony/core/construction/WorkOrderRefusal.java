package dev.hycolony.core.construction;

/** Why a work order was not created, in the order the checks run. */
public enum WorkOrderRefusal {
    NO_PERMISSION, ALREADY_EXISTS, MAX_LEVEL, NOT_BUILT, INVALID_TYPE, BUILDER_NECESSARY, BUILDER_TOO_FAR_AWAY, NO_BLUEPRINT, OUT_OF_COLONY
}
