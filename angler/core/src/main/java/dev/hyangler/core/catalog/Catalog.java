package dev.hyangler.core.catalog;

import dev.hyangler.api.RodStats;
import java.util.List;
import java.util.Map;

/** Every data file read, sorted by use, and those left out; immutable once read. */
public record Catalog(
        List<Entry> fish,
        List<Entry> junk,
        List<Entry> treasure,
        Map<String, RodStats> rods,
        List<Rejection> rejections) {
    /** Before the assets load: nothing to catch, no rod. */
    public static final Catalog EMPTY = new Catalog(List.of(), List.of(), List.of(), Map.of(), List.of());
}
