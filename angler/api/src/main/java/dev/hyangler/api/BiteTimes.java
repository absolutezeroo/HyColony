package dev.hyangler.api;

/**
 * The three vanilla delays of one bite, in ticks (spec § 5).
 *
 * @param waitTicks before a fish comes, at least 1
 * @param approachTicks the fish swimming to the bobber
 * @param windowTicks how long the bite lasts: the time to hook it
 * @since 1.0
 */
public record BiteTimes(int waitTicks, int approachTicks, int windowTicks) {}
