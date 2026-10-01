package dev.hycolony.core.app.hut;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * The order of MC WindowHutAllInventory's list (updateResources): the items whose shown name or id contains the
 * filter, closest names to the filter first (Levenshtein distance), then the sort the window's button chose.
 *
 * <p>Deviation from MC: the filter looks in the shown name and the item id, where MC looks in the item's description
 * id and its tooltip lines (Minecraft item data Hytale has not).
 */
public final class HutStockOrder {
    /** MC sortDescriptor 0..4 and the button's label for each. */
    public enum Sort {
        NONE("v^"),
        NAME_ASC("A^"),
        NAME_DESC("Av"),
        COUNT_ASC("1^"),
        COUNT_DESC("1v");

        private final String label;

        Sort(String label) {
            this.label = label;
        }

        /** MC's label for the sort button, not translated. */
        public String label() {
            return label;
        }

        /** MC setSortFlag: the next sort, back to none after the last. */
        public Sort next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    private HutStockOrder() {}

    /** {@code stock} filtered and ordered as MC's window, {@code name} giving each item's shown name. */
    public static List<HutStock> sorted(
            List<HutStock> stock, Function<HutStock, String> name, String filter, Sort sort) {
        String lower = filter.toLowerCase(Locale.ROOT);
        Comparator<HutStock> byName = Comparator.comparing(name);
        Comparator<HutStock> byCount = Comparator.comparingInt(HutStock::count);
        Comparator<HutStock> closest = Comparator.comparingInt(s -> levenshtein(name.apply(s), filter));
        // MC sorts by distance first, then (a stable sort) by the chosen order: ties keep the distance order.
        Comparator<HutStock> order = switch (sort) {
            case NONE -> closest;
            case NAME_ASC -> byName;
            case NAME_DESC -> byName.reversed();
            case COUNT_ASC -> byCount;
            case COUNT_DESC -> byCount.reversed();
        };
        return stock.stream()
                .filter(s -> filter.isEmpty()
                        || name.apply(s).toLowerCase(Locale.ROOT).contains(lower)
                        || s.item().id().toLowerCase(Locale.ROOT).contains(lower))
                .sorted(closest)
                .sorted(order)
                .toList();
    }

    /** The edit distance between {@code a} and {@code b}, case-sensitive (MC StringUtils.getLevenshteinDistance). */
    static int levenshtein(String a, String b) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }
}
