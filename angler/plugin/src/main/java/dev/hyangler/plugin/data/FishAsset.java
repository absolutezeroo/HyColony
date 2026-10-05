package dev.hyangler.plugin.data;

import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.assetstore.AssetRegistry;
import com.hypixel.hytale.assetstore.AssetStore;
import com.hypixel.hytale.assetstore.codec.AssetBuilderCodec;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.assetstore.map.JsonAssetWithMap;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.server.core.asset.HytaleAssetStore;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import java.util.Map;
import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.jspecify.annotations.Nullable;

/**
 * One {@code Server/HyAngler/Fish/<item id>.json} file (spec § 6.1): a fish one can catch, its weight and its rules.
 * A HyAngler asset type every pack may add to; a pack loaded later replaces a file of the same name. Conditions and
 * modifiers are kept as raw BSON of any shape (RawBsonCodec): the core judges them, so a malformed rule never stops
 * the server; only a type error in a typed key does, as for any Hytale asset (spec § 6).
 */
public final class FishAsset implements JsonAssetWithMap<String, DefaultAssetMap<String, FishAsset>> {
    /** Where the files live under each pack's {@code Server/}. */
    static final String PATH = "HyAngler/Fish";

    static final AssetBuilderCodec<String, FishAsset> CODEC = AssetBuilderCodec.builder(
                    FishAsset.class,
                    FishAsset::new,
                    Codec.STRING,
                    (a, id) -> a.id = id,
                    a -> a.id,
                    (a, data) -> a.data = data,
                    a -> a.data)
            .append(new KeyedCodec<>("Weight", Codec.INTEGER), (a, v) -> a.weight = v, a -> a.weight)
            .add()
            .append(new KeyedCodec<>("Quality", Codec.INTEGER), (a, v) -> a.quality = v, a -> a.quality)
            .add()
            .append(new KeyedCodec<>("Rarities", Codec.BOOLEAN), (a, v) -> a.rarities = v, a -> a.rarities)
            .add()
            .append(
                    new KeyedCodec<>("Conditions", RawBsonCodec.INSTANCE),
                    (a, v) -> a.conditions = v,
                    a -> a.conditions)
            .add()
            .append(new KeyedCodec<>("Modifiers", RawBsonCodec.INSTANCE), (a, v) -> a.modifiers = v, a -> a.modifiers)
            .add()
            .build();

    private AssetExtraInfo.@Nullable Data data;
    private String id = "";
    private @Nullable Integer weight;
    private @Nullable Integer quality;
    private @Nullable Boolean rarities;
    private @Nullable BsonValue conditions;
    private @Nullable BsonValue modifiers;

    private FishAsset() {}

    /** Registers the type, loaded after the items it names (as Hytale's ShopPlugin.setup). Call once, in setup(). */
    public static void register(com.hypixel.hytale.server.core.plugin.registry.AssetRegistry registry) {
        registry.register(HytaleAssetStore.builder(FishAsset.class, new DefaultAssetMap<String, FishAsset>())
                .setPath(PATH)
                .setCodec(CODEC)
                .setKeyFunction(FishAsset::getId)
                .loadsAfter(Item.class)
                .build());
    }

    /** Every fish file read, by item id; empty before the assets load or without the type registered. */
    public static Map<String, FishAsset> all() {
        AssetStore<String, FishAsset, DefaultAssetMap<String, FishAsset>> store =
                AssetRegistry.getAssetStore(FishAsset.class);
        return store == null ? Map.of() : store.getAssetMap().getAssetMap();
    }

    @Override
    public String getId() {
        return id;
    }

    /** The file as JSON for the core, with only the keys it had. */
    String json() {
        BsonDocument doc = new BsonDocument();
        BsonFields.put(doc, "Weight", weight);
        BsonFields.put(doc, "Quality", quality);
        BsonFields.put(doc, "Rarities", rarities == null ? null : BsonBoolean.valueOf(rarities));
        BsonFields.put(doc, "Conditions", conditions);
        BsonFields.put(doc, "Modifiers", modifiers);
        return doc.toJson();
    }
}
