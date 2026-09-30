package dev.hycolony.api;

import java.util.Objects;

/**
 * What an action did. Its four cases are fixed: adding one would break an addon's exhaustive {@code switch}, so it
 * would be a major version.
 *
 * @since 1.0
 */
public sealed interface ActionResult {
    /** Done. */
    record Done() implements ActionResult {}

    /** Refused, for {@code reason}: the actor lacks the right, or the game's rules forbid it now. */
    record Refused(ApiText reason) implements ActionResult {
        /** Refuses a missing reason. */
        public Refused {
            Objects.requireNonNull(reason, "reason");
        }
    }

    /** What the action names (colony, citizen, request...) does not exist. */
    record NotFound() implements ActionResult {}

    /** It exists but cannot act now, such as a citizen whose body is not loaded. */
    record Unavailable() implements ActionResult {}
}
