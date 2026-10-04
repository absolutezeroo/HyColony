package dev.hyangler.core.catalog;

import dev.hyangler.api.condition.Condition;

/** Multiplies an entry's weight while its condition holds (Tide conditional modifier, spec § 6.1). */
public record Modifier(Condition when, double multiplier) {}
