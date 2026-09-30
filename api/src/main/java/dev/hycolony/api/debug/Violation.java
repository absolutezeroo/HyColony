package dev.hycolony.api.debug;

import dev.hycolony.api.ApiText;
import dev.hycolony.api.CitizenRef;
import dev.hycolony.api.Experimental;
import dev.hycolony.api.Pos;
import java.util.Optional;

/**
 * A broken invariant: {@code code} ({@code WALK_ENDED_AWAY}, {@code JOB_STEP_STALE}, {@code QUEUE_INCOHERENT},
 * {@code REQUEST_UNRESOLVED}, {@code RESPAWN_FAILED}, {@code AI_FAILING}, {@code STUCK_ESCALATED}), said by
 * {@code detail}, with the citizen it concerns and where, when it has them.
 *
 * @since 1.0
 */
@Experimental
public record Violation(String code, ApiText detail, Optional<CitizenRef> citizen, Optional<Pos> pos) {}
