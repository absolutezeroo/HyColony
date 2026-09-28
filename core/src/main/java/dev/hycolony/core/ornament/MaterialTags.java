package dev.hycolony.core.ornament;

import java.util.Map;
import java.util.Set;

/** DO's material tags as Hytale block ids: which blocks each slot tag accepts (MC DO {@code ModTags}). */
public record MaterialTags(Map<String, Set<String>> tags) {

    public MaterialTags {
        tags = Map.copyOf(tags);
    }

    /** Whether tag accepts blockId; false for an unknown tag. */
    public boolean accepts(String tag, String blockId) {
        return materials(tag).contains(blockId);
    }

    /** The block ids tag accepts; empty for an unknown tag. */
    public Set<String> materials(String tag) {
        return tags.getOrDefault(tag, Set.of());
    }
}
