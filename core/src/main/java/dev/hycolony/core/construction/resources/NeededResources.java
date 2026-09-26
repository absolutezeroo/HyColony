package dev.hycolony.core.construction.resources;

import dev.hycolony.core.construction.blueprint.BlueprintEntry;
import dev.hycolony.core.construction.blueprint.StructurePlan;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.port.ItemCatalog;
import dev.hycolony.core.kernel.port.WorldBlocks;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.ToIntFunction;

/**
 * Items still needed to finish a plan (SOLID then DECO, plan order). Port of MC's neededResources. The placement
 * sequence (one item per not-done entry) is what the buckets are cut from, as MC's requestMaterials does.
 */
public final class NeededResources {
    private final List<ItemKey> sequence;
    private final Map<ItemKey, Integer> remaining;
    private final Map<ItemKey, Integer> view;
    private final ToIntFunction<ItemKey> maxStack;
    private int total;

    private NeededResources(
            List<ItemKey> sequence, Map<ItemKey, Integer> remaining, int total, ToIntFunction<ItemKey> maxStack) {
        this.sequence = Collections.unmodifiableList(sequence);
        this.remaining = remaining;
        this.view = Collections.unmodifiableMap(remaining);
        this.total = total;
        this.maxStack = maxStack;
    }

    static NeededResources empty() {
        return new NeededResources(List.of(), new LinkedHashMap<>(), 0, k -> 64);
    }

    /** One item per not-yet-done entry that has an item to place it with. */
    public static NeededResources compute(StructurePlan plan, WorldBlocks world, ItemCatalog catalog) {
        List<ItemKey> seq =
                new ArrayList<>(plan.solidList().size() + plan.decoList().size());
        collect(plan.solidList(), plan, world, catalog, seq);
        collect(plan.decoList(), plan, world, catalog, seq);
        Map<ItemKey, Integer> counts = new LinkedHashMap<>();
        for (ItemKey item : seq) {
            counts.merge(item, 1, Integer::sum);
        }
        return new NeededResources(seq, counts, seq.size(), catalog::maxStack);
    }

    private static void collect(
            List<BlueprintEntry> entries,
            StructurePlan plan,
            WorldBlocks world,
            ItemCatalog catalog,
            List<ItemKey> out) {
        for (BlueprintEntry e : entries) {
            Optional<ItemKey> item = catalog.itemForBlock(e.state().key());
            if (item.isPresent() && !plan.isDone(e, world)) {
                out.add(item.get());
            }
        }
    }

    /** Read-only placement order at compute time: one item per not-done entry. Not reduced by {@link #reduce}. */
    public List<ItemKey> sequence() {
        return sequence;
    }

    /** Read-only, insertion-ordered. */
    public Map<ItemKey, Integer> remaining() {
        return view;
    }

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

    public int total() {
        return total;
    }

    int maxStack(ItemKey item) {
        return maxStack.applyAsInt(item);
    }
}
