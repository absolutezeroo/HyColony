package dev.hycolony.plugin.crafting;

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
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * One {@code Server/HyColony/JobTags/<name>.json} file (spec 2026-10-04 § 6.1): items added to one of MC's job tags
 * ({@link dev.hycolony.core.crafting.recipe.JobTags}). Every pack may add files; the files of a tag add up, the file
 * name only keeps them apart (a pack replaces one of ours by reusing its name).
 */
public final class JobTagAsset implements JsonAssetWithMap<String, DefaultAssetMap<String, JobTagAsset>> {
    /** Where the files live under each pack's {@code Server/}. */
    static final String PATH = "HyColony/JobTags";

    static final AssetBuilderCodec<String, JobTagAsset> CODEC = AssetBuilderCodec.builder(
                    JobTagAsset.class,
                    JobTagAsset::new,
                    Codec.STRING,
                    (a, id) -> a.id = id,
                    a -> a.id,
                    (a, data) -> a.data = data,
                    a -> a.data)
            .append(new KeyedCodec<>("Tag", Codec.STRING), (a, v) -> a.tag = v, a -> a.tag)
            .add()
            .append(new KeyedCodec<>("Values", Codec.STRING_ARRAY), (a, v) -> a.values = v, a -> a.values)
            .add()
            .build();

    private AssetExtraInfo.@Nullable Data data;
    private String id = "";
    private String tag = "";
    private String[] values = new String[0];

    private JobTagAsset() {}

    /** Registers the type, loaded after the items it names (as Hytale's ShopPlugin.setup). Call once, in setup(). */
    public static void register(com.hypixel.hytale.server.core.plugin.registry.AssetRegistry registry) {
        registry.register(HytaleAssetStore.builder(JobTagAsset.class, new DefaultAssetMap<String, JobTagAsset>())
                .setPath(PATH)
                .setCodec(CODEC)
                .setKeyFunction(JobTagAsset::getId)
                .loadsAfter(Item.class)
                .build());
    }

    /** Every job tag file read, by file name; empty before the assets load or without the type registered. */
    public static Map<String, JobTagAsset> all() {
        AssetStore<String, JobTagAsset, DefaultAssetMap<String, JobTagAsset>> store =
                AssetRegistry.getAssetStore(JobTagAsset.class);
        return store == null ? Map.of() : store.getAssetMap().getAssetMap();
    }

    @Override
    public String getId() {
        return id;
    }

    /** The tag the file adds to; empty when the file has none (skipped as unknown). */
    String tag() {
        return tag;
    }

    /** Its values: item ids, or {@code res:<resource type>}. */
    List<String> values() {
        return List.of(values);
    }
}
