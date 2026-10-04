package dev.hycolony.plugin.food;

import static java.util.stream.Collectors.toCollection;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.BenchRequirement;
import com.hypixel.hytale.protocol.BenchType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.bench.ProcessingBench;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.plugin.crafting.ResourceTypeIndex;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.logging.Level;
import org.jspecify.annotations.Nullable;

/**
 * Hytale's cooking stations (spec 2026-10-04 § 7, MC every furnace): the processing benches with a recipe whose
 * primary output is a food, what each input of those recipes cooks into, and what their fuel slots burn. Read once the
 * assets are loaded; never throws (a failure answers no station, logged SEVERE).
 *
 * @param benches the ids of the cooking benches
 * @param cooked each recipe input -> its recipe's primary output; the first recipe by bench then recipe id wins
 * @param fuels the items of every resource type a cooking bench's fuel slot takes, without repeats
 */
record CookingBenches(Set<String> benches, Map<ItemKey, ItemKey> cooked, List<ItemKey> fuels) {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    /** No cooking station. */
    static final CookingBenches NONE = new CookingBenches(Set.of(), Map.of(), List.of());

    CookingBenches {
        benches = Set.copyOf(benches);
        cooked = Map.copyOf(cooked);
        fuels = List.copyOf(fuels);
    }

    /** One processing recipe making a food at one bench. */
    private record Cooking(String bench, CraftingRecipe recipe, ItemKey output) {}

    /** Reads the recipes and block types; {@code isFood} tells a food. */
    static CookingBenches load(Predicate<ItemKey> isFood) {
        try {
            ResourceTypeIndex resources = ResourceTypeIndex.load();
            List<Cooking> cookings = cookings(isFood);
            Set<String> benches = cookings.stream().map(Cooking::bench).collect(toCollection(TreeSet::new));
            Map<ItemKey, ItemKey> cooked = new HashMap<>();
            for (Cooking c : cookings) {
                addInputs(c, resources, cooked);
            }
            LOG.at(Level.INFO).log("Cooking: %d items cook at %s", cooked.size(), benches);
            return new CookingBenches(benches, cooked, fuels(benches, resources));
        } catch (RuntimeException e) {
            LOG.at(Level.SEVERE).withCause(e).log("Cooking benches could not be read; nothing cooks");
            return NONE;
        }
    }

    /** Every processing recipe whose primary output is a food, by bench then recipe id. */
    private static List<Cooking> cookings(Predicate<ItemKey> isFood) {
        List<Cooking> out = new ArrayList<>();
        for (CraftingRecipe r : CraftingRecipe.getAssetMap().getAssetMap().values()) {
            Optional<ItemKey> food =
                    r == null ? Optional.empty() : primaryOutput(r).filter(isFood);
            food.ifPresent(output -> processingBenches(r).forEach(b -> out.add(new Cooking(b, r, output))));
        }
        out.sort(Comparator.comparing(Cooking::bench)
                .thenComparing(c -> c.recipe().getId()));
        return out;
    }

    /** The item {@code r} makes; empty without a primary output. */
    private static Optional<ItemKey> primaryOutput(CraftingRecipe r) {
        MaterialQuantity primary = r.getPrimaryOutput();
        return primary == null || primary.getItemId() == null
                ? Optional.empty()
                : Optional.of(new ItemKey(primary.getItemId()));
    }

    /** The ids of the processing benches {@code r} names. */
    private static List<String> processingBenches(CraftingRecipe r) {
        BenchRequirement[] required = r.getBenchRequirement();
        if (required == null) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (BenchRequirement b : required) {
            if (b != null && b.type == BenchType.Processing && b.id != null) {
                out.add(b.id);
            }
        }
        return out;
    }

    /** Maps each input of the cooking {@code c}, an item or each item of a resource type, to its output. */
    private static void addInputs(Cooking c, ResourceTypeIndex resources, Map<ItemKey, ItemKey> out) {
        MaterialQuantity[] inputs = c.recipe().getInput();
        if (inputs == null) {
            return;
        }
        for (MaterialQuantity in : inputs) {
            for (ItemKey item : items(in, resources)) {
                out.putIfAbsent(item, c.output());
            }
        }
    }

    private static List<ItemKey> items(@Nullable MaterialQuantity in, ResourceTypeIndex resources) {
        if (in == null) {
            return List.of();
        }
        if (in.getItemId() != null) {
            return List.of(new ItemKey(in.getItemId()));
        }
        return in.getResourceTypeId() == null ? List.of() : resources.items(in.getResourceTypeId());
    }

    /** The items of each fuel slot's resource type of every block whose processing bench is one of {@code benches}. */
    private static List<ItemKey> fuels(Set<String> benches, ResourceTypeIndex resources) {
        Set<String> types = new TreeSet<>();
        for (BlockType type : BlockType.getAssetMap().getAssetMap().values()) {
            if (type != null
                    && type.getBench() instanceof ProcessingBench p
                    && benches.contains(p.getId())
                    && p.getFuel() != null) {
                for (ProcessingBench.ProcessingSlot slot : p.getFuel()) {
                    if (slot != null && slot.getResourceTypeId() != null) {
                        types.add(slot.getResourceTypeId());
                    }
                }
            }
        }
        return types.stream()
                .flatMap(t -> resources.items(t).stream())
                .distinct()
                .toList();
    }
}
