package dev.hycolony.plugin.ornament.runtime;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockTypeTextures;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.CustomModelTexture;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import dev.hycolony.plugin.ornament.api.VariantKey;
import org.jspecify.annotations.Nullable;

/**
 * Builds a variant's BlockType and Item, both named {@link VariantKey#blockTypeKey()}, as copies of its shape's
 * template block and template item with the variant's textures and icon. It only builds the objects:
 * {@link BlockTypeSynchronizer} registers them.
 *
 * <p>The block copy keeps the template's model, hitbox, sounds and gathering, so every variant reuses the one
 * {@code .blockymodel} the client already has; its textures are vanilla ones, except a composed shape's generated
 * texture, sent to clients before the block.
 */
public final class DynamicBlockTypeFactory {
    /**
     * The variant's BlockType, not yet registered: a cube + model shape takes the primary texture on its model and
     * the secondary on its cube; a composed shape takes {@code composedTexture} on its model (required then). Throws
     * {@link IllegalStateException} without the template or the composed texture.
     */
    public BlockType create(VariantKey key, @Nullable String composedTexture) {
        BlockType template =
                template(BlockType.getAssetMap().getAsset(key.shape().templateKey()), key);
        if (key.shape().layoutTexture().isEmpty()) {
            return new VariantBlockType(
                    template, key, key.primary().texture(), key.secondary().texture());
        }
        if (composedTexture == null) {
            throw new IllegalStateException(key.id() + " needs its composed texture");
        }
        return new VariantBlockType(template, key, composedTexture, null);
    }

    /**
     * The variant's Item, placing the variant's block and showing {@code icon} (a common asset path, or none);
     * not yet registered. Throws {@link IllegalStateException} when the template item is missing.
     */
    public Item createItem(VariantKey key, @Nullable String icon) {
        Item template = template(Item.getAssetMap().getAsset(key.shape().templateKey()), key);
        return new VariantItem(template, key, icon);
    }

    private static <T> T template(@Nullable T template, VariantKey key) {
        if (template == null) {
            throw new IllegalStateException(
                    "missing ornament template " + key.shape().templateKey());
        }
        return template;
    }

    /**
     * BlockType's fields are protected with no setters: a subclass is the only way to set them without decoding
     * JSON. {@code data} is dropped: kept, it would make every load re-read the template's contained assets under
     * our pack and name the template's item in the packet (docs/research/plugin-b-api.md § 17). Without
     * {@code data}, vanilla finds no item, so {@link #getItem} names the variant's own.
     */
    private static final class VariantBlockType extends BlockType {
        /** @param cubeTexture the cube's texture, or null to keep the template's (a composed shape has no cube) */
        VariantBlockType(BlockType template, VariantKey key, String modelTexture, @Nullable String cubeTexture) {
            super(template);
            this.data = null;
            this.id = key.blockTypeKey();
            this.customModelTexture = new CustomModelTexture[] {new CustomModelTexture(modelTexture, 1)};
            if (cubeTexture != null) {
                this.textures = new BlockTypeTextures[] {new BlockTypeTextures(cubeTexture)};
            }
            this.state = null;
            this.connectedBlockRuleSet = null;
        }

        /** The variant's item (same key), which breaking the block drops; null until it is registered. */
        @Override
        public @Nullable Item getItem() {
            return Item.getAssetMap().getAsset(id);
        }

        /**
         * The packet names the item even when it was built before the item was registered (the packet is cached
         * and the block must be registered first, since the item's packet needs the block id).
         */
        @Override
        public com.hypixel.hytale.protocol.BlockType toPacket() {
            com.hypixel.hytale.protocol.BlockType packet = super.toPacket();
            packet.item = id;
            return packet;
        }
    }

    /**
     * Same reason as {@link VariantBlockType}: protected fields, and the template's {@code data} dropped. The name
     * is the template's. Item's copy constructor skips quality, reticle, durability, fuel, glider, music and
     * container settings: the template item must not use them.
     */
    private static final class VariantItem extends Item {
        VariantItem(Item template, VariantKey key, @Nullable String icon) {
            super(template);
            this.data = null;
            this.id = key.blockTypeKey();
            this.blockId = key.blockTypeKey();
            this.icon = icon;
        }
    }
}
