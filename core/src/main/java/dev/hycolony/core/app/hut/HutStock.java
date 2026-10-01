package dev.hycolony.core.app.hut;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemKey;
import java.util.List;

/**
 * An item of a hut's inventory summary (MC WindowHutAllInventory): its total in the hut block and racks, and each
 * container holding it, most first (what Locate highlights).
 */
public record HutStock(ItemKey item, int count, List<Holder> holders) {
    /** MC Utils.format suffixes: thousands, millions, billions. */
    private static final String[] SUFFIXES = {"k", "M", "G"};

    public HutStock {
        holders = List.copyOf(holders);
    }

    /** A container and how many of the item it holds. */
    public record Holder(BlockPos pos, int count) {}

    /** MC Utils.format: 999, 1k, 1.2k, 12k, 1.5M; one decimal only under 10 of the unit, and only if not zero. */
    public static String abbreviate(int value) {
        if (value < 1000) {
            return Integer.toString(value);
        }
        long unit = 1000;
        int suffix = 0;
        while (suffix + 1 < SUFFIXES.length && value >= unit * 1000) {
            unit *= 1000;
            suffix++;
        }
        long tenths = value / (unit / 10);
        boolean decimal = tenths < 100 && tenths % 10 != 0;
        String number = decimal ? String.valueOf(tenths / 10d) : Long.toString(tenths / 10);
        return number + SUFFIXES[suffix];
    }
}
