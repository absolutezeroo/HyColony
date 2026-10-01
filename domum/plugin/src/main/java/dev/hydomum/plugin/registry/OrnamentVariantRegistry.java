package dev.hydomum.plugin.registry;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.common.CommonAsset;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import dev.hydomum.api.ShapeCatalog;
import dev.hydomum.api.VariantKey;
import dev.hydomum.core.BootVariants;
import dev.hydomum.plugin.api.OrnamentVariant;
import dev.hydomum.plugin.api.RequiredVariants;
import dev.hydomum.plugin.persistence.VariantStore;
import dev.hydomum.plugin.runtime.BlockTypeSynchronizer;
import dev.hydomum.plugin.runtime.MaterialCatalog;
import dev.hydomum.plugin.runtime.VariantAssets;
import dev.hydomum.plugin.runtime.VariantPalette;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import org.jspecify.annotations.Nullable;

/**
 * Cache {@link VariantKey} -> runtime variant: two identical requests always get the same BlockType, and a missing
 * variant is created once, even when requested again while it is being created. Variants requested together are
 * created in one batch: one BlockType load and one Item load, whose new models and icons are sent together.
 */
public final class OrnamentVariantRegistry {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    private static final long CREATE_TIMEOUT_SECONDS = 30;

    /** What the variants are made of, known once assets are loaded. */
    public record Catalogs(ShapeCatalog shapes, MaterialCatalog materials) {}

    /** The keys a batch created. */
    private record Batch(List<VariantKey> done) {}

    // Read and written from world threads and from the creation threads.
    private final Map<VariantKey, CompletableFuture<OrnamentVariant>> variants = new ConcurrentHashMap<>();
    private final BlockTypeSynchronizer synchronizer;
    private final VariantStore store;
    private final VariantAssets assets;
    private final VariantPalette palette;
    private final VariantBuilder builder;
    private volatile @Nullable Catalogs catalogs;

    /**
     * @param store the saved variants, extended by each creation
     * @param assets generates models and icons
     * @param palette the texture two-material variants read, started with the catalogs
     */
    public OrnamentVariantRegistry(
            BlockTypeSynchronizer synchronizer, VariantStore store, VariantAssets assets, VariantPalette palette) {
        this.synchronizer = synchronizer;
        this.store = store;
        this.assets = assets;
        this.palette = palette;
        this.builder = new VariantBuilder(assets, palette);
    }

    /** The shapes and materials; empty before {@link #start}. */
    public Optional<Catalogs> catalogs() {
        return Optional.ofNullable(catalogs);
    }

    /** Whether key's variant exists or is being created. */
    public boolean known(VariantKey key) {
        return variants.containsKey(key);
    }

