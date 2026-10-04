package dev.hycolony.plugin.crafting;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import dev.hycolony.core.crafting.recipe.JobTags;
import dev.hycolony.core.kernel.item.ItemKey;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;

/**
 * The job tags of every {@code Server/HyColony/JobTags} file ({@link JobTagAsset}) for the core's {@link JobTags}:
 * each value is an item id, or {@code res:<type>} for every item of that resource type (as recipe ingredients). An
 * unknown value is skipped, all logged in one WARNING. Deviation from MC (Hytale world): MC reads Minecraft item tags →
 * HyColony reads its own asset files. Read once the assets are loaded; never throws (a failure answers no tag, SEVERE).
 */
public final class HytaleJobTags {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    /** The prefix of a resource type value, as the recipe ingredients write it. */
    static final String RESOURCE_PREFIX = "res:";

    private HytaleJobTags() {}

    /** Every tag file merged by tag, in file name order. */
    public static JobTags load() {
        try {
            ResourceTypeIndex resources = ResourceTypeIndex.load();
            List<String> unknown = new ArrayList<>();
            List<JobTags.TagFile> files = JobTagAsset.all().values().stream()
                    .sorted(Comparator.comparing(JobTagAsset::getId))
                    .map(a -> new JobTags.TagFile(a.tag(), items(a, resources, unknown)))
                    .toList();
            if (!unknown.isEmpty()) {
                LOG.at(Level.WARNING).log("HyColony job tags: unknown values skipped: %s", unknown);
            }
            return JobTags.merge(files, w -> LOG.at(Level.WARNING).log("HyColony %s", w));
        } catch (RuntimeException e) {
            LOG.at(Level.SEVERE).withCause(e).log("HyColony job tags could not be read; none apply");
            return JobTags.EMPTY;
        }
    }

    /** The items of one file's values; each unknown value is added to {@code unknown} as {@code file: value}. */
    private static List<ItemKey> items(JobTagAsset file, ResourceTypeIndex resources, List<String> unknown) {
        List<ItemKey> out = new ArrayList<>();
        for (String value : file.values()) {
            List<ItemKey> found = itemsOf(value, resources);
            if (found.isEmpty()) {
                unknown.add(file.getId() + ": " + value);
            }
            out.addAll(found);
        }
        return out;
    }

    /** The items {@code value} names: one known item, or each item of a resource type; empty when unknown. */
    private static List<ItemKey> itemsOf(String value, ResourceTypeIndex resources) {
        if (value.startsWith(RESOURCE_PREFIX)) {
            return resources.items(value.substring(RESOURCE_PREFIX.length()));
        }
        return Item.getAssetMap().getAsset(value) == null ? List.of() : List.of(new ItemKey(value));
    }
}
