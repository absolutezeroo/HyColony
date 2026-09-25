package dev.hycolony.core.construction;

import dev.hycolony.core.kernel.item.ItemKey;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

/** Splits needs into loads a builder can carry. Port of MC's BuildingResourcesModule bucket logic. */
public final class Buckets {
    /** Citizen inventory (27) minus 9 slots kept free. */
    public static final int BUCKET_STACKS = 27 - 9;

    private Buckets() {}

    /** In insertion order; each bucket holds at most 18 stacks, an item that overflows continues in the next one. */
    public static List<Map<ItemKey, Integer>> split(Map<ItemKey, Integer> needs, ToIntFunction<ItemKey> maxStack) {
        List<Map<ItemKey, Integer>> out = new ArrayList<>();
        Map<ItemKey, Integer> bucket = null;
        int used = BUCKET_STACKS;
        for (Map.Entry<ItemKey, Integer> e : needs.entrySet()) {
            int max = Math.max(1, maxStack.applyAsInt(e.getKey()));
            int left = e.getValue();
            while (left > 0) {
                if (used == BUCKET_STACKS) {
                    bucket = new LinkedHashMap<>();
                    out.add(bucket);
                    used = 0;
                }
                int take = (int) Math.min(left, (long) (BUCKET_STACKS - used) * max);
                bucket.put(e.getKey(), take);
                used += (take + max - 1) / max;
                left -= take;
            }
        }
        return out;
    }
}
