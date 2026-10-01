package dev.hycolony.core.app.select;

import dev.hycolony.core.kernel.Levenshtein;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

/**
 * The item list of an item picker (Structurize WindowSelectRes.updateResources): the items whose id or name contains
 * the filter, ignoring case, those the player holds first, then by name without a filter or closest name to it with
 * one (Levenshtein distance).
 *
 * <p>Deviation from Structurize: the filter is matched against the item's id where Structurize uses its translation
 * key (an id stands for both in Hytale).
 */
public final class SelectResOrder {
    private SelectResOrder() {}

    /** An item with its name and distance to the filter, computed once before sorting. */
    private record Entry(String id, String name, boolean held, int distance) {}

    /** {@code ids} filtered and sorted; {@code name} gives an item's name in the player's language. */
    public static List<String> sorted(
            List<String> ids, Function<String, String> name, Set<String> held, String filter) {
        String lower = filter.toLowerCase(Locale.ROOT);
        Comparator<Entry> heldFirst = Comparator.comparing((Entry e) -> !e.held());
        Comparator<Entry> then =
                filter.isEmpty() ? Comparator.comparing(Entry::name) : Comparator.comparingInt(Entry::distance);
        return ids.stream()
                .map(id -> {
                    String n = name.apply(id);
                    return new Entry(id, n, held.contains(id), filter.isEmpty() ? 0 : Levenshtein.distance(n, filter));
                })
                .filter(e -> filter.isEmpty()
                        || e.id().toLowerCase(Locale.ROOT).contains(lower)
                        || e.name().toLowerCase(Locale.ROOT).contains(lower))
                .sorted(heldFirst.thenComparing(then))
                .map(Entry::id)
                .toList();
    }
}
