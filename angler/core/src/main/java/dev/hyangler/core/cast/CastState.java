package dev.hyangler.core.cast;

/** Where a cast stands (spec § 7.3). */
public enum CastState {
    FLYING,
    FLOATING,
    APPROACH,
    BITING,
    GROUNDED,
    ENDED
}
