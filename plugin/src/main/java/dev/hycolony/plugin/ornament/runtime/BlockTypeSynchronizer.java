package dev.hycolony.plugin.ornament.runtime;

import com.hypixel.hytale.assetstore.AssetUpdateQuery;
import com.hypixel.hytale.assetstore.map.BlockTypeAssetMap;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.UpdateType;
import com.hypixel.hytale.protocol.packets.assets.UpdateBlockTypes;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.universe.Universe;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * Registers runtime BlockTypes in Hytale's BlockType store, which gives each a new block id and broadcasts one
 * {@code UpdateBlockTypes} (AddOrUpdate, only these types) to connected players. It never sends
 * {@code RequestCommonAssetsRebuild}: no common asset (texture, model) is added.
 *
 * <p>Must not run on a world thread: {@code World.tick} holds the read lock of {@code AssetRegistry.ASSET_LOCK} and
 * {@code AssetStore.loadAssets} takes its write lock, which deadlocks (docs/research/plugin-b-api.md § 17).
 */
public final class BlockTypeSynchronizer {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    /** Client caches the {@code UpdateBlockTypes} packet asks to rebuild (its four {@code update*} flags). */
    public enum Rebuild {
        /** No flag, as vanilla registers its "Unknown" placeholder blocks ({@code BlockType.getBlockIdOrUnknown}). */
        NONE,
        /**
         * Block textures only: the client rebuilds its block texture atlas, which a new model texture needs (without
         * it, a composed variant shows the wrong atlas region; in game 2026-09-28).
         */
        TEXTURES,
        /**
         * The Asset Editor's flags for the edited fields ({@code UIRebuildCaches} of DrawType, Textures,
         * CustomModel, CustomModelTexture in {@code BlockType.CODEC}): block textures, models, model textures.
         */
        EDITOR,
        /** Every flag, as a plain {@code loadAssets} (the 2026-09-27 experiment). */
        ALL;

        /** The store query flags of this mode. */
        AssetUpdateQuery.RebuildCache cache() {
            return switch (this) {
                case NONE -> AssetUpdateQuery.RebuildCache.NO_REBUILD;
                case TEXTURES -> new AssetUpdateQuery.RebuildCache(true, false, false, false, false, false);
                case EDITOR -> new AssetUpdateQuery.RebuildCache(true, true, true, false, false, false);
                case ALL -> AssetUpdateQuery.RebuildCache.DEFAULT;
            };
        }
    }

    private final String packKey;

    /** @param packKey the plugin's asset pack name ({@code Group:Name}, as PluginManager registers it) */
    public BlockTypeSynchronizer(String packKey) {
        this.packKey = packKey;
    }

    /**
     * Loads {@code types} into the store and broadcasts them with {@code rebuild}'s flags. When {@code twice}, the
     * first packet carries no flag and a second copy carries them: each flag makes the client rebuild a cache (the
     * block atlas flickers once per rebuild, in game 2026-09-28), and whichever packet the client keeps, the flags
     * come last. Returns nothing: read the ids back from {@code BlockType.getAssetMap()}. Throws
     * {@link IllegalStateException} when a type did not load.
     */
    public void register(List<BlockType> types, Rebuild rebuild, boolean twice) {
        BlockTypeAssetMap<String, BlockType> map = BlockType.getAssetMap();
        int maxIdBefore = map.getNextIndex();
        long start = System.nanoTime();
        Rebuild first = twice ? Rebuild.NONE : rebuild;
        BlockType.getAssetStore().loadAssets(packKey, types, new AssetUpdateQuery(first.cache()));
        long micros = (System.nanoTime() - start) / 1_000;
        for (BlockType type : types) {
            int id = map.getIndex(type.getId());
            if (id == Integer.MIN_VALUE) {
                throw new IllegalStateException("BlockType " + type.getId() + " was not loaded");
            }
            LOG.at(Level.FINE).log("hyornament: registered %s as block id %d", type.getId(), id);
        }
        LOG.at(Level.INFO).log(
                "hyornament: UpdateBlockTypes AddOrUpdate of %d type(s), maxId %d -> %d, %s, in %d us",
                types.size(), maxIdBefore, map.getNextIndex(), first.cache(), micros);
        if (twice) {
            try {
                resend(types, rebuild, map);
            } catch (RuntimeException e) { // optional workaround: the types are already registered and sent once
                LOG.at(Level.SEVERE).withCause(e).log("hyornament: second UpdateBlockTypes failed");
            }
        }
    }

    /**
     * Loads {@code items} into the Item store, which broadcasts one {@code UpdateItems} AddOrUpdate with
     * {@code rebuild}'s flags, plus {@code updateIcons} when {@code icons} (an item names a new icon file). Register
     * their blocks first: an item's packet carries its block's id. Throws {@link IllegalStateException} when an item
     * did not load.
     */
    public void registerItems(List<Item> items, Rebuild rebuild, boolean icons) {
        AssetUpdateQuery.RebuildCache c = rebuild.cache();
        AssetUpdateQuery.RebuildCache cache = new AssetUpdateQuery.RebuildCache(
                c.isBlockTextures(), c.isModels(), c.isModelTextures(), c.isMapGeometry(), icons, false);
        Item.getAssetStore().loadAssets(packKey, items, new AssetUpdateQuery(cache));
        for (Item item : items) {
            if (Item.getAssetMap().getAsset(item.getId()) == null) {
                throw new IllegalStateException("Item " + item.getId() + " was not loaded");
            }
        }
        LOG.at(Level.INFO).log(
                "hyornament: UpdateItems AddOrUpdate of %d item(s), updateIcons=%b", items.size(), icons);
    }

    /**
     * Broadcasts the same AddOrUpdate packet {@code loadAssets} just sent. Seen in game (2026-09-28): a client
     * renders the type of the first runtime {@code UpdateBlockTypes} of its connection pink and black until it
     * reconnects, while the following ones render; the second copy is the workaround under test.
     */
    private static void resend(List<BlockType> types, Rebuild rebuild, BlockTypeAssetMap<String, BlockType> map) {
        Map<Integer, com.hypixel.hytale.protocol.BlockType> packets = new HashMap<>();
        for (BlockType type : types) {
            packets.put(map.getIndex(type.getId()), type.toPacket());
        }
        AssetUpdateQuery.RebuildCache cache = rebuild.cache();
        Universe.get()
                .broadcastPacketNoCache(new UpdateBlockTypes(
                        UpdateType.AddOrUpdate,
                        map.getNextIndex(),
                        packets,
                        cache.isBlockTextures(),
                        cache.isModelTextures(),
                        cache.isModels(),
                        cache.isMapGeometry()));
        LOG.at(Level.INFO).log(
                "hyornament: UpdateBlockTypes sent a second time for %d type(s), %s", types.size(), cache);
    }
}
