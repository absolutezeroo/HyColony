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
 * {@code .blockymodel} the client already has, and only textures and icons already known to the client.
 */
public final class DynamicBlockTypeFactory {
    /** The variant's BlockType, not yet registered; throws {@link IllegalStateException} without the template. */
    public BlockType create(VariantKey key) {
        return new VariantBlockType(
                template(BlockType.getAssetMap().getAsset(key.shape().templateKey()), key), key);
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
        VariantBlockType(BlockType template, VariantKey key) {
            super(template);
            this.data = null;
            this.id = key.blockTypeKey();
            this.customModelTexture = new CustomModelTexture[] {
                new CustomModelTexture(key.primary().texture(), 1)
            };
            this.textures = new BlockTypeTextures[] {
                new BlockTypeTextures(key.secondary().texture())
            };
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
