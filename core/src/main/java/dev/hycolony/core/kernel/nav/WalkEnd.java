package dev.hycolony.core.kernel.nav;

/** How a walk ended. */
public enum WalkEnd {
    /** The body got close enough without waiting for the nav. */
    CLOSE,
    /** The nav ended, and where it left the body counts as arrived. */
    NAV_ENDED,
    /** The stuck handler teleported the body, which then got close enough. */
    TELEPORTED,
    /** The stuck handler gave up: the body works from where it stands. */
    GAVE_UP
}
