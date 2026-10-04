package dev.hyangler.api.condition;

import dev.hyangler.api.Angler;
import dev.hyangler.api.Catch;
import dev.hyangler.api.FishingContext;
import java.util.Optional;

/**
 * Changes or cancels a landed catch (spec § 8.1).
 *
 * @since 1.0
 */
@FunctionalInterface
public interface CatchHook {
    /** The catch to give, the same or another; empty cancels it (nothing is given). */
    Optional<Catch> apply(Angler angler, FishingContext context, Catch landed);
}
