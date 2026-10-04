package dev.hycolony.api.event;

import dev.hycolony.api.CitizenRef;

/**
 * A citizen died; it is no longer in its colony, so lookups of {@code citizen} find nothing.
 *
 * @since 1.4
 */
public record CitizenDied(CitizenRef citizen) {}
