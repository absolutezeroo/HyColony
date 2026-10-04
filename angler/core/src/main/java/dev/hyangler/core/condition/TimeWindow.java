package dev.hyangler.core.condition;

/**
 * An hour window of Hytale's day, from (inclusive) to (exclusive); a window whose start is after its end passes
 * midnight.
 */
record TimeWindow(double from, double to) {

    /** Whether hour, from 0 to 24, falls in the window. */
    boolean contains(double hour) {
        return from <= to ? hour >= from && hour < to : hour >= from || hour < to;
    }
}
