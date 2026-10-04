package dev.hycolony.plugin.food;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemQuality;
import dev.hycolony.core.crafting.recipe.JobTags;
import dev.hycolony.core.kernel.item.FoodInfo;
import dev.hycolony.core.kernel.item.ItemKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import org.jspecify.annotations.Nullable;

/**
 * The foods a citizen eats (spec 2026-10-04 § 5): each {@code Server/HyColony/Foods} file whose item exists, then each
 * other consumable item of the id-map's food category that is no variant and whose quality is not hidden from search
 * (Hytale's templates), at the value of its quality's rank ({@link dev.hycolony.core.kernel.item.FoodQuality}); then
 * none of the {@code excluded_food} tag (MC ItemStackUtils.ISFOOD), and those of the {@code poisonousfood} tag
 * poisonous. Deviation from MC (Hytale world): MC marks poisonous foods only by its poisonousfood tag → a HyColony food
 * file may say so too ({@code Poisonous}), as our table did. A file out of bounds is skipped, so its item takes
 * its quality's value like a food without a file. Read once the assets are loaded; never throws (a failure answers
 * what was read so far, logged SEVERE).
 *
 * @param foods every food, by item
 * @param fromFiles the item ids whose food file was taken
 */
record FoodTable(Map<ItemKey, FoodInfo> foods, Set<String> fromFiles) {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    FoodTable {
        foods = Map.copyOf(foods);
        fromFiles = Set.copyOf(fromFiles);
    }

    /** Every food, without the items of {@code tags}' excluded_food and poisonous for those of its poisonousfood. */
    static FoodTable load(FoodIds ids, JobTags tags) {
        Map<ItemKey, FoodInfo> out = new HashMap<>();
        Set<String> files = new HashSet<>();
        try {
            Map<String, Item> items = Item.getAssetMap().getAssetMap();
            addFiles(items, out, files);
            ids.category().ifPresent(category -> addByQuality(items, category, ids, out));
            Set<ItemKey> excluded = tags.get(JobTags.EXCLUDED_FOOD);
            Set<ItemKey> poisonous = tags.get(JobTags.POISONOUS_FOOD);
            out.keySet().removeAll(excluded);
            files.removeIf(id -> excluded.contains(new ItemKey(id)));
            out.replaceAll((item, food) -> poisonous.contains(item) ? poisoned(food) : food);
            LOG.at(Level.INFO).log("Foods: %d, %d of them from a food file", out.size(), files.size());
        } catch (RuntimeException e) {
            LOG.at(Level.SEVERE).withCause(e).log("HyColony foods could not be read");
        }
        return new FoodTable(out, files);
    }

    /** Adds each food file of a known item to {@code out} and its id to {@code files}; skips and logs the others. */
    private static void addFiles(Map<String, Item> items, Map<ItemKey, FoodInfo> out, Set<String> files) {
        List<String> unknown = new ArrayList<>();
        for (FoodValueAsset f : FoodValueAsset.all().values()) {
            if (!items.containsKey(f.getId())) {
                unknown.add(f.getId());
            } else if (add(f, out)) {
                files.add(f.getId());
            }
        }
        if (!unknown.isEmpty()) {
            LOG.at(Level.WARNING).log("HyColony food files of unknown items skipped: %s", unknown);
        }
    }

    /** {@code food}, poisonous (MC poisonousfood tag). */
    private static FoodInfo poisoned(FoodInfo food) {
        return new FoodInfo(food.nutrition(), food.tier(), true);
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
            ItemQuality quality =
                    item == null ? null : ItemQuality.getAssetMap().getAsset(item.getQualityIndex());
            if (item != null && isDefaultFood(item, quality, category)) {
                out.putIfAbsent(
                        new ItemKey(item.getId()),
                        ids.rank(quality == null ? null : quality.getId()).food());
            }
        }
    }

    /**
     * A consumable item of the food category that is no variant and whose quality is not hidden from search: Hytale's
     * variants (Food_Fish_Raw_Rare…) only carry a recipe whose output is another item, and its templates
     * (Template_Food, quality Template) are no real items; its item library hides both.
     */
    private static boolean isDefaultFood(Item item, @Nullable ItemQuality quality, String category) {
        String[] categories = item.getCategories();
        return item.isConsumable()
                && !item.isVariant()
                && (quality == null || !quality.isHiddenFromSearch())
                && categories != null
                && Arrays.asList(categories).contains(category);
    }
}
