package dev.hycolony.core.crafting.recipe;

import java.util.List;
import java.util.Objects;

/**
 * The bench a Hytale recipe is crafted at: its id, the bench categories it belongs to and the lowest bench tier that
 * makes it. {@link #FIELDCRAFT} is the hand, with no bench (MC the 2x2 grid, intermediate {@code AIR}); such a recipe
 * keeps its categories for the job filters. Deviation from MC: MC recipes know no bench tier nor category.
 */
public record BenchRequirement(String benchId, List<String> categories, int requiredTier) {
    /** Hytale's bench id for recipes made by hand. */
    public static final String FIELDCRAFT = "Fieldcraft";

    public BenchRequirement {
        Objects.requireNonNull(benchId, "benchId");
        categories = List.copyOf(categories);
        if (requiredTier < 0) {
            throw new IllegalArgumentException("requiredTier must be >= 0: " + requiredTier);
        }
    }

    /** Whether the recipe is made by hand, with no bench. */
    public boolean isFieldcraft() {
        return FIELDCRAFT.equals(benchId);
    }
}
