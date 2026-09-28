package dev.hycolony.plugin.ornament.runtime;

import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.StateData;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.universe.world.connectedblocks.ConnectedBlockRuleSet;
import dev.hycolony.plugin.ornament.api.VariantKey;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.jspecify.annotations.Nullable;

/**
 * Builds a variant's BlockTypes and Item, named after {@link VariantKey#blockTypeKey()}, as copies of its shape's
 * template block (with each of its states) and template item, with the variant's textures and icon. It only builds
 * the objects: {@link BlockTypeSynchronizer} registers them.
 *
 * <p>The block copies keep the template's models, hitboxes, sounds and gathering, so every variant reuses the
 * {@code .blockymodel}s the client already has; its textures are vanilla ones, except a composed shape's generated
 * texture, sent to clients before the blocks.
 */
public final class DynamicBlockTypeFactory {
    /**
     * The variant's main BlockType, then one per template state ({@code *<key>_State_Definitions_<state>}, the key
     * vanilla gives a decoded state), all to register together. A cube + model shape takes the primary texture on its
     * model and the secondary on its cube; a composed shape takes {@code composedTexture} on its model (required
     * then). Throws {@link IllegalStateException} without a template block or the composed texture.
     */
    public List<BlockType> create(VariantKey key, @Nullable String composedTexture) {
        BlockType template =
                template(BlockType.getAssetMap().getAsset(key.shape().templateKey()), key);
        VariantBlockType.Look look = look(key, composedTexture);
        String mainKey = key.blockTypeKey();
        Map<String, String> stateKeys = new LinkedHashMap<>();
        for (String state : stateNames(template)) {
            stateKeys.put(state, "*" + mainKey + "_State_Definitions_" + state);
        }
        VariantBlockType.Family family = new VariantBlockType.Family(
                mainKey,
                stateKeys.isEmpty() ? null : new VariantStateData(stateKeys),
                copy(template.getConnectedBlockRuleSet()));
        List<BlockType> blocks = new ArrayList<>();
        blocks.add(new VariantBlockType(template, mainKey, look, family));
        stateKeys.forEach((state, stateKey) -> blocks.add(
                new VariantBlockType(template(template.getBlockForState(state), key), stateKey, look, family)));
        return blocks;
    }

    /**
     * The variant's Item, placing the variant's block and showing {@code icon} (a common asset path, or none);
     * not yet registered. Throws {@link IllegalStateException} when the template item is missing.
     */
    public Item createItem(VariantKey key, @Nullable String icon) {
        Item template = template(Item.getAssetMap().getAsset(key.shape().templateKey()), key);
        return new VariantItem(template, key, icon);
    }

    private static VariantBlockType.Look look(VariantKey key, @Nullable String composedTexture) {
        if (key.shape().layoutTexture().isEmpty()) {
            return new VariantBlockType.Look(
                    key.primary().texture(), key.secondary().texture());
        }
        if (composedTexture == null) {
            throw new IllegalStateException(key.id() + " needs its composed texture");
        }
        return new VariantBlockType.Look(composedTexture, null);
    }

    private static Set<String> stateNames(BlockType template) {
        StateData states = template.getState();
        Set<String> names = states == null ? null : states.getStateNames();
        return names == null ? Set.of() : names;
    }

    /**
     * A copy of the template's connection rules, by a codec round trip: a rule set caches its own block's state ids
     * ({@code updateCachedBlockTypes}), so a shared instance would point the template at the variant, or back.
     */
    private static @Nullable ConnectedBlockRuleSet copy(@Nullable ConnectedBlockRuleSet rules) {
        if (rules == null) {
            return null;
        }
        BsonValue encoded = ConnectedBlockRuleSet.CODEC.encode(rules, new ExtraInfo());
        forgetResolvedBlocks(encoded);
        return ConnectedBlockRuleSet.CODEC.decode(encoded, new ExtraInfo());
    }

    /**
     * Removes {@code Block} from every output that names a {@code State}: {@code ConnectedBlockOutput.resolve} writes
     * the key it resolved into {@code Block}, so the template's encoded rules name the template's own states (seen in
     * game: a variant's corner turned into the template's). Ornament templates only declare states, never blocks.
     */
    private static void forgetResolvedBlocks(BsonValue value) {
        if (value.isDocument()) {
            BsonDocument doc = value.asDocument();
            if (doc.containsKey("State")) {
                doc.remove("Block");
            }
            doc.values().forEach(DynamicBlockTypeFactory::forgetResolvedBlocks);
        } else if (value.isArray()) {
            value.asArray().forEach(DynamicBlockTypeFactory::forgetResolvedBlocks);
        }
    }

    private static <T> T template(@Nullable T template, VariantKey key) {
        if (template == null) {
            throw new IllegalStateException(
                    "missing ornament template " + key.shape().templateKey());
        }
        return template;
    }

    /**
     * Item's fields are protected too, and the template's {@code data} is dropped for the same reason. The name is
     * the template's. Item's copy constructor skips quality, reticle, durability, fuel, glider, music and container
     * settings: the template item must not use them.
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
