package dev.hycolony.core.crafting.recipe;

import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ToolType;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * MC RecipeStorage: what a hut can craft, as Hytale describes it (inputs, outputs, bench, tool). {@code
 * secondaryOutputs} are the other outputs of one run (a bucket given back); {@code knowledgeRequired} marks a Hytale
 * recipe only players who learnt it may use.
 */
public record Recipe(
        List<Ingredient> inputs,
        ItemAmount primaryOutput,
        List<ItemAmount> secondaryOutputs,
        BenchRequirement bench,
        Optional<ToolType> requiredTool,
        RecipeSource source,
        boolean knowledgeRequired) {
    public Recipe {
        inputs = List.copyOf(inputs);
        Objects.requireNonNull(primaryOutput, "primaryOutput");
        secondaryOutputs = List.copyOf(secondaryOutputs);
        Objects.requireNonNull(bench, "bench");
        Objects.requireNonNull(requiredTool, "requiredTool");
        Objects.requireNonNull(source, "source");
    }

    /**
     * MC RecipeStorage.getCleanedInput: the inputs with equal ingredients merged and their amounts summed. As in MC
     * {@code processInputsAndTools}, a merged ingredient leaves its place and goes to the end of the list.
     */
    public List<Ingredient> cleanedInput() {
        List<Ingredient> items = new ArrayList<>(inputs.size());
        for (Ingredient in : inputs) {
            Ingredient merged = in;
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i).sameThingAs(in)) {
                    merged = in.withAmount(items.remove(i).amount() + in.amount());
                    break;
                }
            }
            items.add(merged);
        }
        return List.copyOf(items);
    }

    /**
     * MC RecipeStorage.equals: same cleaned input, outputs, bench and tool. Deviation from MC: the source is ignored,
     * where MC also compares {@code recipeSource}; {@link RecipeRegistry} adds it back to find a recipe's id.
     */
    public boolean sameContentAs(Recipe other) {
        return content().equals(other.content());
    }

    /** What {@link #sameContentAs} compares, as one value a map can key on (MC RecipeStorage.hashCode). */
    Content content() {
        return new Content(cleanedInput(), primaryOutput, secondaryOutputs, bench, requiredTool);
    }

    /** A recipe's content without its source and knowledge flag. */
    record Content(
            List<Ingredient> cleanedInput,
            ItemAmount primaryOutput,
            List<ItemAmount> secondaryOutputs,
            BenchRequirement bench,
            Optional<ToolType> requiredTool) {}
}
