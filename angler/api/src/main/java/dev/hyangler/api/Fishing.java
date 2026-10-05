package dev.hyangler.api;

import dev.hyangler.api.condition.CatchHook;
import dev.hyangler.api.condition.ConditionTypes;
import dev.hyangler.api.event.FishingEvent;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.random.RandomGenerator;

/**
 * HyAngler's fishing engine, read through the plugin's holder (spec § 8). Rolls and chances read the data files loaded
 * once and the context given, and are safe from any thread; their one side effect is reporting a condition that
 * throws. Before the data loads, nothing bites.
 *
 * @since 1.0
 */
public interface Fishing {
    /** A catch for this context, or empty when nothing can bite there. Catch hooks are not applied (see land). */
    Optional<Catch> roll(FishingContext context, RandomGenerator random);

    /** Every possible catch in this context and its probability, most likely first; empty when nothing can bite. */
    List<CatchChance> chances(FishingContext context);

    /** The stats of a rod item, empty for an item without a rod file. */
    Optional<RodStats> rod(String itemId);

    /** Vanilla's bite delays for this lure (above 5 it counts as 5), the config's multiplier applied (spec § 5). */
    BiteTimes biteTimes(int lure, RandomGenerator random);

    /** Where another mod registers its condition types, during its setup, before the data loads. */
    ConditionTypes conditionTypes();

    /**
     * Adds a hook run on every landed catch, after the roll, in registration order (spec § 8.1); one that throws or
     * returns null is reported and skipped. Throws {@link NullPointerException} on a null argument.
     */
    Subscription addCatchHook(String owner, CatchHook hook);

    /**
     * Listens to one event type; the listener runs on the world thread, isolated from the others. Throws {@link
     * NullPointerException} on a null argument.
     */
    <E extends FishingEvent> Subscription subscribe(Class<E> type, Consumer<? super E> listener);
}
