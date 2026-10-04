package dev.hycolony.plugin.food;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemQuality;
import dev.hycolony.core.kernel.item.FoodInfo;
import dev.hycolony.core.kernel.item.ItemKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import org.jspecify.annotations.Nullable;

/**
 * The foods a citizen eats (spec 2026-10-04 § 5): each {@code Server/HyColony/Foods} file whose item exists, then each
 * other consumable, non-variant item of the id-map's food category, at the value of its quality's rank
 * ({@link dev.hycolony.core.kernel.item.FoodQuality}). Read once the assets are loaded; never throws (a failure
 * answers what was read so far, logged SEVERE).
 */
final class FoodTable {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    private FoodTable() {}

    /** Every food, by item. */
    static Map<ItemKey, FoodInfo> load(FoodIds ids) {
        Map<ItemKey, FoodInfo> out = new HashMap<>();
        try {
            Map<String, Item> items = Item.getAssetMap().getAssetMap();
            int files = addFiles(items, out);
            ids.category().ifPresent(category -> addByQuality(items, category, ids, out));
            LOG.at(Level.INFO).log("Foods: %d, %d of them from a food file", out.size(), files);
        } catch (RuntimeException e) {
            LOG.at(Level.SEVERE).withCause(e).log("HyColony foods could not be read");
        }
        return out;
    }

    /** Adds each food file of a known item, skipping and logging the others; returns how many it added. */
    private static int addFiles(Map<String, Item> items, Map<ItemKey, FoodInfo> out) {
        List<String> unknown = new ArrayList<>();
        int added = 0;
        for (FoodValueAsset f : FoodValueAsset.all().values()) {
            if (!items.containsKey(f.getId())) {
                unknown.add(f.getId());
            } else if (add(f, out)) {
                added++;
            }
        }
        if (!unknown.isEmpty()) {
            LOG.at(Level.WARNING).log("HyColony food files of unknown items skipped: %s", unknown);
        }
        return added;
    }

    /** Adds one food file; false (logged) when its values are out of bounds. */
    private static boolean add(FoodValueAsset f, Map<ItemKey, FoodInfo> out) {
        try {
            out.put(new ItemKey(f.getId()), new FoodInfo(f.nutrition(), f.tier(), f.poisonous()));
            return true;
        } catch (IllegalArgumentException e) {
            LOG.at(Level.WARNING).log("HyColony food file %s skipped: %s", f.getId(), e.getMessage());
            return false;
        }
    }

    /** Each Hytale food without a file, at its quality rank's value (MC FoodUtils.EDIBLE, any modded food). */
    private static void addByQuality(
            Map<String, Item> items, String category, FoodIds ids, Map<ItemKey, FoodInfo> out) {
        for (Item item : items.values()) {
            if (item != null && isDefaultFood(item, category)) {
                out.putIfAbsent(
                        new ItemKey(item.getId()), ids.rank(qualityId(item)).food());
            }
        }
    }

    /**
     * A consumable item of the food category that is no variant: Hytale's variants (Food_Fish_Raw_Rare…) only carry a
     * recipe whose output is another item, and its item library hides them (Item.isVariant).
     */
    private static boolean isDefaultFood(Item item, String category) {
        String[] categories = item.getCategories();
        return item.isConsumable()
                && !item.isVariant()
                && categories != null
                && Arrays.asList(categories).contains(category);
    }

    private static @Nullable String qualityId(Item item) {
        ItemQuality quality = ItemQuality.getAssetMap().getAsset(item.getQualityIndex());
        return quality == null ? null : quality.getId();
    }
}
