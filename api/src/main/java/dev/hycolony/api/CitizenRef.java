package dev.hycolony.api;

import java.util.Objects;

/**
 * A citizen: its colony and its id there. Stable across saves and restarts, unlike the body in the world.
 *
 * @since 1.0
 */
public record CitizenRef(ColonyRef colony, int citizenId) {
    /** Refuses a missing colony. */
    public CitizenRef {
        Objects.requireNonNull(colony, "colony");
    }
}
