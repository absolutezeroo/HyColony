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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.jspecify.annotations.Nullable;

/**
 * One {@code Server/HyAngler/Catches/<item id>.json} file (spec § 6.2): junk or treasure one can catch, its weight,
 * count and rules. A HyAngler asset type every pack may add to; a pack loaded later replaces a file of the same name.
 * Conditions and modifiers are kept as raw BSON of any shape (RawBsonCodec): the core judges them, so a malformed rule
 * never stops the server; only a type error in a typed key does, as for any Hytale asset (spec § 6).
 */
public final class CatchAsset implements JsonAssetWithMap<String, DefaultAssetMap<String, CatchAsset>> {
    /** Where the files live under each pack's {@code Server/}. */
    static final String PATH = "HyAngler/Catches";

    static final AssetBuilderCodec<String, CatchAsset> CODEC = AssetBuilderCodec.builder(
                    CatchAsset.class,
                    CatchAsset::new,
                    Codec.STRING,
                    (a, id) -> a.id = id,
                    a -> a.id,
                    (a, data) -> a.data = data,
                    a -> a.data)
            .append(new KeyedCodec<>("Category", Codec.STRING), (a, v) -> a.category = v, a -> a.category)
            .add()
            .append(new KeyedCodec<>("Weight", Codec.INTEGER), (a, v) -> a.weight = v, a -> a.weight)
            .add()
            .append(new KeyedCodec<>("Quality", Codec.INTEGER), (a, v) -> a.quality = v, a -> a.quality)
            .add()
            .append(new KeyedCodec<>("Count", Codec.INT_ARRAY), (a, v) -> a.count = v, a -> a.count)
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
    private @Nullable String category;
    private @Nullable Integer weight;
    private @Nullable Integer quality;
    private int @Nullable [] count;
    private @Nullable BsonValue conditions;
    private @Nullable BsonValue modifiers;

    private CatchAsset() {}

    /** Registers the type, loaded after the items it names (as Hytale's ShopPlugin.setup). Call once, in setup(). */
    public static void register(com.hypixel.hytale.server.core.plugin.registry.AssetRegistry registry) {
        registry.register(HytaleAssetStore.builder(CatchAsset.class, new DefaultAssetMap<String, CatchAsset>())
                .setPath(PATH)
                .setCodec(CODEC)
                .setKeyFunction(CatchAsset::getId)
                .loadsAfter(Item.class)
                .build());
    }

    /** Every catch file read, by item id; empty before the assets load or without the type registered. */
    public static Map<String, CatchAsset> all() {
        AssetStore<String, CatchAsset, DefaultAssetMap<String, CatchAsset>> store =
                AssetRegistry.getAssetStore(CatchAsset.class);
        return store == null ? Map.of() : store.getAssetMap().getAssetMap();
    }

    @Override
    public String getId() {
        return id;
    }

    /** The file as JSON for the core, with only the keys it had. */
    String json() {
        BsonDocument doc = new BsonDocument();
        BsonFields.put(doc, "Category", category == null ? null : new BsonString(category));
        BsonFields.put(doc, "Weight", weight);
        BsonFields.put(doc, "Quality", quality);
        BsonFields.put(doc, "Count", count == null ? null : ints(count));
        BsonFields.put(doc, "Conditions", conditions);
        BsonFields.put(doc, "Modifiers", modifiers);
        return doc.toJson();
    }

    /** The values as a BSON array of ints. */
    private static BsonArray ints(int[] values) {
        List<BsonValue> out = new ArrayList<>(values.length);
        for (int v : values) {
            out.add(new BsonInt32(v));
        }
        return new BsonArray(out);
    }
}
