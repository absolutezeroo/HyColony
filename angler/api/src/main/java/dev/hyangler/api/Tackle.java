package dev.hyangler.api;

import java.util.Objects;
import java.util.Optional;

/**
 * What an angler fishes with, its parts' stats summed (spec § 8.1). P1 rods fill lure, luck and maxLine; the other
 * components belong to P3's parts and stay neutral until then: adding them later would break the api.
 *
 * @param lure each level shortens the wait by 100 ticks (vanilla Lure)
 * @param luck raises rare catches and treasure, lowers junk (vanilla Luck of the Sea)
 * @param maxLine the longest line, in blocks, before it breaks
 * @param reelSpeed P3: reeling speed factor, 1 neutral
 * @param lineStrength P3: what the line holds, 0 neutral (no limit)
 * @param hookSize P3: hook size, 0 neutral (any fish)
 * @param depthBias P3: blocks below the surface the float or sinker holds the bait, 0 neutral (surface)
 * @param bait P3: the bait's item id, empty without bait
 * @since 1.0
 */
public record Tackle(
        int lure,
        int luck,
        int maxLine,
        double reelSpeed,
        double lineStrength,
        int hookSize,
        int depthBias,
        Optional<String> bait) {
    /** The line of a rod whose file gives none, in blocks (vanilla breaks the line beyond 32 blocks). */
    public static final int DEFAULT_MAX_LINE = 32;

    /** No known rod: no lure, no luck, the default line. */
    public static final Tackle NONE = of(0, 0, DEFAULT_MAX_LINE);

    /** Throws {@link IllegalArgumentException} on a negative lure, luck, size or depth, or a line under 1 block. */
    public Tackle {
        Objects.requireNonNull(bait, "bait");
        if (lure < 0 || luck < 0 || maxLine < 1 || hookSize < 0 || depthBias < 0) {
            throw new IllegalArgumentException("invalid tackle: lure " + lure + ", luck " + luck + ", line " + maxLine);
        }
    }

    /** A P1 rod: lure, luck and line, every P3 part neutral. */
    public static Tackle of(int lure, int luck, int maxLine) {
        return new Tackle(lure, luck, maxLine, 1.0, 0.0, 0, 0, Optional.empty());
    }
}
