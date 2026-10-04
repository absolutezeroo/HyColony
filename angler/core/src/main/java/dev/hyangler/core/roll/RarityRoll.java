package dev.hyangler.core.roll;

import dev.hyangler.api.Rarity;
import java.util.random.RandomGenerator;

/**
 * A caught fish's rarity state (spec § 6.3): the baited trap's weights (Drops_Fishing_Trap_Crude_Baited_Wild: common
 * 100, uncommon 50, rare 10, epic 5, legendary 1), each raised by luck × its quality (0 to +4).
 */
final class RarityRoll {
    private static final int[] WEIGHT = {100, 50, 10, 5, 1};
    private static final Rarity[] RARITIES = Rarity.values();

    private RarityRoll() {}

    /** One rarity for this luck. */
    static Rarity roll(int luck, RandomGenerator rng) {
        int[] w = weights(luck);
        int r = rng.nextInt(0, sum(w));
        for (int i = 0; i < w.length; i++) {
            r -= w[i];
            if (r < 0) {
                return RARITIES[i];
            }
        }
        return Rarity.COMMON;
    }

    /** Each rarity's probability for this luck, by ordinal. */
    static double[] chances(int luck) {
        int[] w = weights(luck);
        double total = sum(w);
        double[] out = new double[w.length];
        for (int i = 0; i < w.length; i++) {
            out[i] = w[i] / total;
        }
        return out;
    }

    private static int[] weights(int luck) {
        int[] w = new int[WEIGHT.length];
        for (int i = 0; i < w.length; i++) {
            w[i] = WEIGHT[i] + i * luck;
        }
        return w;
    }

    private static int sum(int[] w) {
        int s = 0;
        for (int v : w) {
            s += v;
        }
        return s;
    }
}
