package dev.hycolony.plugin.crafting;

import com.hypixel.hytale.protocol.ItemResourceType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import dev.hycolony.core.kernel.item.ItemKey;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

/**
 * Every item by the Hytale resource types it lists ({@code Item.getResourceTypes}), each list sorted by item id so a
 * choice among them is stable. Every resource type of 0.6.8 counts 1 per item, so a count of items is a count of the
 * resource (plugin-b-api § « Recettes et tables »).
 */
final class ResourceTypeIndex {
    private final Map<String, List<ItemKey>> items;

    private ResourceTypeIndex(Map<String, List<ItemKey>> items) {
        this.items = items;
    }

    /** Reads the item asset map once. */
    static ResourceTypeIndex load() {
        Map<String, List<ItemKey>> out = new TreeMap<>();
        SkippedAssets skipped = new SkippedAssets("item");
        for (Item item : Item.getAssetMap().getAssetMap().values()) {
            try {
                add(out, item);
            } catch (RuntimeException e) {
                skipped.skip(item == null ? "?" : item.getId(), e);
            }
        }
        out.replaceAll((_, list) ->
                list.stream().sorted(Comparator.comparing(ItemKey::id)).toList());
        return new ResourceTypeIndex(out);
    }

    /** Files {@code item} under each resource type it lists; an item without id is ignored. */
    private static void add(Map<String, List<ItemKey>> out, @Nullable Item item) {
        if (item == null || item.getId() == null || item.getResourceTypes() == null) {
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
    List<ItemKey> items(String resourceTypeId) {
        return items.getOrDefault(resourceTypeId, List.of());
    }
}
