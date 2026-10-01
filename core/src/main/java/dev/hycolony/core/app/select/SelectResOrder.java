package dev.hycolony.core.app.select;

import dev.hycolony.core.app.hut.HutStockOrder;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

/**
 * The item list of an item picker (Structurize WindowSelectRes.updateResources): the items whose id or name contains
 * the filter, ignoring case, those the player holds first, then by name without a filter or closest name to it with
 * one (Levenshtein distance).
 */
public final class SelectResOrder {
    private SelectResOrder() {}

    /** {@code ids} filtered and sorted; {@code name} gives an item's name in the player's language. */
    public static List<String> sorted(
            List<String> ids, Function<String, String> name, Set<String> held, String filter) {
        String lower = filter.toLowerCase(Locale.ROOT);
        Comparator<String> heldFirst = Comparator.comparing((String id) -> !held.contains(id));
        Comparator<String> then = filter.isEmpty()
                ? Comparator.comparing(name)
                : Comparator.comparingInt(id -> HutStockOrder.levenshtein(name.apply(id), filter));
        return ids.stream()
                .filter(id -> filter.isEmpty()
                        || id.toLowerCase(Locale.ROOT).contains(lower)
                        || name.apply(id).toLowerCase(Locale.ROOT).contains(lower))
                .sorted(heldFirst.thenComparing(then))
                .toList();
    }
}
