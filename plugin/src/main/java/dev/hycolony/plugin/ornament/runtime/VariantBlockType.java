package dev.hycolony.plugin.ornament.runtime;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockTypeTextures;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.CustomModelTexture;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.StateData;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.universe.world.connectedblocks.ConnectedBlockRuleSet;
import org.jspecify.annotations.Nullable;

/**
 * One BlockType of a variant, its main block or one of its states: a copy of the matching template block with the
 * variant's textures, state table and connection rules.
 *
 * <p>BlockType's fields are protected with no setters: a subclass is the only way to set them without decoding
 * JSON. {@code data} is dropped: kept, it would make every load re-read the template's contained assets under our
 * pack and name the template's item in the packet (docs/research/plugin-b-api.md § 17). Without {@code data},
 * vanilla finds neither the item nor the default state, so both are answered here from the family's main key.
 */
final class VariantBlockType extends BlockType {
    /** The variant's textures: model always, cube only for a cube + model shape. */
    record Look(String modelTexture, @Nullable String cubeTexture) {}

    /**
     * What a variant's blocks share: the main block's key (its item and default state), the state table and one copy
     * of the connection rules, which cache the family's block ids (ConnectedBlocksModule.onBlockTypesChanged).
     */
    record Family(
            String mainKey,
            @Nullable StateData states,
            @Nullable ConnectedBlockRuleSet rules) {}

    private final String mainKey;

    VariantBlockType(BlockType template, String id, Look look, Family family) {
        super(template);
        this.data = null;
        this.id = id;
        this.mainKey = family.mainKey();
        this.customModelTexture = new CustomModelTexture[] {new CustomModelTexture(look.modelTexture(), 1)};
        if (look.cubeTexture() != null) {
            this.textures = new BlockTypeTextures[] {new BlockTypeTextures(look.cubeTexture())};
        }
        this.state = family.states();
        this.connectedBlockRuleSet = family.rules();
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
