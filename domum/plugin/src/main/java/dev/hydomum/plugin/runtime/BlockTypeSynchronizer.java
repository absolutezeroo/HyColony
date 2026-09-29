package dev.hydomum.plugin.runtime;

import com.hypixel.hytale.assetstore.AssetUpdateQuery;
import com.hypixel.hytale.assetstore.map.BlockTypeAssetMap;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.ItemBase;
import com.hypixel.hytale.protocol.UpdateType;
import com.hypixel.hytale.protocol.packets.assets.UpdateBlockTypes;
import com.hypixel.hytale.protocol.packets.assets.UpdateItems;
import com.hypixel.hytale.server.core.asset.common.CommonAsset;
import com.hypixel.hytale.server.core.asset.common.CommonAssetModule;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.universe.Universe;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * Registers runtime BlockTypes and Items in Hytale's stores, then publishes a batch's new common assets (PNGs) to
 * connected players. The stores load first and their packets carry no client rebuild flag; the new PNGs go next;
 * the flags that make clients read them come last, in their own packets (the sequencing of Frames'
 * DynamicAssetReloader, with targeted flags instead of its {@code RequestCommonAssetsRebuild}, which is never sent).
 *
 * <p>Must not run on a world thread: {@code World.tick} holds the read lock of {@code AssetRegistry.ASSET_LOCK} and
 * {@code AssetStore.loadAssets} takes its write lock, which deadlocks (docs/research/plugin-b-api.md § 17).
 */
public final class BlockTypeSynchronizer {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    /**
     * Block textures only: the client rebuilds its block texture atlas, which a new model texture needs (without it,
     * a composed variant shows the wrong atlas region; in game 2026-09-28).
     */
    private static final AssetUpdateQuery.RebuildCache BLOCK_TEXTURES =
            new AssetUpdateQuery.RebuildCache(true, false, false, false, false, false);

    private final String packKey;

    /** @param packKey the plugin's asset pack name ({@code Group:Name}, as PluginManager registers it) */
    public BlockTypeSynchronizer(String packKey) {
        this.packKey = packKey;
    }

    /**
     * Loads {@code types} into the store, which gives each a block id and broadcasts one {@code UpdateBlockTypes}
     * AddOrUpdate without rebuild flag (as vanilla registers its "Unknown" blocks). When {@code twice}, a second copy
     * follows: a client misses the first runtime one of its connection. Read the ids back from
     * {@code BlockType.getAssetMap()}. Throws {@link IllegalStateException} when a type did not load.
     */
    public void register(List<BlockType> types, boolean twice) {
        BlockTypeAssetMap<String, BlockType> map = BlockType.getAssetMap();
        int maxIdBefore = map.getNextIndex();
        long start = System.nanoTime();
        BlockType.getAssetStore().loadAssets(packKey, types, AssetUpdateQuery.DEFAULT_NO_REBUILD);
        long micros = (System.nanoTime() - start) / 1_000;
        for (BlockType type : types) {
            int id = map.getIndex(type.getId());
            if (id == Integer.MIN_VALUE) {
                throw new IllegalStateException("BlockType " + type.getId() + " was not loaded");
            }
            LOG.at(Level.FINE).log("hydomum: registered %s as block id %d", type.getId(), id);
        }
        LOG.at(Level.INFO).log(
                "hydomum: UpdateBlockTypes AddOrUpdate of %d type(s), maxId %d -> %d, in %d us",
                types.size(), maxIdBefore, map.getNextIndex(), micros);
        if (twice) {
            try {
                broadcastTypes(types, AssetUpdateQuery.RebuildCache.NO_REBUILD);
            } catch (RuntimeException e) { // optional workaround: the types are already registered and sent once
                LOG.at(Level.SEVERE).withCause(e).log("hydomum: second UpdateBlockTypes failed");
            }
        }
    }

    /**
     * Loads {@code items} into the Item store, which broadcasts one {@code UpdateItems} AddOrUpdate without rebuild
     * flag. Register their blocks first: an item's packet carries its block's id. Throws
     * {@link IllegalStateException} when an item did not load.
     */
    public void registerItems(List<Item> items) {
        Item.getAssetStore().loadAssets(packKey, items, AssetUpdateQuery.DEFAULT_NO_REBUILD);
        for (Item item : items) {
            if (Item.getAssetMap().getAsset(item.getId()) == null) {
                throw new IllegalStateException("Item " + item.getId() + " was not loaded");
            }
        }
        LOG.at(Level.INFO).log("hydomum: UpdateItems AddOrUpdate of %d item(s)", items.size());
    }

    /**
     * Sends connected players the unsent PNGs a registered batch names, then asks them to read them: the batch's
     * {@code types} again with {@code updateBlockTextures} when a texture is new (the client rebuilds its whole
     * atlas), its {@code items} again with {@code updateIcons} when an icon is new. Sends nothing without new PNG, or
     * without player: joining players download them with the required assets. A failure is logged: clients lack the
     * new PNGs until they reconnect.
     */
    public void publish(List<BlockType> types, List<Item> items, VariantAssets.Unsent unsent) {
        List<CommonAsset> textures = unsent.textures();
        List<CommonAsset> icons = unsent.icons();
        if ((textures.isEmpty() && icons.isEmpty()) || Universe.get().getPlayerCount() == 0) {
            return;
        }
        try {
            List<CommonAsset> assets = new ArrayList<>(textures);
            assets.addAll(icons);
            CommonAssetModule.get().sendAssets(assets, false);
            if (!textures.isEmpty()) {
                broadcastTypes(types, BLOCK_TEXTURES);
            }
            if (!icons.isEmpty()) {
                broadcastItemIcons(items);
            }
            LOG.at(Level.INFO).log(
                    "hydomum: published %d texture(s) and %d icon(s), then their rebuild flags",
                    textures.size(), icons.size());
        } catch (RuntimeException e) {
            LOG.at(Level.SEVERE).withCause(e).log("hydomum: new assets not sent, clients need to reconnect");
        }
    }

    /** Broadcasts an {@code UpdateBlockTypes} AddOrUpdate of the registered types with {@code cache}'s flags. */
    private static void broadcastTypes(List<BlockType> types, AssetUpdateQuery.RebuildCache cache) {
        BlockTypeAssetMap<String, BlockType> map = BlockType.getAssetMap();
        Map<Integer, com.hypixel.hytale.protocol.BlockType> packets = new HashMap<>();
        for (BlockType type : types) {
            packets.put(map.getIndex(type.getId()), type.toPacket());
        }
        Universe.get()
                .broadcastPacketNoCache(new UpdateBlockTypes(
                        UpdateType.AddOrUpdate,
                        map.getNextIndex(),
                        packets,
                        cache.isBlockTextures(),
                        cache.isModelTextures(),
                        cache.isModels(),
                        cache.isMapGeometry()));
        LOG.at(Level.INFO).log("hydomum: UpdateBlockTypes sent again for %d type(s), %s", types.size(), cache);
    }

    /**
     * Broadcasts an {@code UpdateItems} AddOrUpdate of {@code items} with {@code updateIcons}: without it, a client
     * ignores a new icon (in game 2026-09-28).
     */
    private static void broadcastItemIcons(List<Item> items) {
        Map<String, ItemBase> packets = new HashMap<>();
        for (Item item : items) {
            packets.put(item.getId(), item.toPacket());
        }
        Universe.get().broadcastPacketNoCache(new UpdateItems(UpdateType.AddOrUpdate, packets, null, false, true));
    }
}
