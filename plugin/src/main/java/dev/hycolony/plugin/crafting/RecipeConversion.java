package dev.hycolony.plugin.crafting;

import com.hypixel.hytale.protocol.BenchType;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import dev.hycolony.core.crafting.recipe.BenchRequirement;
import dev.hycolony.core.crafting.recipe.Ingredient;
import dev.hycolony.core.crafting.recipe.Recipe;
import dev.hycolony.core.crafting.recipe.RecipeSource;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Turns a Hytale {@link CraftingRecipe} into a core {@link Recipe} (plugin-b-api § « Recettes et tables »). Pure: reads
 * the asset only.
 */
final class RecipeConversion {
    private RecipeConversion() {}

    /**
     * The core recipe, or empty when the colony cannot craft it: no {@code Crafting} bench requirement (processing,
     * diagram and structural benches are out of scope), an input given by item tag, or no output item.
     *
     * <p>Deviation from MC: a recipe several benches make keeps its first {@code Crafting} bench only (28 recipes in
     * 0.6.8); the one recipe asking for an item tag is left out, as the core cannot name a tag back from its index.
     */
    static Optional<Recipe> convert(CraftingRecipe r) {
        com.hypixel.hytale.protocol.@Nullable BenchRequirement bench = craftingBench(r);
        Optional<List<Ingredient>> inputs = inputs(r.getInput());
        MaterialQuantity primary = primaryOutput(r);
        if (bench == null
                || inputs.isEmpty()
                || primary == null
                || primary.getItemId() == null
                || primary.getQuantity() <= 0) {
            return Optional.empty();
        }
        return Optional.of(new Recipe(
                inputs.get(),
                new ItemAmount(new ItemKey(primary.getItemId()), primary.getQuantity()),
                secondaryOutputs(r.getOutputs(), primary.getItemId()),
                new BenchRequirement(
                        bench.id,
                        bench.categories == null
                                ? List.of()
                                : Arrays.stream(bench.categories)
                                        .filter(Objects::nonNull)
                                        .toList(),
                        Math.max(0, bench.requiredTierLevel)),
                Optional.empty(),
                new RecipeSource.Hytale(r.getId()),
                r.isKnowledgeRequired()));
    }

    /** The first {@code Crafting} requirement ({@code Fieldcraft} included), or null. */
    private static com.hypixel.hytale.protocol.@Nullable BenchRequirement craftingBench(CraftingRecipe r) {
        com.hypixel.hytale.protocol.BenchRequirement[] all = r.getBenchRequirement();
        if (all == null) {
            return null;
        }
        return Arrays.stream(all)
                .filter(b -> b != null && b.type == BenchType.Crafting && b.id != null && !b.id.isEmpty())
                .findFirst()
                .orElse(null);
    }

    /** Every input as an item or a resource type; empty if one is a tag or unreadable. */
    private static Optional<List<Ingredient>> inputs(MaterialQuantity @Nullable [] in) {
        if (in == null || in.length == 0) {
            return Optional.empty();
        }
        List<Ingredient> out = new ArrayList<>(in.length);
        for (MaterialQuantity m : in) {
            if (m == null || m.getQuantity() <= 0) {
                return Optional.empty();
            }
            if (m.getItemId() != null) {
                out.add(new Ingredient.OfItem(new ItemKey(m.getItemId()), m.getQuantity()));
            } else if (m.getResourceTypeId() != null) {
                out.add(new Ingredient.OfResourceType(m.getResourceTypeId(), m.getQuantity()));
            } else {
                return Optional.empty();
            }
        }
        return Optional.of(out);
    }

    /** {@code PrimaryOutput}, else the first output (a recipe file without it, CraftingRecipe.processConfig). */
    private static @Nullable MaterialQuantity primaryOutput(CraftingRecipe r) {
        if (r.getPrimaryOutput() != null) {
            return r.getPrimaryOutput();
        }
        MaterialQuantity[] outputs = r.getOutputs();
        return outputs == null || outputs.length == 0 ? null : outputs[0];
    }

    /** The outputs besides one occurrence of the primary item; outputs without an item are ignored. */
    private static List<ItemAmount> secondaryOutputs(MaterialQuantity @Nullable [] outputs, String primaryItem) {
        List<ItemAmount> out = new ArrayList<>();
        boolean primarySkipped = false;
        for (MaterialQuantity m : outputs == null ? new MaterialQuantity[0] : outputs) {
            if (m == null || m.getItemId() == null || m.getQuantity() <= 0) {
                continue;
            }
            if (!primarySkipped && m.getItemId().equals(primaryItem)) {
                primarySkipped = true;
                continue;
            }
            out.add(new ItemAmount(new ItemKey(m.getItemId()), m.getQuantity()));
        }
        return out;
    }
}
