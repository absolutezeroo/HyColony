package dev.hycolony.api.debug;

import dev.hycolony.api.CitizenRef;
import dev.hycolony.api.Experimental;

/**
 * A citizen's AI went from the state {@code from} to {@code to} ({@code IDLE}, {@code WORKING}...).
 *
 * @since 1.0
 */
@Experimental
public record CitizenStateChanged(CitizenRef citizen, String from, String to) {}
