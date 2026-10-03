package dev.hycolony.core.construction.resources;

import dev.hycolony.core.construction.blueprint.BlueprintEntry;
import dev.hycolony.core.construction.blueprint.PlanCatalogs;
import dev.hycolony.core.construction.blueprint.StructurePlan;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.port.WorldBlocks;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

/**
 * Items still needed to finish a plan (SOLID then DECO, plan order). Port of MC's neededResources. The placement
 * sequence (one element per unit of item a not-done entry costs) is what the buckets are cut from, as MC's
 * requestMaterials does.
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
        return new NeededResources(List.of(), new LinkedHashMap<>(), 0, _ -> 64);
    }

    /**
     * Every unit of every item a not-yet-done entry costs ({@link EntryCost}), so a bench with its upgrades weighs in
     * the buckets for all it asks; a block only to turn costs nothing ({@link StructurePlan#onlyTurns}).
     */
    public static NeededResources compute(StructurePlan plan, WorldBlocks world, PlanCatalogs catalogs) {
        List<ItemKey> seq =
                new ArrayList<>(plan.solidList().size() + plan.decoList().size());
        for (List<BlueprintEntry> entries : List.of(plan.solidList(), plan.decoList())) {
            for (BlueprintEntry e : entries) {
                List<ItemAmount> cost =
                        EntryCost.of(e, world.get(plan.worldPos(e)).orElse(null), catalogs);
                if (!cost.isEmpty() && !plan.isDone(e, world, catalogs) && !plan.onlyTurns(e, world)) {
                    cost.forEach(a -> seq.addAll(Collections.nCopies(a.count(), a.item())));
                }
            }
        }
        Map<ItemKey, Integer> counts = new LinkedHashMap<>();
        for (ItemKey item : seq) {
            counts.merge(item, 1, Integer::sum);
        }
        return new NeededResources(seq, counts, seq.size(), catalogs.items()::maxStack);
    }

    /** Read-only placement order at compute time: one element per unit of item. Not reduced by {@link #reduce}. */
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

    /**
     * MC BuildingResourcesModuleView.getProgress: 100 minus the share of the plan's items still to place; 0 if none.
     */
    public int progressPercent() {
        int all = sequence.size();
        return all == 0 ? 0 : Math.max(100 - (int) (total * 100.0 / all), 0);
    }

    int maxStack(ItemKey item) {
        return maxStack.applyAsInt(item);
    }
}
