package dev.hycolony.core.construction;

import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.port.ItemCatalog;
import dev.hycolony.core.kernel.port.WorldBlocks;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.ToIntFunction;

/** Items still needed to finish a plan, in placement order (SOLID then DECO). Port of MC's neededResources. */
public final class NeededResources {
    private final Map<ItemKey, Integer> remaining;
    private final Map<ItemKey, Integer> view;
    private final ToIntFunction<ItemKey> maxStack;
    private int total;

    private NeededResources(Map<ItemKey, Integer> remaining, int total, ToIntFunction<ItemKey> maxStack) {
        this.remaining = remaining;
        this.view = Collections.unmodifiableMap(remaining);
        this.total = total;
        this.maxStack = maxStack;
    }

    static NeededResources empty() {
        return new NeededResources(new LinkedHashMap<>(), 0, k -> 64);
    }

    /** One item per not-yet-done entry that has an item to place it with. */
    public static NeededResources compute(StructurePlan plan, WorldBlocks world, ItemCatalog catalog) {
        Map<ItemKey, Integer> out = new LinkedHashMap<>();
        int total = count(plan.solidList(), plan, world, catalog, out)
                + count(plan.decoList(), plan, world, catalog, out);
        return new NeededResources(out, total, catalog::maxStack);
    }

    private static int count(List<BlueprintEntry> entries, StructurePlan plan, WorldBlocks world, ItemCatalog catalog,
            Map<ItemKey, Integer> out) {
        int n = 0;
        for (BlueprintEntry e : entries) {
            Optional<ItemKey> item = catalog.itemForBlock(e.state().key());
            if (item.isPresent() && !plan.isDone(e, world)) {
                out.merge(item.get(), 1, Integer::sum);
                n++;
            }
        }
        return n;
    }

    /** Read-only, insertion-ordered. */
    public Map<ItemKey, Integer> remaining() { return view; }

    /** On placement. Drops the item once nothing of it is left. */
    public void reduce(ItemKey item, int n) {
        Integer cur = remaining.get(item);
        if (cur == null || n <= 0) {
            return;
        }
        if (cur <= n) {
            remaining.remove(item);
            total -= cur;
        } else {
            remaining.put(item, cur - n);
            total -= n;
        }
    }

    public int total() { return total; }

    int maxStack(ItemKey item) { return maxStack.applyAsInt(item); }
}
