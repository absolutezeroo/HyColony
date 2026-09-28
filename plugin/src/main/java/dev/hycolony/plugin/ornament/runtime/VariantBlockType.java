package dev.hycolony.plugin.ornament.runtime;

import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockGathering;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.CustomModelTexture;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.StateData;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.universe.world.connectedblocks.ConnectedBlockRuleSet;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.jspecify.annotations.Nullable;

/**
 * One BlockType of a variant, its main block or one of its states: a copy of the matching template block with the
 * variant's layout texture, state table and connection rules.
 *
 * <p>BlockType's fields are protected with no setters: a subclass is the only way to set them without decoding
 * JSON. {@code data} is dropped: kept, it would make every load re-read the template's contained assets under our
 * pack and name the template's item in the packet (docs/research/plugin-b-api.md § 17). Without {@code data},
 * vanilla finds neither the item nor the default state, so both are answered here from the family's main key.
 */
final class VariantBlockType extends BlockType {
    /**
     * What a variant's blocks share: the main block's key (its item and default state), the state table, one copy
     * of the connection rules, which cache the family's block ids (ConnectedBlocksModule.onBlockTypesChanged), and
     * the template's key, which a copied state's gathering may name.
     */
    record Family(
            String mainKey,
            @Nullable StateData states,
            @Nullable ConnectedBlockRuleSet rules,
            String templateKey) {}

    private final String mainKey;
    /** The template's asset data, answered by {@link #getData()} only; the {@code data} field stays null. */
    private final AssetExtraInfo.@Nullable Data templateData;

    VariantBlockType(BlockType template, String id, String modelTexture, Family family) {
        super(template);
        this.templateData = template.getData();
        this.data = null;
        this.id = id;
        this.mainKey = family.mainKey();
        this.customModelTexture = new CustomModelTexture[] {new CustomModelTexture(modelTexture, 1)};
        this.state = family.states();
        this.connectedBlockRuleSet = family.rules();
        this.gathering = gathering(template.getGathering(), family);
    }

    /**
     * template's gathering with the item it gives back renamed from the template's to the variant's (a wall corner
     * or a double slab names its template item, which a variant must not give); the template's own when it names
     * no item. A codec round trip: the fields are protected.
     */
    private static @Nullable BlockGathering gathering(@Nullable BlockGathering template, Family family) {
        if (template == null) {
            return null;
        }
        BsonValue encoded = BlockGathering.CODEC.encode(template, new ExtraInfo());
        if (!(encoded instanceof BsonDocument gathering)
                || !(gathering.get("Breaking") instanceof BsonDocument breaking)
                || !(breaking.get("ItemId") instanceof BsonString item)
                || !item.getValue().equals(family.templateKey())) {
            return template;
        }
        breaking.put("ItemId", new BsonString(family.mainKey()));
        return BlockGathering.CODEC.decode(gathering, new ExtraInfo());
    }

    /**
     * The template's asset data, for the readers that only test or read its tags: vanilla switches a block's
     * interaction state (a door opening) only when {@code getData()} is not null (BlockOperations.
     * setBlockInteractionState, WorldChunk.setBlockInteractionState), so without it a created door played its sound
     * and stayed shut; connection and condition checks read the template's tags. The asset store, the item and the
     * packet read the {@code data} field (BlockType.CODEC, getItem, toPacket), which stays null.
     */
    @Override
    public AssetExtraInfo.@Nullable Data getData() {
        return templateData;
    }

    /** The variant's item (the main block's key), which breaking any of its blocks drops; null until registered. */
    @Override
    public @Nullable Item getItem() {
        return Item.getAssetMap().getAsset(mainKey);
    }

    /** The main block, which vanilla reads from {@code data} (null here); none for a variant without states. */
    @Override
    public @Nullable String getDefaultStateKey() {
        return state == null ? null : mainKey;
    }

    /**
     * The packet names the item even when it was built before the item was registered (the packet is cached and
     * the block must be registered first, since the item's packet needs the block id). Its connection rules are
     * rebuilt too: the first packet is built before {@code LoadedAssetsEvent} resolves them (AssetStore.loadAssets0),
     * so the cached one would hold -1 ids.
     */
    @Override
    public com.hypixel.hytale.protocol.BlockType toPacket() {
        com.hypixel.hytale.protocol.BlockType packet = super.toPacket();
        packet.item = mainKey;
        if (connectedBlockRuleSet != null) {
            packet.connectedBlockRuleSet = connectedBlockRuleSet.toPacket(getAssetMap());
        }
        return packet;
    }
}