    /**
     * The variants of keys, in order: cached ones as they are, the others created together off the calling thread
     * (safe from a world thread) and broadcast. Fails when a key fails or before {@link #start}; a failed key is
     * forgotten, so it can be retried. A timeout only fails the answer: a slow creation that ends later still
     * registers and saves its variants, and asking again then reuses them.
     */
    public CompletableFuture<List<OrnamentVariant>> request(List<VariantKey> keys) {
        List<VariantKey> fresh = new ArrayList<>();
        CompletableFuture<Batch> batch = new CompletableFuture<>();
        List<CompletableFuture<OrnamentVariant>> wanted = new ArrayList<>();
        for (VariantKey key : keys) {
            wanted.add(variants.computeIfAbsent(key, k -> {
                fresh.add(k);
                CompletableFuture<OrnamentVariant> variant = batch.thenApply(b -> made(b, k));
                var _ = variant.whenComplete((v, t) -> {
                    if (t != null) {
                        variants.remove(k, variant);
                    }
                });
                return variant;
            }));
        }
        if (!fresh.isEmpty()) {
            LOG.at(Level.INFO).log("hydomum: creating %d variant(s)", fresh.size());
            // No state may wait forever (CLAUDE.md § 4): a stuck creation fails and can be retried.
            var _ = CompletableFuture.supplyAsync(() -> create(fresh, false))
                    .orTimeout(CREATE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .whenComplete((b, t) -> {
                        if (t != null) {
                            batch.completeExceptionally(t);
                        } else {
                            batch.complete(b);
                        }
                    });
        }
        return CompletableFuture.allOf(wanted.toArray(CompletableFuture[]::new))
                .thenApply(v -> wanted.stream().map(CompletableFuture::join).toList());
    }

    /**
     * Takes the catalogs, draws the palette, then registers every saved variant again and those other mods require
     * ({@link RequiredVariants}), in one store load and without any client rebuild, then saves the required ones; call
     * it at boot, off any world thread and before chunks load. A palette that cannot be drawn is logged: two-material
     * variants then fail one by one. A variant that fails, or a required id that is refused, is logged and skipped.
     */
    public void start(Catalogs loaded) {
        catalogs = loaded;
        try {
            palette.start(loaded.shapes(), loaded.materials());
        } catch (RuntimeException | LinkageError | java.awt.AWTError e) { // AWT may lack native libraries
            LOG.at(Level.SEVERE).withCause(e).log("hydomum: palette not drawn, two-material variants unavailable");
        }
        BootVariants.Result boot = BootVariants.merge(
                store.load(loaded.shapes()),
                RequiredVariants.required(),
                loaded.shapes(),
                loaded.materials().tags());
        boot.refused().forEach(id -> LOG.at(Level.WARNING).log("hydomum: required variant %s refused", id));
        List<VariantKey> saved = boot.keys();
        if (saved.isEmpty()) {
            return;
        }
        long start = System.nanoTime();
        Batch batch = create(saved, true);
        batch.done().forEach(k -> variants.put(k, CompletableFuture.completedFuture(variant(k))));
        // A required variant is saved like a crafted one: blocks placed from it survive the requiring mod's removal.
        store.add(batch.done());
        LOG.at(Level.INFO).log(
                "hydomum: registered %d saved or required variant(s) in %d ms",
                batch.done().size(), (System.nanoTime() - start) / 1_000_000);
    }

    /**
     * Builds keys' blocks and items (their new models and icons registered, not sent), sends the new models, registers
     * the blocks and items, then publishes the new icons and saves the keys in the {@link VariantStore}. At boot
     * nothing is sent nor saved ({@link #start} saves; no player yet: files, blocks and items reach clients when they
     * join); otherwise
     * UpdateBlockTypes goes twice (the client misses the first runtime one), and no packet asks for a texture rebuild.
     */
    private Batch create(List<VariantKey> keys, boolean boot) {
        Catalogs loaded = catalogs;
        if (loaded == null) {
            throw new IllegalStateException("ornament catalogs not loaded yet");
        }
        VariantBuilder.Built built = builder.build(keys, loaded.materials());
        if (!built.types().isEmpty()) {
            // Models go before the blocks that name them: nothing makes a client reread one later.
            List<CommonAsset> models = assets.takeUnsentModels(built.assetNames());
            if (!boot) {
                synchronizer.sendModels(models);
            }
            synchronizer.register(built.types(), !boot);
            synchronizer.registerItems(built.items());
            // Taken once the stores hold this batch: a key that failed before keeps its icon for its retry.
            List<CommonAsset> icons = assets.takeUnsentIcons(built.assetNames());
            if (!boot) {
                synchronizer.publishIcons(built.items(), icons);
                store.add(built.done());
            }
        }
        return new Batch(built.done());
    }

    /** key's variant once its batch ran; throws when the batch could not create it. */
    private static OrnamentVariant made(Batch batch, VariantKey key) {
        if (!batch.done().contains(key)) {
            throw new IllegalStateException("could not create " + key.id());
        }
        return variant(key);
    }

    /** key's registered variant, with the block id clients know it by. */
    private static OrnamentVariant variant(VariantKey key) {
        return new OrnamentVariant(key, BlockType.getAssetMap().getIndex(key.blockTypeKey()));
    }
}
