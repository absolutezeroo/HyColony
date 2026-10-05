package dev.hyangler.api.condition;

import dev.hyangler.api.FishingContext;

/**
 * A rule a catch needs, read from a data file (spec § 6.4).
 *
 * @since 1.0
 */
@FunctionalInterface
public interface Condition {
    /** Whether the catch can happen in this context; pure. One that throws counts as false and is reported. */
    boolean test(FishingContext context);
}
