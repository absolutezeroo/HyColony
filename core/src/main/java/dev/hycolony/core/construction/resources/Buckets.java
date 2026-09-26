package dev.hycolony.core.construction.resources;

import dev.hycolony.core.kernel.item.ItemKey;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

/** Splits needs into loads a builder can carry. Port of MC's BuildingResourcesModule.addNeededResource. */
public final class Buckets {
    /** Citizen inventory (27) minus 9 slots kept free. */
    public static final int BUCKET_STACKS = 27 - 9;

    private Buckets() {}

    /**
     * Walks the placement sequence, adding each step to the last bucket unless that would take it past 18 stacks, in
     * which case a new bucket opens. An item can therefore appear in several, non-adjacent buckets. The returned
     * list and maps are mutable (the module consumes them on placement).
     */
    public static List<Map<ItemKey, Integer>> split(List<ItemKey> sequence, ToIntFunction<ItemKey> maxStack) {
        List<Map<ItemKey, Integer>> out = new ArrayList<>();
        Map<ItemKey, Integer> bucket = null;
        int used = 0;
        for (ItemKey item : sequence) {
            int max = Math.max(1, maxStack.applyAsInt(item));
            int cur = bucket == null ? 0 : bucket.getOrDefault(item, 0);
            int grow = (cur + max) / max - (cur + max - 1) / max; // 1 when cur + 1 opens a new stack
            if (bucket == null || used + grow > BUCKET_STACKS) {
                bucket = new LinkedHashMap<>();
                out.add(bucket);
                used = 0;
                cur = 0;
                grow = 1;
            }
            bucket.put(item, cur + 1);
            used += grow;
        }
        return out;
    }
}
