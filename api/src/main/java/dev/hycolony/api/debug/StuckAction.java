package dev.hycolony.api.debug;

import dev.hycolony.api.CitizenRef;
import dev.hycolony.api.Experimental;
import dev.hycolony.api.Pos;
import dev.hycolony.api.Vec;

/**
 * The stuck handler took {@code action} ({@code REPATH}, {@code TELEPORT}, {@code GIVE_UP}) on a citizen's walk to
 * {@code target}, its body at {@code at}.
 *
 * @since 1.0
 */
@Experimental
public record StuckAction(CitizenRef citizen, Pos target, Vec at, String action) {}
