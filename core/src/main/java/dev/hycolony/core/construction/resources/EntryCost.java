package dev.hycolony.core.construction.resources;

import dev.hycolony.core.construction.blueprint.BlueprintEntry;
import dev.hycolony.core.crafting.recipe.RecipeCatalog;
import dev.hycolony.core.kernel.item.BlockItems;
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
     * The item that places the block for a plain cell ({@link #placingItem}), none if it has no item (free to place).
     * For a crafting bench, the item that places it, plus the Hytale upgrades from tier 1 to its planned tier; amounts
     * of one item are summed, the placing item first.
     *
     * <p>Deviation from MC (Hytale world): MC blocks have no tier, a bench there costs its item only → Hytale benches
     * climb tiers by {@code TierLevels[].UpgradeRequirement} (SP3b-1 spec, deviation 3; audit-monde-hytale A-19).
     */
    public static List<ItemAmount> of(BlueprintEntry e, ItemCatalog items, RecipeCatalog recipes) {
        Optional<ItemAmount> placing = placingItem(items.blockItems(e.state().key()));
        if (e.workstation().isEmpty()) {
            return placing.map(List::of).orElse(List.of());
        }
        Workstation bench = e.workstation().get();
        Map<ItemKey, Integer> sum = new LinkedHashMap<>();
        // The plan's block item places the bench: several blocks share a bench id (Bench_Farming, Bench_Trough).
        placing.ifPresent(a -> sum.put(a.item(), a.count()));
        for (ItemAmount a : recipes.benchUpgradeCost(bench.benchId(), FRESH_BENCH_TIER, bench.tier())) {
            sum.merge(a.item(), a.count(), Integer::sum);
        }
        List<ItemAmount> out = new ArrayList<>(sum.size());
        sum.forEach((item, count) -> out.add(new ItemAmount(item, count)));
        return out;
    }

    /**
     * The item that places the block (Structurize BlockUtils.getItemStackFromBlockState): its own item when a player
     * can get it; else the item whose placement makes it (a torch for a wall torch, a lantern for a ceiling one), else
     * what breaking it gives (two small chests for a large one, a trunk for a full trunk, cobble for stone), else its
     * own item anyway; empty for a block without item.
     *
     * <p>Deviation from MC (Hytale world): MC's own block item → Hytale's own item only when it has a source, as the
     * container item of a variant has none (BlockType.getItem); then the item of its placement override
     * (BlockPlacementSettings), then its break drop, which may be several items where MC asks one
     * (BlockHarvestUtils.getDrops; docs/research/audit-monde-hytale.md A-15). Grass and paths cost themselves, which
     * Hytale crafts, where Structurize's placement handlers ask dirt.
     */
    static Optional<ItemAmount> placingItem(BlockItems b) {
        if (b.own().isPresent() && b.ownHasSource()) {
            return Optional.of(new ItemAmount(b.own().get(), 1));
        }
        if (b.placedBy().isPresent()) {
            return Optional.of(new ItemAmount(b.placedBy().get(), 1));
        }
        if (b.breakDrop().isPresent()) {
            return b.breakDrop();
        }
        return b.own().map(item -> new ItemAmount(item, 1));
    }
}
