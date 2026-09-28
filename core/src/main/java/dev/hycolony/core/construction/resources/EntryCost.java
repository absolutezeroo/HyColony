package dev.hycolony.core.construction.resources;

import dev.hycolony.core.construction.blueprint.BlueprintEntry;
import dev.hycolony.core.crafting.recipe.RecipeCatalog;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.item.Workstation;
import dev.hycolony.core.kernel.port.ItemCatalog;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The items the builder consumes to place one cell of a plan (MC placement handlers' getRequiredItems), which both
 * the needs of the order and the placement itself go through.
 */
public final class EntryCost {
    /** Hytale bench tiers start at 1: the upgrades of a planned bench are counted from there. */
    private static final int FRESH_BENCH_TIER = 1;

    private EntryCost() {}

    /**
     * The block's item for a plain cell, none if it has no item (free to place). For a crafting bench, the bench's
     * item (the block's item if the catalog does not know it, none if neither does), plus the Hytale upgrades from
     * tier 1 to its planned tier; amounts of one item are summed, the bench's item first.
     *
     * <p>Deviation from MC: MC blocks have no tier, a bench there costs its item only (SP3b-1 spec, deviation 3).
     */
    public static List<ItemAmount> of(BlueprintEntry e, ItemCatalog items, RecipeCatalog recipes) {
        Optional<ItemKey> blockItem = items.itemForBlock(e.state().key());
        if (e.workstation().isEmpty()) {
            return blockItem.map(item -> List.of(new ItemAmount(item, 1))).orElse(List.of());
        }
        Workstation bench = e.workstation().get();
        Map<ItemKey, Integer> sum = new LinkedHashMap<>();
        recipes.benchItem(bench.benchId()).or(() -> blockItem).ifPresent(item -> sum.put(item, 1));
        for (ItemAmount a : recipes.benchUpgradeCost(bench.benchId(), FRESH_BENCH_TIER, bench.tier())) {
            sum.merge(a.item(), a.count(), Integer::sum);
        }
        List<ItemAmount> out = new ArrayList<>(sum.size());
        sum.forEach((item, count) -> out.add(new ItemAmount(item, count)));
        return out;
    }
}
