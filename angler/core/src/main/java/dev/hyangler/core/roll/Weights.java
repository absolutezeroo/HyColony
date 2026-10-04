package dev.hyangler.core.roll;

import dev.hyangler.api.CatchCategory;
import dev.hyangler.api.FishingContext;
import dev.hyangler.core.catalog.Entry;
import dev.hyangler.core.catalog.Modifier;

/**
 * Effective weights (spec § 5, § 6.1): vanilla's max(⌊weight + quality × luck⌋, 0) (LootPoolEntry quality), then the
 * Tide-style modifiers that hold; vanilla's categories fish 85 (−1), junk 10 (−2), treasure 5 (+2).
 */
final class Weights {
    private static final int FISH_WEIGHT = 85;
    private static final int FISH_QUALITY = -1;
    private static final int JUNK_WEIGHT = 10;
    private static final int JUNK_QUALITY = -2;
    private static final int TREASURE_WEIGHT = 5;
    private static final int TREASURE_QUALITY = 2;

    private Weights() {}

    /** The entry's weight in ctx: 0 when its condition fails. */
    static int effective(Entry e, FishingContext ctx) {
        if (!e.condition().test(ctx)) {
            return 0;
        }
        double w = e.weight() + e.quality() * ctx.tackle().luck();
        for (Modifier m : e.modifiers()) {
            if (m.when().test(ctx)) {
                w *= m.multiplier();
            }
        }
        return Math.max(0, (int) Math.floor(w));
    }

    /** A category's weight in ctx; treasure needs open water (vanilla FishingHook open-water rule). */
    static int category(CatchCategory c, FishingContext ctx) {
        if (c == CatchCategory.TREASURE && !ctx.openWater()) {
            return 0;
        }
        int luck = ctx.tackle().luck();
        return Math.max(
                0,
                switch (c) {
                    case FISH -> FISH_WEIGHT + FISH_QUALITY * luck;
                    case JUNK -> JUNK_WEIGHT + JUNK_QUALITY * luck;
                    case TREASURE -> TREASURE_WEIGHT + TREASURE_QUALITY * luck;
                });
    }
}
