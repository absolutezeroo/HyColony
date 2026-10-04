package dev.hycolony.plugin.food;

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
import org.jspecify.annotations.Nullable;

/**
 * One {@code Server/HyColony/Foods/<item id>.json} file (spec 2026-10-04 § 5.1): what eating that item gives a citizen
 * (MC FoodProperties nutrition, IMinecoloniesFoodItem tier, {@code poisonousfood} tag). A HyColony asset type that
 * every pack may add to; a pack loaded later replaces a file of the same name.
 */
public final class FoodValueAsset implements JsonAssetWithMap<String, DefaultAssetMap<String, FoodValueAsset>> {
    /** Where the files live under each pack's {@code Server/}. */
    static final String PATH = "HyColony/Foods";

    static final AssetBuilderCodec<String, FoodValueAsset> CODEC = AssetBuilderCodec.builder(
                    FoodValueAsset.class,
                    FoodValueAsset::new,
                    Codec.STRING,
                    (a, id) -> a.id = id,
                    a -> a.id,
                    (a, data) -> a.data = data,
                    a -> a.data)
            .append(new KeyedCodec<>("Nutrition", Codec.INTEGER), (a, v) -> a.nutrition = v, a -> a.nutrition)
            .add()
            .append(new KeyedCodec<>("Tier", Codec.INTEGER), (a, v) -> a.tier = v, a -> a.tier)
            .add()
            .append(new KeyedCodec<>("Poisonous", Codec.BOOLEAN), (a, v) -> a.poisonous = v, a -> a.poisonous)
            .add()
            .build();

    private AssetExtraInfo.@Nullable Data data;
    private String id = "";
    private int nutrition;
    private int tier;
    private boolean poisonous;

    private FoodValueAsset() {}

    /** Registers the type, loaded after the items it names (as Hytale's ShopPlugin.setup). Call once, in setup(). */
    public static void register(com.hypixel.hytale.server.core.plugin.registry.AssetRegistry registry) {
        registry.register(HytaleAssetStore.builder(FoodValueAsset.class, new DefaultAssetMap<String, FoodValueAsset>())
                .setPath(PATH)
                .setCodec(CODEC)
                .setKeyFunction(FoodValueAsset::getId)
                .loadsAfter(Item.class)
                .build());
    }

    /** Every food file read, by item id; empty before the assets load or without the type registered. */
    public static Map<String, FoodValueAsset> all() {
        AssetStore<String, FoodValueAsset, DefaultAssetMap<String, FoodValueAsset>> store =
                AssetRegistry.getAssetStore(FoodValueAsset.class);
        return store == null ? Map.of() : store.getAssetMap().getAssetMap();
    }

    @Override
    public String getId() {
        return id;
    }

    int nutrition() {
        return nutrition;
    }

    int tier() {
        return tier;
    }

    boolean poisonous() {
        return poisonous;
    }
}
