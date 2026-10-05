package dev.hyangler.core.roll;

import dev.hyangler.api.CatchCategory;
import dev.hyangler.api.FishingContext;
import dev.hyangler.api.condition.Condition;
import dev.hyangler.core.catalog.Entry;
import dev.hyangler.core.catalog.Modifier;
import java.util.function.Consumer;

/**
 * Effective weights (spec § 5, § 6.1): vanilla's max(⌊weight + quality × luck⌋, 0) (LootPoolEntry quality), then the
 * Tide-style modifiers that hold; vanilla's categories fish 85 (−1), junk 10 (−2), treasure 5 (+2).
 */
final class Weights {
    /** Deviation from vanilla: luck past this counts as this, which keeps every weight and sum within an int. */
    static final int MAX_LUCK = 1024;

    private static final int FISH_WEIGHT = 85;
    private static final int FISH_QUALITY = -1;
    private static final int JUNK_WEIGHT = 10;
    private static final int JUNK_QUALITY = -2;
    private static final int TREASURE_WEIGHT = 5;
    private static final int TREASURE_QUALITY = 2;

    private Weights() {}

    /** The entry's weight in ctx: 0 when its condition fails; a condition that throws fails, reported to onFailure. */
    static int effective(Entry e, FishingContext ctx, Consumer<RuntimeException> onFailure) {
        if (!holds(e.condition(), ctx, onFailure)) {
            return 0;
        }
        double w = e.weight() + (double) e.quality() * luck(ctx);
        for (Modifier m : e.modifiers()) {
            if (holds(m.when(), ctx, onFailure)) {
                w *= m.multiplier();
            }
        }
        return Math.max(0, (int) Math.floor(w)); // the cast saturates a huge weight at Integer.MAX_VALUE
    }

    /** A category's weight in ctx; treasure needs open water (vanilla FishingHook open-water rule). */
    static int category(CatchCategory c, FishingContext ctx) {
        if (c == CatchCategory.TREASURE && !ctx.openWater()) {
            return 0;
        }
        int luck = luck(ctx);
        return Math.max(
                0,
                switch (c) {
                    case FISH -> FISH_WEIGHT + FISH_QUALITY * luck;
                    case JUNK -> JUNK_WEIGHT + JUNK_QUALITY * luck;
                    case TREASURE -> TREASURE_WEIGHT + TREASURE_QUALITY * luck;
                });
    }

    /**
     * The tackle's luck, at most MAX_LUCK. Deviation from vanilla (Hytale world): vanilla adds the player's Luck
     * attribute (FishingHook.retrieve: withLuck(luck + owner.getLuck())); Hytale has no luck attribute.
     */
    static int luck(FishingContext ctx) {
        return Math.min(ctx.tackle().luck(), MAX_LUCK);
    }

    // Another mod's condition must not break a roll, as its catch hooks cannot (spec § 8.1).
    private static boolean holds(Condition c, FishingContext ctx, Consumer<RuntimeException> onFailure) {
        try {
            return c.test(ctx);
        } catch (RuntimeException e) {
            onFailure.accept(e);
            return false;
        }
    }
}
