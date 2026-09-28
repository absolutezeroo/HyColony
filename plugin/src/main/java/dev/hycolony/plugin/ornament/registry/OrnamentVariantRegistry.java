package dev.hycolony.plugin.ornament.registry;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import dev.hycolony.core.ornament.ShapeCatalog;
import dev.hycolony.core.ornament.VariantKey;
import dev.hycolony.plugin.ornament.api.OrnamentVariant;
import dev.hycolony.plugin.ornament.persistence.VariantStore;
import dev.hycolony.plugin.ornament.runtime.BlockTypeSynchronizer;
import dev.hycolony.plugin.ornament.runtime.BlockTypeSynchronizer.Rebuild;
import dev.hycolony.plugin.ornament.runtime.MaterialCatalog;
import dev.hycolony.plugin.ornament.runtime.VariantAssets;
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
 * created in one batch: one BlockType load and one Item load, so at most one client atlas rebuild.
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
    private final VariantBuilder builder;
    private volatile @Nullable Catalogs catalogs;

    /**
     * @param store the saved variants, extended by each creation
     * @param assets generates pair textures and icons
     */
    public OrnamentVariantRegistry(BlockTypeSynchronizer synchronizer, VariantStore store, VariantAssets assets) {
        this.synchronizer = synchronizer;
        this.store = store;
        this.builder = new VariantBuilder(assets);
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
            LOG.at(Level.INFO).log("hyornament: creating %d variant(s)", fresh.size());
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
     * Takes the catalogs and registers every saved variant again, in one store load and without any client rebuild;
     * call it at boot, off any world thread and before chunks load. A variant that fails is logged and skipped.
     */
    public void start(Catalogs loaded) {
        catalogs = loaded;
        List<VariantKey> saved = store.load(loaded.shapes());
        if (saved.isEmpty()) {
            return;
        }
        long start = System.nanoTime();
        Batch batch = create(saved, true);
        batch.done().forEach(k -> variants.put(k, CompletableFuture.completedFuture(variant(k))));
        LOG.at(Level.INFO).log(
                "hyornament: restored %d saved variant(s) in %d ms",
                batch.done().size(), (System.nanoTime() - start) / 1_000_000);
    }

    /**
     * Builds and registers keys' blocks (textures first), then their items, and records them. At boot nothing is
     * sent (no player yet: blocks and items reach clients in their Init packets); otherwise UpdateBlockTypes goes
     * twice (the client misses the first runtime one), with an atlas rebuild only when a pair texture is new, and
     * UpdateItems asks clients to refresh their icons (in game 2026-09-28).
     */
    private Batch create(List<VariantKey> keys, boolean boot) {
        Catalogs loaded = catalogs;
        if (loaded == null) {
            throw new IllegalStateException("ornament catalogs not loaded yet");
        }
        VariantBuilder.Built built = builder.build(keys, loaded.materials());
        if (!built.types().isEmpty()) {
            synchronizer.register(built.types(), built.newTexture() && !boot ? Rebuild.TEXTURES : Rebuild.NONE, !boot);
            synchronizer.registerItems(built.items(), Rebuild.NONE, !boot);
            if (!boot) {
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
