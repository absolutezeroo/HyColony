package dev.hycolony.api.debug;

import dev.hycolony.api.ColonyRef;
import dev.hycolony.api.Experimental;

/**
 * The request {@code request} (its id, as in {@code RequestSnapshot}) of {@code colony} went from the state
 * {@code from} to {@code to} ({@code ASSIGNED}, {@code IN_PROGRESS}, {@code COMPLETED}...). It is heard in the midst
 * of the request system's work, a request tree maybe half built: a listener only notes it, and never calls
 * {@link DebugAccess#check} from there.
 *
 * @since 1.0
 */
@Experimental
public record RequestStateChanged(ColonyRef colony, String request, String from, String to) {}
