package dev.hyangler.core.roll;

import dev.hyangler.api.Catch;
import dev.hyangler.api.CatchCategory;
import dev.hyangler.api.CatchChance;
import dev.hyangler.api.FishingContext;
import dev.hyangler.api.Rarity;
import dev.hyangler.core.catalog.Catalog;
import dev.hyangler.core.catalog.Entry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * Rolls a catch (spec § 5, § 6): a category by vanilla's weights, then an entry of it by its effective weight, then a
 * count, and a rarity for a fish with rarity states. Pure: reads its immutable catalog and the context only.
 */
public final class CatchRoller {
    private static final CatchCategory[] CATEGORIES = CatchCategory.values();
    private final Catalog catalog;

    public CatchRoller(Catalog catalog) {
        this.catalog = catalog;
    }

    /** A catch, or empty when no entry of any category can bite in ctx. */
    public Optional<Catch> roll(FishingContext ctx, RandomGenerator rng) {
        int[] categoryWeights = categoryWeights(ctx);
        int total = sum(categoryWeights);
        if (total == 0) {
            return Optional.empty();
        }
        CatchCategory category = CATEGORIES[pick(categoryWeights, rng.nextInt(0, total))];
        List<Entry> entries = entries(category);
        int[] w = entryWeights(entries, ctx);
        Entry e = entries.get(pick(w, rng.nextInt(0, sum(w))));
        int count = e.minCount() == e.maxCount() ? e.minCount() : rng.nextInt(e.minCount(), e.maxCount() + 1);
        Optional<Rarity> rarity =
                e.rarities() ? Optional.of(RarityRoll.roll(ctx.tackle().luck(), rng)) : Optional.empty();
        return Optional.of(new Catch(e.id(), count, category, rarity, e.id()));
    }

    /** Every entry that can bite in ctx with its probability, most likely first; empty when none can. */
    public List<CatchChance> chances(FishingContext ctx) {
        int[] categoryWeights = categoryWeights(ctx);
        double total = sum(categoryWeights);
        List<CatchChance> out = new ArrayList<>();
        if (total == 0) {
            return out;
        }
        for (int k = 0; k < CATEGORIES.length; k++) {
            CatchCategory c = CATEGORIES[k];
            List<Entry> entries = entries(c);
            int[] w = entryWeights(entries, ctx);
            double share = categoryWeights[k] / total;
            double sum = sum(w);
            for (int i = 0; i < entries.size(); i++) {
                if (w[i] > 0) {
                    out.add(new CatchChance(
                            entries.get(i).id(),
                            c,
                            share * w[i] / sum,
                            entries.get(i).id()));
                }
            }
        }
        out.sort(Comparator.comparingDouble(CatchChance::probability).reversed());
        return out;
    }

    /** Each category's weight, in CATEGORIES' order; 0 for a category with no entry that can bite. */
    private int[] categoryWeights(FishingContext ctx) {
        int[] w = new int[CATEGORIES.length];
        for (int k = 0; k < CATEGORIES.length; k++) {
            CatchCategory c = CATEGORIES[k];
            w[k] = sum(entryWeights(entries(c), ctx)) > 0 ? Weights.category(c, ctx) : 0;
        }
        return w;
    }

    private List<Entry> entries(CatchCategory c) {
        return switch (c) {
            case FISH -> catalog.fish();
            case JUNK -> catalog.junk();
            case TREASURE -> catalog.treasure();
        };
    }

    private static int[] entryWeights(List<Entry> entries, FishingContext ctx) {
        int[] w = new int[entries.size()];
        for (int i = 0; i < w.length; i++) {
            w[i] = Weights.effective(entries.get(i), ctx);
        }
        return w;
    }

    private static int pick(int[] weights, int r) {
        int left = r;
        for (int i = 0; i < weights.length; i++) {
            left -= weights[i];
            if (left < 0) {
                return i;
            }
        }
        throw new IllegalStateException("weighted pick past the total");
    }

    private static int sum(int[] w) {
        int s = 0;
        for (int v : w) {
            s += v;
        }
        return s;
    }
}
