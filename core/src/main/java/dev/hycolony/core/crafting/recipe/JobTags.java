package dev.hycolony.core.crafting.recipe;

import dev.hycolony.core.kernel.item.ItemKey;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * MC's item tags that HyColony's systems read ({@code data/minecolonies/tags/items}): each crafter's
 * {@code <crafter>_product} and {@code <crafter>_product_excluded} (MC TagConstants.CRAFTING_*), the
 * {@code reduceable_ingredient} and {@code reduceable_product_excluded} tags of recipe improvement, and
 * {@code excluded_food} (MC ModTags.excludedFood). Each is the union of every file naming it, as a Minecraft tag adds
 * up its packs' files, and a file may include other tags (MC {@code #tag} entries).
 *
 * <p>Deviation from MC (Hytale world): MC reads Minecraft item tags → HyColony reads them from its
 * {@code Server/HyColony/JobTags} asset files, read in every pack (AssetStore.java:755), which any mod may add to (spec
 * 2026-10-04 § 6).
 *
 * @param tags tag name -> its items, includes resolved
 */
public record JobTags(Map<String, Set<ItemKey>> tags) {
    /** No tag at all. */
    public static final JobTags EMPTY = new JobTags(Map.of());

    /** MC CRAFTING_REDUCEABLE's ingredient tag: what an improvement may take off a recipe. */
    public static final String REDUCEABLE_INGREDIENT = "reduceable_ingredient";

    /** MC CRAFTING_REDUCEABLE's product exclusion tag: what is never improved. */
    public static final String REDUCEABLE_PRODUCT_EXCLUDED = "reduceable_product_excluded";

    /** MC ModTags.excludedFood: items that are never food for a citizen (ItemStackUtils.ISFOOD). */
    public static final String EXCLUDED_FOOD = "excluded_food";

    private static final String PRODUCT = "_product";
    private static final String PRODUCT_EXCLUDED = "_product_excluded";

    /**
     * One tag file: a tag name, the items it adds and the tags whose items it adds too (MC {@code #tag} entries).
     *
     * @param tag the tag the file adds to
     * @param values its items
     * @param includes the names of the tags it includes
     */
    public record TagFile(String tag, List<ItemKey> values, List<String> includes) {
        public TagFile {
            values = List.copyOf(values);
            includes = List.copyOf(includes);
        }

        /** A file without included tags. */
        public TagFile(String tag, List<ItemKey> values) {
            this(tag, values, List.of());
        }
    }

    public JobTags {
        Map<String, Set<ItemKey>> copy = new LinkedHashMap<>();
        tags.forEach((tag, items) -> copy.put(tag, Set.copyOf(items)));
        tags = Map.copyOf(copy);
    }

    /**
     * Merges {@code files} by tag, then adds each included tag's items (through further includes, once each). A file
     * or an include of a tag HyColony does not read is skipped after one call to {@code warn}.
     */
    public static JobTags merge(Collection<TagFile> files, Consumer<String> warn) {
        Map<String, Set<ItemKey>> items = new LinkedHashMap<>();
        Map<String, Set<String>> includes = new LinkedHashMap<>();
        for (TagFile file : files) {
            if (!isKnown(file.tag())) {
                warn.accept("JobTags: unknown tag '" + file.tag() + "' skipped");
                continue;
            }
            items.computeIfAbsent(file.tag(), t -> new LinkedHashSet<>()).addAll(file.values());
            for (String included : file.includes()) {
                if (isKnown(included)) {
                    includes.computeIfAbsent(file.tag(), t -> new LinkedHashSet<>())
                            .add(included);
                } else {
                    warn.accept("JobTags: " + file.tag() + " includes unknown tag '" + included + "', skipped");
                }
            }
        }
        Map<String, Set<ItemKey>> out = new LinkedHashMap<>();
        items.keySet().forEach(tag -> out.put(tag, resolve(tag, items, includes)));
        return new JobTags(out);
    }

    /** The items of {@code tag} and of every tag it includes, directly or not; each tag is visited once. */
    private static Set<ItemKey> resolve(
            String tag, Map<String, Set<ItemKey>> items, Map<String, Set<String>> includes) {
        Set<ItemKey> out = new LinkedHashSet<>();
        Set<String> seen = new HashSet<>();
        Deque<String> next = new ArrayDeque<>(List.of(tag));
        while (!next.isEmpty()) {
            String current = next.pop();
            if (seen.add(current)) {
                out.addAll(items.getOrDefault(current, Set.of()));
                next.addAll(includes.getOrDefault(current, Set.of()));
            }
        }
        return out;
    }

    /** The items of {@code tag}; empty for a tag no file names. */
    public Set<ItemKey> get(String tag) {
        return tags.getOrDefault(tag, Set.of());
    }

    /** MC {@code <crafter>_product}: the farmer's crafter {@code farmer} reads {@code farmer_product}. */
    Set<ItemKey> products(String crafter) {
        return get(crafter + PRODUCT);
    }

    /** MC {@code <crafter>_product_excluded}. */
    Set<ItemKey> excludedProducts(String crafter) {
        return get(crafter + PRODUCT_EXCLUDED);
    }

    /** Whether HyColony reads {@code tag}: a reduceable tag, excluded_food, or a crafter's product (exclusion) tag. */
    static boolean isKnown(String tag) {
        return tag.equals(REDUCEABLE_INGREDIENT)
                || tag.equals(REDUCEABLE_PRODUCT_EXCLUDED)
                || tag.equals(EXCLUDED_FOOD)
                || (tag.endsWith(PRODUCT) && tag.length() > PRODUCT.length())
                || (tag.endsWith(PRODUCT_EXCLUDED) && tag.length() > PRODUCT_EXCLUDED.length());
    }
}
