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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * Registers runtime BlockTypes and Items in Hytale's stores and sends connected players a batch's new files: its
 * models first, since no rebuild flag makes a client reread a model that arrives after the block naming it; its icons
 * last, with the Items again and {@code updateIcons}. No packet carries {@code updateBlockTextures}: variants read
 * textures clients already hold (VariantPalette), so no client atlas rebuild, hence no flicker.
 *
 * <p>Must not run on a world thread: {@code World.tick} holds the read lock of {@code AssetRegistry.ASSET_LOCK} and
 * {@code AssetStore.loadAssets} takes its write lock, which deadlocks (docs/research/plugin-b-api.md § 17).
 */
public final class BlockTypeSynchronizer {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
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
     * Sends connected players a batch's new models, before its blocks are registered. Sends nothing without model or
     * without player: joining players download them with the required assets. A failure is logged: clients lack the
     * models until they reconnect.
     */
    public void sendModels(List<CommonAsset> models) {
        if (models.isEmpty() || Universe.get().getPlayerCount() == 0) {
            return;
        }
        try {
            CommonAssetModule.get().sendAssets(models, false);
            LOG.at(Level.INFO).log("hydomum: sent %d new model(s)", models.size());
        } catch (RuntimeException e) {
            LOG.at(Level.SEVERE).withCause(e).log("hydomum: new models not sent, clients need to reconnect");
        }
    }

    /**
     * Sends connected players a registered batch's new icons, then its {@code items} again with {@code updateIcons}
     * (without it, a client ignores a new icon; in game 2026-09-28). Sends nothing without icon or without player;
     * a failure is logged: clients lack the icons until they reconnect.
     */
    public void publishIcons(List<Item> items, List<CommonAsset> icons) {
        if (icons.isEmpty() || Universe.get().getPlayerCount() == 0) {
            return;
        }
        try {
            CommonAssetModule.get().sendAssets(icons, false);
            broadcastItemIcons(items);
            LOG.at(Level.INFO).log("hydomum: published %d icon(s), then updateIcons", icons.size());
        } catch (RuntimeException e) {
            LOG.at(Level.SEVERE).withCause(e).log("hydomum: new icons not sent, clients need to reconnect");
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
