package dev.hycolony.plugin.ornament.runtime;

import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.StateData;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.universe.world.connectedblocks.ConnectedBlockRuleSet;
import dev.hycolony.core.ornament.VariantKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.jspecify.annotations.Nullable;

/**
 * Builds a variant's BlockTypes and Item, named after {@link VariantKey#blockTypeKey()}, as copies of its shape's
 * template block (with each of its states) and template item, with the variant's layout texture and icon. It only
 * builds the objects: {@link BlockTypeSynchronizer} registers them.
 *
 * <p>The block copies keep the template's models, hitboxes, sounds and gathering, so every variant reuses the
 * {@code .blockymodel}s the client already has; only the texture its models read changes (MC DO retextures the same
 * model, {@code MateriallyTexturedBakedModel}), and the item a state gives back becomes the variant's.
 *
 * <p>Deviation from MC: a variant sounds and breaks like its template (its shape's default material): DO takes
 * them from the chosen material.
 */
public final class DynamicBlockTypeFactory {
    private static final String PATTERNS = "TemplateShapeBlockPatterns";
    private static final String STATE = "_State_Definitions_";

    /**
     * The variant's main BlockType, then one per template state ({@code *<key>_State_Definitions_<state>}, the key
     * vanilla gives a decoded state), all to register together, every model reading modelTexture. Throws
     * {@link IllegalStateException} without a template block.
     */
    public List<BlockType> create(VariantKey key, String modelTexture) {
        String templateKey = key.shape().templateKey();
        BlockType template = template(BlockType.getAssetMap().getAsset(templateKey), key);
        String mainKey = key.blockTypeKey();
        Map<String, String> stateKeys = new LinkedHashMap<>();
        for (String state : stateNames(template)) {
            stateKeys.put(state, "*" + mainKey + STATE + state);
        }
        VariantBlockType.Family family = new VariantBlockType.Family(
                mainKey,
                stateKeys.isEmpty() ? null : new VariantStateData(stateKeys),
                copy(template.getConnectedBlockRuleSet(), templateKey, mainKey),
                templateKey);
        List<BlockType> blocks = new ArrayList<>();
        blocks.add(new VariantBlockType(template, mainKey, modelTexture, family));
        stateKeys.forEach((state, stateKey) -> blocks.add(
                new VariantBlockType(template(template.getBlockForState(state), key), stateKey, modelTexture, family)));
        return blocks;
    }

    /**
     * The variant's Item, placing the variant's block and showing {@code icon} (a common asset path; the template's
     * icon when null); not yet registered. Throws {@link IllegalStateException} when the template item is missing.
     */
    public Item createItem(VariantKey key, @Nullable String icon) {
        Item template = template(Item.getAssetMap().getAsset(key.shape().templateKey()), key);
        return new VariantItem(template, key, icon);
    }

    private static Set<String> stateNames(BlockType template) {
        StateData states = template.getState();
        Set<String> names = states == null ? null : states.getStateNames();
        return names == null ? Set.of() : names;
    }

    /**
     * A copy of the template's connection rules, by a codec round trip: a rule set caches its own block's state ids
     * ({@code updateCachedBlockTypes}), so a shared instance would point the template at the variant, or back. The
     * template's own keys in a connection template's patterns become the variant's (its main block and its states);
     * other blocks (a fence's gate pattern names the template gate), the template shape, face tags and material
     * name stay shared, so variants of one shape join across materials as in DO.
     */
    private static @Nullable ConnectedBlockRuleSet copy(
            @Nullable ConnectedBlockRuleSet rules, String templateKey, String mainKey) {
        if (rules == null) {
            return null;
        }
        BsonValue encoded = ConnectedBlockRuleSet.CODEC.encode(rules, new ExtraInfo());
        forgetResolvedBlocks(encoded);
        if (encoded.isDocument() && encoded.asDocument().get(PATTERNS) instanceof BsonDocument patterns) {
            patterns.replaceAll((shape, targets) -> new BsonString(renamed(targets, templateKey, mainKey)));
        }
        return ConnectedBlockRuleSet.CODEC.decode(encoded, new ExtraInfo());
    }

    /**
     * targets (comma-separated block keys, each maybe weighted as {@code BlockPattern} encodes several:
     * {@code 100.0%key}) with templateKey and its states renamed to mainKey's, weights kept.
     */
    private static String renamed(BsonValue targets, String templateKey, String mainKey) {
        String state = "*" + templateKey + STATE;
        return Arrays.stream(targets.asString().getValue().split(","))
                .map(target -> {
                    int weight = target.lastIndexOf('%') + 1;
                    String block = target.substring(weight);
                    String renamed = block.equals(templateKey)
                            ? mainKey
                            : block.startsWith(state) ? "*" + mainKey + STATE + block.substring(state.length()) : block;
                    return target.substring(0, weight) + renamed;
                })
                .collect(Collectors.joining(","));
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
     * Item's fields are protected too, and the template's {@code data} is dropped for the same reason. Item's copy
     * constructor skips quality, reticle, durability, fuel, glider, music and container settings: the template item
     * must not use them. Interactions defined inside the template item are dropped: they name the template's block.
     *
     * <p>Deviation from MC: the name is the template's (the shape's), without the materials DO shows: a Hytale item
     * name takes no parameter.
     */
    private static final class VariantItem extends Item {
        VariantItem(Item template, VariantKey key, @Nullable String icon) {
            super(template);
            this.data = null;
            this.id = key.blockTypeKey();
            this.blockId = key.blockTypeKey();
            if (icon != null) {
                this.icon = icon;
            }
            // An interaction written inside the template item (a slab's merge into a full block) is a contained asset
            // ("*<template>_..."): it matches the template's block and places the template's state, so a variant
            // would merge into, and turn into, the template. Named vanilla interactions (Block_Secondary) are kept.
            String own = "*" + template.getId();
            this.interactions = template.getInteractions().entrySet().stream()
                    .filter(e -> !e.getValue().startsWith(own))
                    .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
        }
    }
}
