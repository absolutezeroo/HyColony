package dev.hycolony.core.crafting.recipe;

import dev.hycolony.core.kernel.item.ItemKey;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * MC's crafter item tags ({@code data/minecolonies/tags/items}: {@code <job>_product}, {@code <job>_product_excluded},
 * {@code reduceable_ingredient}, {@code reduceable_product_excluded}), each the union of every file naming it, as a
 * Minecraft tag adds up its packs' files.
 *
 * <p>Deviation from MC (Hytale world): MC reads Minecraft item tags → HyColony reads them from its
 * {@code Server/HyColony/JobTags} asset files, which any mod may add to (spec 2026-10-04 § 6).
 *
 * @param tags tag name -> its items
 */
public record JobTags(Map<String, Set<ItemKey>> tags) {
    /** No tag at all. */
    public static final JobTags EMPTY = new JobTags(Map.of());

    /** MC CRAFTING_REDUCEABLE's ingredient tag: what an improvement may take off a recipe. */
    public static final String REDUCEABLE_INGREDIENT = "reduceable_ingredient";

    /** MC CRAFTING_REDUCEABLE's product exclusion tag: what is never improved. */
    public static final String REDUCEABLE_PRODUCT_EXCLUDED = "reduceable_product_excluded";

    private static final String PRODUCT = "_product";
    private static final String PRODUCT_EXCLUDED = "_product_excluded";

    /** One tag file: a tag name and the items it adds. */
    public record TagFile(String tag, List<ItemKey> values) {
        public TagFile {
            values = List.copyOf(values);
        }
    }

    public JobTags {
        Map<String, Set<ItemKey>> copy = new LinkedHashMap<>();
        tags.forEach((tag, items) -> copy.put(tag, Set.copyOf(items)));
        tags = Map.copyOf(copy);
    }

    /** Merges {@code files} by tag; a file of a tag HyColony does not read is skipped after one {@code warn}. */
    public static JobTags merge(Collection<TagFile> files, Consumer<String> warn) {
        Map<String, Set<ItemKey>> out = new LinkedHashMap<>();
        for (TagFile file : files) {
            if (isKnown(file.tag())) {
                out.computeIfAbsent(file.tag(), t -> new LinkedHashSet<>()).addAll(file.values());
            } else {
                warn.accept("JobTags: unknown tag '" + file.tag() + "' skipped");
            }
        }
        return new JobTags(out);
    }

    /** The items of {@code tag}; empty for a tag no file names. */
    public Set<ItemKey> get(String tag) {
        return tags.getOrDefault(tag, Set.of());
    }

    /** MC {@code <job>_product}: {@code hycolony:farmer} reads {@code farmer_product}. */
    Set<ItemKey> products(String jobId) {
        return get(jobName(jobId) + PRODUCT);
    }

    /** MC {@code <job>_product_excluded}. */
    Set<ItemKey> excludedProducts(String jobId) {
        return get(jobName(jobId) + PRODUCT_EXCLUDED);
    }

    /** Whether HyColony reads {@code tag}: the two reduceable tags, or a job's product or product exclusion tag. */
    static boolean isKnown(String tag) {
        return tag.equals(REDUCEABLE_INGREDIENT)
                || tag.equals(REDUCEABLE_PRODUCT_EXCLUDED)
                || (tag.endsWith(PRODUCT) && tag.length() > PRODUCT.length())
                || (tag.endsWith(PRODUCT_EXCLUDED) && tag.length() > PRODUCT_EXCLUDED.length());
    }

    /** The job id without its namespace, as MC names its tags after the job. */
    private static String jobName(String jobId) {
        int colon = jobId.indexOf(':');
        return colon < 0 ? jobId : jobId.substring(colon + 1);
    }
}
