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
 * each value is an item id, {@code res:<type>} for every item of that resource type (HyColony's own spelling of a
 * recipe ingredient's {@code ResourceTypeId}), or {@code #<tag>} for the items of another tag (MC tag entries). An
 * unknown value is skipped, all logged in one WARNING. Deviation from MC (Hytale world): MC reads Minecraft item tags →
 * HyColony reads its own asset files, read in every pack (AssetStore.java:755). Read once the assets are loaded; never
 * throws (a failure answers no tag, SEVERE).
 */
public final class HytaleJobTags {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    /** The prefix of a resource type value: HyColony's convention, not Hytale's. */
    static final String RESOURCE_PREFIX = "res:";
    /** The prefix of an included tag, as MC writes it. */
    static final String TAG_PREFIX = "#";

    private HytaleJobTags() {}

    /** Every tag file merged by tag, in file name order. */
    public static JobTags load() {
        try {
            ResourceTypeIndex resources = ResourceTypeIndex.load();
            List<String> unknown = new ArrayList<>();
            List<JobTags.TagFile> files = JobTagAsset.all().values().stream()
                    .sorted(Comparator.comparing(JobTagAsset::getId))
                    .map(a -> new JobTags.TagFile(a.tag(), items(a, resources, unknown), includes(a)))
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

    /**
     * The items of one file's item and resource type values ({@code #tag} values are {@link #includes}); each unknown
     * value is added to {@code unknown} as {@code file: value}.
     */
    private static List<ItemKey> items(JobTagAsset file, ResourceTypeIndex resources, List<String> unknown) {
        List<ItemKey> out = new ArrayList<>();
        for (String value :
                file.values().stream().filter(v -> !v.startsWith(TAG_PREFIX)).toList()) {
            List<ItemKey> found = itemsOf(value, resources);
            if (found.isEmpty()) {
                unknown.add(file.getId() + ": " + value);
            }
            out.addAll(found);
        }
        return out;
    }

    /** The tags one file's {@code #tag} values include; the core skips (and logs) an unknown one. */
    private static List<String> includes(JobTagAsset file) {
        return file.values().stream()
                .filter(v -> v.startsWith(TAG_PREFIX))
                .map(v -> v.substring(TAG_PREFIX.length()))
                .toList();
    }

    /** The items {@code value} names: one known item, or each item of a resource type; empty when unknown. */
    private static List<ItemKey> itemsOf(String value, ResourceTypeIndex resources) {
        if (value.startsWith(RESOURCE_PREFIX)) {
            return resources.items(value.substring(RESOURCE_PREFIX.length()));
        }
        return Item.getAssetMap().getAsset(value) == null ? List.of() : List.of(new ItemKey(value));
    }
}
