package dev.hycolony.core.crafting.recipe;

import com.google.gson.JsonObject;
import dev.hycolony.core.kernel.item.ItemKey;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The rules of {@code hycolony/crafting.json}: which recipes each job may learn (MC the {@code crafterProduct} and
 * {@code crafterProductExclusions} item tags), the custom recipes its hut gets by level (MC CustomRecipe) and what a
 * recipe improvement may reduce (MC the {@code crafterIngredient} and {@code crafterProductExclusions} tags of
 * {@code CRAFTING_REDUCEABLE}). Deviation from MC: a job allows benches and their categories rather than a list of
 * products, since every Hytale recipe names its bench.
 */
public final class CraftingRules {
    /** No job may learn anything, nothing is reduceable. */
    public static final CraftingRules EMPTY = new CraftingRules(Map.of(), Set.of(), Set.of());

    /** A bench category entry that stands for every category. */
    static final String ANY_CATEGORY = "*";

    /**
     * A recipe given to a job's hut while its level is within {@code [minBuildingLevel, maxBuildingLevel]} (MC
     * CustomRecipe min/max building level): for now an existing Hytale recipe, by its Hytale id.
     */
    public record CustomRecipe(String id, String hytaleRecipe, int minBuildingLevel, int maxBuildingLevel) {}

    /** One bench a job may learn from, and which of its categories ({@code "*"} = all). */
    record Allow(String bench, Set<String> categories) {
        Allow {
            categories = Set.copyOf(categories);
        }

        boolean accepts(BenchRequirement required) {
            return bench.equals(required.benchId())
                    && (categories.contains(ANY_CATEGORY) || categories.containsAll(required.categories()));
        }
    }

    /** What one job may learn, and the custom recipes its hut gets. */
    record JobRules(List<Allow> allow, Set<ItemKey> include, Set<ItemKey> exclude, List<CustomRecipe> custom) {
        JobRules {
            allow = List.copyOf(allow);
            include = Set.copyOf(include);
            exclude = Set.copyOf(exclude);
            custom = List.copyOf(custom);
        }
    }

    private final Map<String, JobRules> jobs;
    private final Set<ItemKey> reduceable;
    private final Set<ItemKey> excludedFromReduction;

    CraftingRules(Map<String, JobRules> jobs, Set<ItemKey> reduceable, Set<ItemKey> excludedFromReduction) {
        this.jobs = Map.copyOf(jobs);
        this.reduceable = Set.copyOf(reduceable);
        this.excludedFromReduction = Set.copyOf(excludedFromReduction);
    }

    /**
     * Reads {@code crafting.json}. Tolerant: a missing key reads as empty, and each invalid entry is skipped after one
     * call to {@code warn}.
     */
    public static CraftingRules parse(JsonObject json, Consumer<String> warn) {
        return new CraftingRulesJson(warn).read(json);
    }

    /**
     * Whether {@code jobId} may learn {@code recipe}. As MC CraftingUtils.getProductValidatorBasedOnTags, an excluded
     * output is refused first and an included one allowed; otherwise the recipe's bench and categories must be
     * allowed. A job absent from the file may learn nothing (MC BuildingFarmer: {@code orElse(false)}).
     */
    public boolean allows(String jobId, Recipe recipe) {
        JobRules job = jobs.get(jobId);
        if (job == null) {
            return false;
        }
        ItemKey output = recipe.primaryOutput().item();
        if (job.exclude().contains(output)) {
            return false;
        }
        return job.include().contains(output) || job.allow().stream().anyMatch(a -> a.accepts(recipe.bench()));
    }

    /** The custom recipes of {@code jobId}; empty for a job absent from the file. */
    public List<CustomRecipe> custom(String jobId) {
        JobRules job = jobs.get(jobId);
        return job == null ? List.of() : job.custom();
    }

    /** Whether an improvement may take one of this ingredient off a recipe (MC crafterIngredient reduceable tag). */
    public boolean isReduceable(ItemKey ingredient) {
        return reduceable.contains(ingredient);
    }

    /** Whether a recipe making this is never improved (MC crafterProductExclusions reduceable tag). */
    public boolean isExcludedFromReduction(ItemKey product) {
        return excludedFromReduction.contains(product);
    }
}
