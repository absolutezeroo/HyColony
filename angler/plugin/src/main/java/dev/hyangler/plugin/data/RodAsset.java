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
import org.bson.BsonDocument;
import org.jspecify.annotations.Nullable;

/**
 * One {@code Server/HyAngler/Rods/<item id>.json} file (spec § 7.1): a rod's tier, lure, luck and line. A HyAngler
 * asset type every pack may add to; a pack loaded later replaces a file of the same name.
 */
public final class RodAsset implements JsonAssetWithMap<String, DefaultAssetMap<String, RodAsset>> {
    /** Where the files live under each pack's {@code Server/}. */
    static final String PATH = "HyAngler/Rods";

    static final AssetBuilderCodec<String, RodAsset> CODEC = AssetBuilderCodec.builder(
                    RodAsset.class,
                    RodAsset::new,
                    Codec.STRING,
                    (a, id) -> a.id = id,
                    a -> a.id,
                    (a, data) -> a.data = data,
                    a -> a.data)
            .append(new KeyedCodec<>("Tier", Codec.INTEGER), (a, v) -> a.tier = v, a -> a.tier)
            .add()
            .append(new KeyedCodec<>("Lure", Codec.INTEGER), (a, v) -> a.lure = v, a -> a.lure)
            .add()
            .append(new KeyedCodec<>("Luck", Codec.INTEGER), (a, v) -> a.luck = v, a -> a.luck)
            .add()
            .append(new KeyedCodec<>("MaxLine", Codec.INTEGER), (a, v) -> a.maxLine = v, a -> a.maxLine)
            .add()
            .build();

    private AssetExtraInfo.@Nullable Data data;
    private String id = "";
    private @Nullable Integer tier;
    private @Nullable Integer lure;
    private @Nullable Integer luck;
    private @Nullable Integer maxLine;

    private RodAsset() {}

    /** Registers the type, loaded after the items it names (as Hytale's ShopPlugin.setup). Call once, in setup(). */
    public static void register(com.hypixel.hytale.server.core.plugin.registry.AssetRegistry registry) {
        registry.register(HytaleAssetStore.builder(RodAsset.class, new DefaultAssetMap<String, RodAsset>())
                .setPath(PATH)
                .setCodec(CODEC)
                .setKeyFunction(RodAsset::getId)
                .loadsAfter(Item.class)
                .build());
    }

    /** Every rod file read, by item id; empty before the assets load or without the type registered. */
    public static Map<String, RodAsset> all() {
        AssetStore<String, RodAsset, DefaultAssetMap<String, RodAsset>> store =
                AssetRegistry.getAssetStore(RodAsset.class);
        return store == null ? Map.of() : store.getAssetMap().getAssetMap();
    }

    @Override
    public String getId() {
        return id;
    }

    /** The file as JSON for the core, with only the keys it had. */
    String json() {
        BsonDocument doc = new BsonDocument();
        BsonFields.put(doc, "Tier", tier);
        BsonFields.put(doc, "Lure", lure);
        BsonFields.put(doc, "Luck", luck);
        BsonFields.put(doc, "MaxLine", maxLine);
        return doc.toJson();
    }
}
