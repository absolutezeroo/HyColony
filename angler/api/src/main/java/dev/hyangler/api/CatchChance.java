package dev.hyangler.api;

/**
 * One possible catch in a context, and its probability among all of them (spec § 8.1, {@code /hyangler test}).
 *
 * @param probability from 0 to 1; the chances of one context sum to 1, or are empty
 * @since 1.0
 */
public record CatchChance(String itemId, CatchCategory category, double probability, String source) {}
