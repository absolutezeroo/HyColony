package dev.hycolony.api.debug;

import dev.hycolony.api.CitizenRef;
import dev.hycolony.api.Experimental;
import dev.hycolony.api.Pos;
import dev.hycolony.api.Vec;

/**
 * A citizen's walk to {@code target} ended {@code how} ({@code CLOSE}, {@code NAV_ENDED}, {@code IN_REACH},
 * {@code TELEPORTED}, {@code GAVE_UP}), its body at {@code at}, {@code distance} blocks from what it walked to;
 * {@code nav} is its nav's status then ({@code ARRIVED}, {@code BLOCKED}...), {@code MOVING} when the walk ended
 * without its nav ({@code CLOSE}, {@code TELEPORTED}).
 *
 * @since 1.0
 */
@Experimental
public record WalkEnded(CitizenRef citizen, Pos target, Vec at, String how, double distance, String nav) {}
