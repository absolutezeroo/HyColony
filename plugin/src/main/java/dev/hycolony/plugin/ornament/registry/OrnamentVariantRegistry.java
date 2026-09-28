package dev.hycolony.plugin.ornament.registry;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import dev.hycolony.plugin.ornament.api.OrnamentVariant;
import dev.hycolony.plugin.ornament.api.VariantKey;
import dev.hycolony.plugin.ornament.persistence.VariantStore;
import dev.hycolony.plugin.ornament.runtime.BlockTypeSynchronizer;
import dev.hycolony.plugin.ornament.runtime.DynamicBlockTypeFactory;
import dev.hycolony.plugin.ornament.runtime.VariantIconPublisher;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import org.jspecify.annotations.Nullable;

/**
 * Cache {@link VariantKey} -> runtime BlockType: two identical requests always get the same BlockType, and a missing
 * variant is created once, even when requested again while it is being created.
 */
public final class OrnamentVariantRegistry {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    private static final long CREATE_TIMEOUT_SECONDS = 30;

    // Read and written from world threads and from the creation threads.
    private final Map<VariantKey, CompletableFuture<OrnamentVariant>> variants = new ConcurrentHashMap<>();
    private final DynamicBlockTypeFactory factory = new DynamicBlockTypeFactory();
    private final BlockTypeSynchronizer synchronizer;
    private final VariantStore store;
    private final VariantIconPublisher icons;

    /**
     * @param store the saved variants, extended by each creation
     * @param icons renders and publishes each variant's own icon
     */
    public OrnamentVariantRegistry(BlockTypeSynchronizer synchronizer, VariantStore store, VariantIconPublisher icons) {
        this.synchronizer = synchronizer;
        this.store = store;
        this.icons = icons;
    }

    /** Which inventory icon a new variant's item gets. */
    public enum Icon {
        /** Its own icon, rendered from its two textures and sent to clients (falls back to MATERIAL on failure). */
        GENERATED,
        /** The vanilla icon of its fill material. */
        MATERIAL,
        /** No icon: clients show "?" (tested in game 2026-09-28). */
        NONE
    }

    /**
     * How a new variant is created: client rebuild flags, {@code UpdateBlockTypes} sent twice, its item's icon,
     * whether a generated icon shows the "asset created" notification ({@code announce}) and asks clients to refresh
     * their icons ({@code iconRefresh}).
     */
    public record Creation(
            BlockTypeSynchronizer.Rebuild rebuild, boolean twice, Icon icon, boolean announce, boolean iconRefresh) {}

    /** A variant request: its future and whether this call started the creation. */
    public record Request(CompletableFuture<OrnamentVariant> variant, boolean created) {}

    /**
     * The variant of {@code key}: the cached one, else created off the calling thread (safe from a world thread) and
     * broadcast as {@code creation} says; a cached variant ignores it. A failed creation completes exceptionally and is
     * forgotten, so it can be retried.
     */
    public Request request(VariantKey key, Creation creation) {
        boolean[] created = {false};
        CompletableFuture<OrnamentVariant> variant = variants.computeIfAbsent(key, k -> {
            created[0] = true;
            LOG.at(Level.INFO).log("hyornament: %s not cached, creating %s", k.id(), k.blockTypeKey());
            // No state may wait forever (CLAUDE.md § 4): a stuck load fails, is forgotten and can be retried.
            return CompletableFuture.supplyAsync(() -> create(k, creation))
                    .orTimeout(CREATE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        });
        if (created[0]) {
            var _ = variant.whenComplete((v, t) -> {
                if (t != null) {
                    variants.remove(key, variant);
                }
            });
        } else {
            LOG.at(Level.INFO).log("hyornament: %s cached, reusing %s", key.id(), key.blockTypeKey());
        }
        return new Request(variant, created[0]);
    }

    /**
     * Registers every saved variant again, in one store load and without any client rebuild; call it at boot, off
     * any world thread and before chunks load. A variant that fails is logged and skipped.
     */
    public void restoreSaved() {
        List<BlockType> types = new ArrayList<>();
        List<Item> items = new ArrayList<>();
        List<VariantKey> keys = new ArrayList<>();
        for (VariantKey key : store.load()) {
            try {
                BlockType type = factory.create(key);
                items.add(factory.createItem(key, icon(key, Icon.GENERATED, false)));
                types.add(type);
                keys.add(key);
            } catch (RuntimeException e) {
                LOG.at(Level.SEVERE).withCause(e).log("hyornament: cannot restore %s", key.id());
            }
        }
        if (types.isEmpty()) {
            return;
        }
        // At boot no player is connected: blocks and items reach clients in their Init packets.
        synchronizer.register(types, BlockTypeSynchronizer.Rebuild.NONE, false);
        synchronizer.registerItems(items, BlockTypeSynchronizer.Rebuild.NONE, false);
        keys.forEach(k -> variants.put(k, CompletableFuture.completedFuture(variant(k))));
        LOG.at(Level.INFO).log("hyornament: restored %d saved variant(s)", keys.size());
    }

    /** Builds, registers and records {@code key}'s BlockType, then its Item; runs on a creation thread. */
    private OrnamentVariant create(VariantKey key, Creation creation) {
        String icon = icon(key, creation.icon(), creation.announce());
        Item item = factory.createItem(key, icon);
        synchronizer.register(List.of(factory.create(key)), creation.rebuild(), creation.twice());
        // A new icon file may need clients to refresh their item icons; vanilla icons never do.
        boolean refresh = creation.icon() == Icon.GENERATED && creation.iconRefresh();
        synchronizer.registerItems(List.of(item), creation.rebuild(), refresh);
        store.add(key);
        return variant(key);
    }

    /** {@code key}'s item icon for {@code mode}; a GENERATED icon that fails is logged and becomes MATERIAL. */
    private @Nullable String icon(VariantKey key, Icon mode, boolean notify) {
        return switch (mode) {
            case MATERIAL -> key.secondary().icon();
            case NONE -> null;
            case GENERATED -> {
                try {
                    yield icons.publish(key, notify);
                } catch (RuntimeException | LinkageError | java.awt.AWTError e) { // AWT may lack native libraries
                    LOG.at(Level.SEVERE).withCause(e).log(
                            "hyornament: icon of %s failed, using its material's", key.id());
                    yield key.secondary().icon();
                }
            }
        };
    }

    private static OrnamentVariant variant(VariantKey key) {
        return new OrnamentVariant(key, BlockType.getAssetMap().getIndex(key.blockTypeKey()));
    }
}
