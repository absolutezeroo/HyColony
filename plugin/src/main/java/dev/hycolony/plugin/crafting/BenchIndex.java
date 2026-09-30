package dev.hycolony.plugin.crafting;

import com.hypixel.hytale.protocol.BenchType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.bench.Bench;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.bench.BenchUpgradeRequirement;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.bench.CraftingBench;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * The {@code Crafting} benches of the game by bench id (plugin-b-api § « Recettes et tables »): their categories and
 * the cost of each tier upgrade. Several blocks may share a bench id ({@code Bench_Farming} and {@code Bench_Trough}
 * are both {@code Farmingbench}): their categories are merged and the tiers come from the one with the most tier
 * levels.
 */
final class BenchIndex {
    private final Map<String, List<String>> categories;
    private final Map<String, Bench> tiered;

    private BenchIndex(Map<String, List<String>> categories, Map<String, Bench> tiered) {
        this.categories = categories;
        this.tiered = tiered;
    }

    /** Reads every block type's {@code Bench}; state variants repeat their base bench, which changes nothing. */
    static BenchIndex load() {
        Map<String, Set<String>> cats = new HashMap<>();
        Map<String, Bench> tiered = new HashMap<>();
        SkippedAssets skipped = new SkippedAssets("block type");
        for (BlockType type : BlockType.getAssetMap().getAssetMap().values()) {
            try {
                Bench bench = craftingBench(type);
                if (bench != null) {
                    cats.computeIfAbsent(bench.getId(), _ -> new LinkedHashSet<>())
                            .addAll(categoryIds(bench));
                    tiered.merge(bench.getId(), bench, (a, b) -> tierCount(b) > tierCount(a) ? b : a);
                }
            } catch (RuntimeException e) {
                skipped.skip(type == null ? "?" : type.getId(), e);
            }
        }
        Map<String, List<String>> frozen = new HashMap<>();
        cats.forEach((id, set) -> frozen.put(id, List.copyOf(set)));
        return new BenchIndex(frozen, tiered);
    }

    /** The block's bench when it is a {@code Crafting} bench with an id, else null. */
    private static @Nullable Bench craftingBench(@Nullable BlockType type) {
        Bench bench = type == null ? null : type.getBench();
        return bench != null && bench.getType() == BenchType.Crafting && bench.getId() != null ? bench : null;
    }

    /** The ids of a crafting bench's categories, in order. */
    private static List<String> categoryIds(Bench bench) {
        if (!(bench instanceof CraftingBench cb) || cb.getCategories() == null) {
            return List.of();
        }
        List<String> ids = new ArrayList<>();
        for (CraftingBench.BenchCategory c : cb.getCategories()) {
            if (c != null && c.getId() != null) {
                ids.add(c.getId());
            }
        }
        return ids;
    }

    /** The bench's categories; empty if unknown (the core then accepts every category). */
    List<String> categories(String benchId) {
        return categories.getOrDefault(benchId, List.of());
    }

    /**
     * The upgrades from {@code fromTier} to {@code toTier}: Hytale's {@code TierLevels[t - 1].UpgradeRequirement}
     * raises tier {@code t} to {@code t + 1} (Bench.getUpgradeRequirement), summed by item. A resource-type material
     * becomes the first item of that type ({@code firstOf}); one with no item is dropped.
     *
     * <p>Deviation from MC: MC has no bench tiers. The core counts items, not resource types, so the builder asks for
     * one precise trunk where Hytale takes any trunk of the family (the Farmingbench upgrades).
     */
    List<ItemAmount> upgradeCost(
            String benchId, int fromTier, int toTier, Function<String, List<ItemKey>> itemsOfResourceType) {
        Bench bench = tiered.get(benchId);
        if (bench == null || toTier <= fromTier) {
            return List.of();
        }
        Map<ItemKey, Integer> sum = new LinkedHashMap<>();
        for (int t = Math.max(1, fromTier); t < toTier; t++) {
            BenchUpgradeRequirement req = bench.getUpgradeRequirement(t);
            MaterialQuantity[] input = req == null ? null : req.getInput();
            for (MaterialQuantity m : input == null ? new MaterialQuantity[0] : input) {
                ItemKey item = itemOf(m, itemsOfResourceType);
                if (item != null) {
                    sum.merge(item, m.getQuantity(), Integer::sum);
                }
            }
        }
        List<ItemAmount> out = new ArrayList<>(sum.size());
        sum.forEach((item, count) -> out.add(new ItemAmount(item, count)));
        return out;
    }

    private static @Nullable ItemKey itemOf(
            @Nullable MaterialQuantity m, Function<String, List<ItemKey>> itemsOfResourceType) {
        if (m == null || m.getQuantity() <= 0) {
            return null;
        }
        if (m.getItemId() != null) {
            return new ItemKey(m.getItemId());
        }
        List<ItemKey> items =
                m.getResourceTypeId() == null ? List.of() : itemsOfResourceType.apply(m.getResourceTypeId());
        return items.isEmpty() ? null : items.getFirst();
    }

    /** How many tier levels the bench declares ({@code TierLevels} length). */
    static int tierCount(Bench b) {
        int n = 0;
        while (b.getTierLevel(n + 1) != null) {
            n++;
        }
        return n;
    }
}
