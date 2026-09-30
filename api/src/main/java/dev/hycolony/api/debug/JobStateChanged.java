package dev.hycolony.api.debug;

import dev.hycolony.api.CitizenRef;
import dev.hycolony.api.Experimental;

/**
 * A citizen's job AI went from the step {@code from} to {@code to} ({@code DELIVERY}, {@code BUILDING_STEP}...);
 * {@code ""} for none, before its first step or after its job AI ended.
 *
 * @since 1.0
 */
@Experimental
public record JobStateChanged(CitizenRef citizen, String from, String to) {}
