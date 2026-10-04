package dev.hyangler.api.event;

import dev.hyangler.api.Catch;
import java.util.Optional;

/**
 * How a cast ended (spec § 7.4). Sealed: a case added later breaks the api.
 *
 * @since 1.0
 */
public sealed interface Outcome {
    /** Hooked in the window; the catch given, or empty if a hook cancelled it or nothing could bite. */
    record Caught(Optional<Catch> landed) implements Outcome {}

    /** Reeled in without a bite in progress. */
    record Escaped() implements Outcome {}

    /** Reeled in from the ground. */
    record Grounded() implements Outcome {}

    /** The line broke: too long. */
    record Broken() implements Outcome {}

    /** Ended without the angler: rod put away, angler gone, chunk unloaded, timeout. */
    record Cancelled() implements Outcome {}
}
