package dev.hycolony.core.kernel.nav;

/** How a walk ended; a body already close before walking ends no walk (MC: no path job started). */
public enum WalkEnd {
    /** A plain walk's nav ended: where it left the body counts as arrived. */
    NAV_ENDED,
    /** A walk close to a block: its nav ended within the walk's reach of that block (MC walkCloseToXNearY). */
    IN_REACH,
    /** The stuck handler teleported the body during the walk, whose nav then ended where the walk accepts it. */
    TELEPORTED,
    /** The stuck handler gave up: the body works from where it stands. */
    GAVE_UP
}
