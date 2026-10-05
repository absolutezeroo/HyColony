package dev.hyangler.core;

import dev.hyangler.api.Angler;
import dev.hyangler.api.BiteTimes;
import dev.hyangler.api.Catch;
import dev.hyangler.api.CatchChance;
import dev.hyangler.api.Fishing;
import dev.hyangler.api.FishingContext;
import dev.hyangler.api.RodStats;
import dev.hyangler.api.Subscription;
import dev.hyangler.api.condition.CatchHook;
import dev.hyangler.api.condition.ConditionTypes;
import dev.hyangler.api.event.FishingEvent;
import dev.hyangler.core.cast.BiteTimer;
import dev.hyangler.core.catalog.Catalog;
import dev.hyangler.core.catalog.CatalogReader;
import dev.hyangler.core.catalog.RawFile;
import dev.hyangler.core.condition.ConditionRegistry;
import dev.hyangler.core.roll.CatchRoller;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.random.RandomGenerator;

/**
 * HyAngler's engine behind the api (spec § 8.1): the catalog read once, rolls, rods, bite times, other mods' condition
 * types, and their catch hooks and event listeners (FishingHooks). Reads never block; the catalog swaps whole.
 */
public final class FishingService implements Fishing {
    private final AnglerSettings settings;
    private final Consumer<RuntimeException> onFailure;
    private final FishingHooks hooks;
    private final ConditionRegistry conditions = new ConditionRegistry();
    // One field, so a reader never sees the rods of one catalog and the catches of another.
    private volatile CatchRoller roller;

    /**
     * settings: the config's HyAngler section (bounded here); onFailure: where another mod's failing hook, listener
     * or condition is reported, from whichever thread rolled or published: it must be thread-safe.
     */
    public FishingService(AnglerSettings settings, Consumer<RuntimeException> onFailure) {
        this.settings = settings.bounded();
        this.onFailure = onFailure;
        this.hooks = new FishingHooks(onFailure);
        this.roller = new CatchRoller(Catalog.EMPTY, onFailure);
    }

    /** The settings in use, within their bounds. */
    public AnglerSettings settings() {
        return settings;
    }

    /** Reads the data files, replacing the catalog whole; rarityItems are the items with rarity states. */
    public void load(List<RawFile> files, Set<String> rarityItems) {
        Catalog read = CatalogReader.read(files, conditions, rarityItems, settings.maxLineDefault());
        roller = new CatchRoller(read, onFailure);
    }

    /** The catalog now, with the files left out (selftest). */
    public Catalog catalog() {
        return roller.catalog();
    }

    @Override
    public Optional<Catch> roll(FishingContext context, RandomGenerator random) {
        return roller.roll(context, random);
    }

    @Override
    public List<CatchChance> chances(FishingContext context) {
        return roller.chances(context);
    }

    @Override
    public Optional<RodStats> rod(String itemId) {
        return Optional.ofNullable(roller.catalog().rods().get(itemId));
    }

    @Override
    public BiteTimes biteTimes(int lure, RandomGenerator random) {
        return BiteTimer.roll(lure, settings.biteTimeMultiplier(), random);
    }

    @Override
    public ConditionTypes conditionTypes() {
        return conditions;
    }

    @Override
    public Subscription addCatchHook(String owner, CatchHook hook) {
        return hooks.addCatchHook(owner, hook);
    }

    @Override
    public <E extends FishingEvent> Subscription subscribe(Class<E> type, Consumer<? super E> listener) {
        return hooks.subscribe(type, listener);
    }

    /**
     * Rolls a catch, then runs the catch hooks in order; empty when nothing bites or a hook cancels. A hook that fails
     * is reported and skipped, the catch kept as it was. Publishes nothing: the caller does once the catch is given.
     */
    public Optional<Catch> land(Angler angler, FishingContext ctx, RandomGenerator rng) {
        return hooks.apply(angler, ctx, roll(ctx, rng));
    }

    /** Hands e to the listeners of its type, each isolated: one that fails is reported and the others still hear. */
    public void publish(FishingEvent e) {
        hooks.publish(e);
    }
}
