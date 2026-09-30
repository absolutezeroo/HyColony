package dev.hycolony.core.app.diagnostics;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.port.Msg;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * A broken invariant: which one, said in the player's language, the citizen it concerns and where, when it has one.
 */
public record Violation(Code code, Msg detail, OptionalInt citizen, Optional<BlockPos> pos) {
    /**
     * The invariants of the v1 (debug-mod.md § 5: 1, 2, 3, 4, 7, 9 and 10). A trace is kept after the fact (a walk's
     * end, a failure), where the others are states a worker may pass through.
     */
    public enum Code {
        /** 1: the nav ended the walk on another floor than its target, or a plain walk more than 2 blocks off. */
        WALK_ENDED_AWAY(true),
        /** 2: no step change nor action for {@link Invariants#JOB_STEP_STALE_TICKS}, outside a legitimate wait. */
        JOB_STEP_STALE(false),
        /** 3: a step serving the head of its queue finds it empty, gone, or not in progress. */
        QUEUE_INCOHERENT(false),
        /** 4: a request assigned or in progress has no resolver. */
        REQUEST_UNRESOLVED(false),
        /** 7: the respawn checks found a loaded spot for a bodiless citizen, yet spawned no body there. */
        RESPAWN_FAILED(true),
        /** 9: its AIs caught {@link Invariants#REPEATED_FAILURES} exceptions or more since it was loaded. */
        AI_FAILING(true),
        /** 10: the stuck handler teleported or gave up on its current or last walk. */
        STUCK_ESCALATED(true);

        private final boolean trace;

        Code(boolean trace) {
            this.trace = trace;
        }

        /** Whether it is kept after the fact, which a worker's next move may clear, rather than a passing state. */
        public boolean trace() {
            return trace;
        }
    }
}
