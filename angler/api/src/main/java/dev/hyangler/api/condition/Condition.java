package dev.hyangler.api.condition;

import dev.hyangler.api.FishingContext;

/**
 * A rule a catch needs, read from a data file (spec § 6.4).
 *
 * @since 1.0
 */
@FunctionalInterface
public interface Condition {
    /** Whether the catch can happen in this context; pure. */
    boolean test(FishingContext context);
}
