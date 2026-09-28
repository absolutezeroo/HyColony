package dev.hycolony.plugin.ornament.registry;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import dev.hycolony.plugin.ornament.api.OrnamentShape;
import dev.hycolony.plugin.ornament.api.OrnamentVariant;
import dev.hycolony.plugin.ornament.api.VariantKey;
import dev.hycolony.plugin.ornament.persistence.VariantStore;
import dev.hycolony.plugin.ornament.runtime.BlockTypeSynchronizer;
import dev.hycolony.plugin.ornament.runtime.DynamicBlockTypeFactory;
import dev.hycolony.plugin.ornament.runtime.VariantAssets;
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
    private final VariantAssets assets;

    /**
     * @param store the saved variants, extended by each creation
     * @param assets generates each variant's own icon and composed texture
     */
    public OrnamentVariantRegistry(BlockTypeSynchronizer synchronizer, VariantStore store, VariantAssets assets) {
        this.synchronizer = synchronizer;
        this.store = store;
        this.assets = assets;
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
                List<BlockType> family = factory.create(key, composedTexture(key, false));
                items.add(factory.createItem(key, icon(key, Icon.GENERATED, false)));
                types.addAll(family);
                keys.add(key);
            } catch (RuntimeException | LinkageError | java.awt.AWTError e) { // AWT may lack native libraries
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
        // Every new file goes out before the packets naming it: model texture, then block; icon, then item.
        List<BlockType> family = factory.create(key, composedTexture(key, creation.announce()));
        String icon = icon(key, creation.icon(), creation.announce());
        Item item = factory.createItem(key, icon);
        // A new model texture only shows once clients rebuild their block texture atlas (in game 2026-09-28).
        BlockTypeSynchronizer.Rebuild rebuild =
                key.shape().layoutTexture().isPresent() && creation.rebuild() == BlockTypeSynchronizer.Rebuild.NONE
                        ? BlockTypeSynchronizer.Rebuild.TEXTURES
                        : creation.rebuild();
        synchronizer.register(family, rebuild, creation.twice());
        // A new icon file may need clients to refresh their item icons; vanilla icons never do.
        boolean refresh = creation.icon() == Icon.GENERATED && creation.iconRefresh();
        // The block's packet already rebuilt the atlas; another rebuild from the item would flicker once more.
        synchronizer.registerItems(List.of(item), BlockTypeSynchronizer.Rebuild.NONE, refresh);
        store.add(key);
        return variant(key);
    }

    /** {@code key}'s generated model texture for a composed shape, null for a cube + model one; throws on failure. */
    private @Nullable String composedTexture(VariantKey key, boolean announce) {
        return key.shape().layoutTexture().isPresent() ? assets.modelTexture(key, announce) : null;
    }

    /**
     * {@code key}'s item icon for {@code mode}; a GENERATED icon that fails is logged and becomes MATERIAL. Only the
     * timber frame has an icon painter: other shapes get MATERIAL for GENERATED.
     */
    private @Nullable String icon(VariantKey key, Icon mode, boolean announce) {
        if (mode == Icon.NONE) {
            return null;
        }
        if (mode == Icon.GENERATED && key.shape() == OrnamentShape.TIMBER_FRAME) {
            try {
                return assets.icon(key, announce);
            } catch (RuntimeException | LinkageError | java.awt.AWTError e) { // AWT may lack native libraries
                LOG.at(Level.SEVERE).withCause(e).log("hyornament: icon of %s failed, using its material's", key.id());
            }
        }
        return key.secondary().icon();
    }

    private static OrnamentVariant variant(VariantKey key) {
        return new OrnamentVariant(key, BlockType.getAssetMap().getIndex(key.blockTypeKey()));
    }
}
