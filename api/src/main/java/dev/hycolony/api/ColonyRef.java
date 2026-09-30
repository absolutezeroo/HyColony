package dev.hycolony.api;

import java.util.Objects;

/**
 * A colony: the name of its world and its id there. A reference, not the colony: read it through {@code ColonyWorld}.
 *
 * @since 1.0
 */
public record ColonyRef(String world, int colonyId) {
    /** Refuses a missing world. */
    public ColonyRef {
        Objects.requireNonNull(world, "world");
    }
}
