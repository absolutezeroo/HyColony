package dev.hycolony.core.kernel.nav;

/** How a walk ended. */
public enum WalkEnd {
    /** The body got close enough without waiting for the nav. */
    CLOSE,
    /** A plain walk's nav ended: where it left the body counts as arrived. */
    NAV_ENDED,
    /** A walk close to a block: its nav ended within the walk's reach of that block (MC walkCloseToXNearY). */
    IN_REACH,
    /** The stuck handler teleported the body, which then got close enough. */
    TELEPORTED,
    /** The stuck handler gave up: the body works from where it stands. */
    GAVE_UP
}
