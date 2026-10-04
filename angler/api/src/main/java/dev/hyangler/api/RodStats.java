package dev.hyangler.api;

/**
 * A rod's stats, from its {@code Server/HyAngler/Rods/} file (spec § 7.1).
 *
 * @param tier the Hytale tool tier, from 0 (Crude)
 * @since 1.0
 */
public record RodStats(int tier, int lure, int luck, int maxLine) {
    /** The tackle of this rod alone (P1: no parts). */
    public Tackle tackle() {
        return Tackle.of(lure, luck, maxLine);
    }
}
