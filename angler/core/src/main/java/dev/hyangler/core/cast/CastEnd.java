package dev.hyangler.core.cast;

/** How a cast ended, and the durability it costs the rod (vanilla FishingHook.retrieve: 1, 2 on the ground). */
public enum CastEnd {
    CAUGHT(1),
    ESCAPED(0),
    GROUNDED(2),
    BROKEN(0),
    CANCELLED(0);

    private final int wear;

    CastEnd(int wear) {
        this.wear = wear;
    }

    /** Durability the rod loses. */
    public int wear() {
        return wear;
    }
}
