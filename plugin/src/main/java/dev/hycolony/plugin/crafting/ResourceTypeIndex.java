package dev.hycolony.plugin.crafting;

import com.hypixel.hytale.protocol.ItemResourceType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemQuality;
import dev.hycolony.core.kernel.item.ItemKey;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

/**
 * Every item by the Hytale resource types it lists ({@code Item.getResourceTypes}), except Hytale's test items,
 * each list sorted by item id so a choice among them is stable. Every resource type of 0.6.8 counts 1 per item, so a
 * count of items is a count of the resource (plugin-b-api § « Recettes et tables »).
 */
public final class ResourceTypeIndex {
    /** The item qualities Hytale keeps out of players' hands (as its BlockColorIndex.EXCLUDED_QUALITIES). */
    private static final Set<String> HIDDEN_QUALITIES = Set.of("Developer", "Technical", "Debug", "Template");

    /**
     * The id prefix of Hytale's debug items: Debug_MusicEmitter_* set no Quality (not inherited from Parent) and
     * inherit a wood trunk's resource types (Fuel among them).
     */
    private static final String DEBUG_PREFIX = "Debug_";

    private final Map<String, List<ItemKey>> items;

    private ResourceTypeIndex(Map<String, List<ItemKey>> items) {
        this.items = items;
    }

    /** Reads the item asset map once. */
    public static ResourceTypeIndex load() {
        Map<String, List<ItemKey>> out = new TreeMap<>();
        Set<Integer> hidden = hiddenQualities();
        SkippedAssets skipped = new SkippedAssets("item");
        for (Item item : Item.getAssetMap().getAssetMap().values()) {
            try {
                add(out, item, hidden);
            } catch (RuntimeException e) {
                skipped.skip(item == null ? "?" : item.getId(), e);
            }
        }
        out.replaceAll((_, list) ->
                list.stream().sorted(Comparator.comparing(ItemKey::id)).toList());
        return new ResourceTypeIndex(out);
    }

    /** The indexes of {@link #HIDDEN_QUALITIES} that the quality asset map knows. */
    private static Set<Integer> hiddenQualities() {
        Set<Integer> out = new HashSet<>();
        for (String id : HIDDEN_QUALITIES) {
            int index = ItemQuality.getAssetMap().getIndexOrDefault(id, -1);
            if (index >= 0) {
                out.add(index);
            }
        }
        return out;
    }

    /**
     * Files {@code item} under each resource type it lists; an item without id, of a hidden quality or with the debug
     * id prefix (Hytale's prototypes, debug items and templates) is ignored.
     */
    private static void add(Map<String, List<ItemKey>> out, @Nullable Item item, Set<Integer> hidden) {
        if (item == null
                || item.getId() == null
                || item.getResourceTypes() == null
                || hidden.contains(item.getQualityIndex())
                || item.getId().startsWith(DEBUG_PREFIX)) {
            return;
        }
        ItemKey key = new ItemKey(item.getId());
        for (ItemResourceType t : item.getResourceTypes()) {
            if (t != null && t.id != null) {
                out.computeIfAbsent(t.id, _ -> new ArrayList<>()).add(key);
            }
        }
    }

    /** The items of {@code resourceTypeId}, sorted by id; empty if none. */
    public List<ItemKey> items(String resourceTypeId) {
        return items.getOrDefault(resourceTypeId, List.of());
    }
}
