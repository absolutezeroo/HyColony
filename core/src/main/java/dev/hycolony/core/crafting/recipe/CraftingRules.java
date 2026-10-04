package dev.hycolony.core.crafting.recipe;

import com.google.gson.JsonObject;
import dev.hycolony.core.kernel.item.ItemKey;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * What each job may learn and improve: the rules of {@code hycolony/crafting.json} (the benches and categories a job
 * learns from, and the custom recipes its hut gets by level, MC CustomRecipe) and the job tags (MC the
 * {@code crafterProduct}, {@code crafterProductExclusions} and {@code CRAFTING_REDUCEABLE} item tags, {@link JobTags}).
 * Deviation from MC: a job allows benches and their categories rather than a list of products, since every Hytale
 * recipe names its bench.
 */
public final class CraftingRules {
    /** No job may learn anything, nothing is reduceable. */
    public static final CraftingRules EMPTY = new CraftingRules(Map.of(), JobTags.EMPTY);

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

    /** What one job may learn by the file, and the custom recipes its hut gets. */
    record JobRules(List<Allow> allow, List<CustomRecipe> custom) {
        JobRules {
            allow = List.copyOf(allow);
            custom = List.copyOf(custom);
        }
    }

    private final Map<String, JobRules> jobs;
    private final JobTags tags;

    CraftingRules(Map<String, JobRules> jobs, JobTags tags) {
        this.jobs = Map.copyOf(jobs);
        this.tags = tags;
    }

    /**
     * Reads {@code crafting.json}, without job tags (see {@link #withTags}). Tolerant: a missing key reads as empty,
     * and each invalid entry is skipped after one call to {@code warn}.
     */
    public static CraftingRules parse(JsonObject json, Consumer<String> warn) {
        return new CraftingRulesJson(warn).read(json);
    }

    /** These file rules with {@code tags}, read once the assets are loaded (spec 2026-10-04 § 6.3). */
    public CraftingRules withTags(JobTags tags) {
        return new CraftingRules(jobs, tags);
    }

    /**
     * Whether {@code jobId}, whose crafting module reads the tags of {@code crafter} (MC TagConstants.CRAFTING_*: the
     * chef reads {@code cook}), may learn {@code recipe}. As MC CraftingUtils.getProductValidatorBasedOnTags, an output
     * in the crafter's product exclusion tag is refused first and one in its product tag allowed; otherwise the
     * recipe's bench and categories must be allowed. A job absent from the file may learn nothing (MC BuildingFarmer:
     * {@code orElse(false)}). Deviation from MC: the ingredient tags ({@code <crafter>_ingredient}, MC
     * CraftingUtils.isRecipeCompatibleBasedOnTags) are not read yet.
     */
    public boolean allows(String jobId, String crafter, Recipe recipe) {
        JobRules job = jobs.get(jobId);
        if (job == null) {
            return false;
        }
        ItemKey output = recipe.primaryOutput().item();
        if (tags.excludedProducts(crafter).contains(output)) {
            return false;
        }
        return tags.products(crafter).contains(output) || job.allow().stream().anyMatch(a -> a.accepts(recipe.bench()));
    }

    /** The custom recipes of {@code jobId}; empty for a job absent from the file. */
    public List<CustomRecipe> custom(String jobId) {
        JobRules job = jobs.get(jobId);
        return job == null ? List.of() : job.custom();
    }

    /** Whether an improvement may take one of this ingredient off a recipe (MC crafterIngredient reduceable tag). */
    public boolean isReduceable(ItemKey ingredient) {
        return tags.get(JobTags.REDUCEABLE_INGREDIENT).contains(ingredient);
    }

    /** Whether a recipe making this is never improved (MC crafterProductExclusions reduceable tag). */
    public boolean isExcludedFromReduction(ItemKey product) {
        return tags.get(JobTags.REDUCEABLE_PRODUCT_EXCLUDED).contains(product);
    }
}
