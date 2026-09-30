package dev.hycolony.api.event;

import dev.hycolony.api.CitizenRef;

/**
 * A new citizen joined its colony.
 *
 * @since 1.0
 */
public record CitizenSpawned(CitizenRef citizen) {}
