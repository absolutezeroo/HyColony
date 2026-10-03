package dev.hycolony.plugin.crafting;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.ItemResourceType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemQuality;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.plugin.item.HytaleItemSources;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Level;
import org.jspecify.annotations.Nullable;

/**
 * Every item by the Hytale resource types it lists ({@code Item.getResourceTypes}), except Hytale's test items, each
 * list ordered with the items a survival player can get first ({@link HytaleItemSources}), then by id, so a choice
 * among them is stable and obtainable when one is. Every resource type of 0.6.8 counts 1 per item, so a
 * count of items is a count of the resource (plugin-b-api § « Recettes et tables »).
 */
public final class ResourceTypeIndex {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

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

    /** Reads the item asset map once, and where each item comes from to order them ({@link #items}). */
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
        Comparator<ItemKey> order = order(out.values());
        out.replaceAll((_, list) -> list.stream().sorted(order).toList());
        return new ResourceTypeIndex(out);
    }

    /**
     * Among {@code lists}' items, those with a source first, then by id; by id alone when the sources cannot be read
     * (logged), so that an order never costs the whole catalog.
     */
    private static Comparator<ItemKey> order(Collection<List<ItemKey>> lists) {
        Comparator<ItemKey> byId = Comparator.comparing(ItemKey::id);
        // ponytail: every load walks the assets for its own source index (the recipe catalog at world start, cooking
        // and fuels at first use); share one index if that ever shows.
        try {
            HytaleItemSources sources = new HytaleItemSources();
            Set<String> sourced = new HashSet<>();
            for (List<ItemKey> list : lists) {
                for (ItemKey item : list) {
                    if (sources.hasSource(item.id())) {
                        sourced.add(item.id());
                    }
                }
            }
            return Comparator.comparing((ItemKey k) -> !sourced.contains(k.id()))
                    .thenComparing(byId);
        } catch (RuntimeException e) {
            LOG.at(Level.WARNING).withCause(e).log("Item sources unreadable: resource types ordered by id only");
            return byId;
        }
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

    /**
     * The items of {@code resourceTypeId}, those a survival player can get first ({@link HytaleItemSources}), then by
     * id; empty if none. Who takes the first one (a bench upgrade, a request's icon, a creative gift) so gets an
     * obtainable item: the giant fern trunks carry the oak trunk's types through their parent (Wood_Hardwood_Trunk,
     * Wood_Trunk…) but breaking one gives an oak trunk, never itself.
     */
    public List<ItemKey> items(String resourceTypeId) {
        return items.getOrDefault(resourceTypeId, List.of());
    }
}
