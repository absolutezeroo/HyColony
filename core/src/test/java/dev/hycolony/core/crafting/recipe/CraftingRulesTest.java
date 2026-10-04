package dev.hycolony.core.crafting.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.hycolony.core.kernel.config.JsonFragments;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** crafting.json: what each job may learn (MC crafterProduct tags) and its custom and reduceable recipes. */
class CraftingRulesTest {
    static final String JSON = """
        {"jobs":{"farmer":{
            "allow":[{"bench":"Farmingbench","categories":["*"]},{"bench":"Fieldcraft","categories":["Seeds"]}],
            "custom":[{"id":"farmer_wheat_seeds","hytaleRecipe":"Plant_Seeds_Wheat",
                       "minBuildingLevel":1,"maxBuildingLevel":5}]}}}
        """;

    static final JobTags TAGS = tags(
            new JobTags.TagFile("farmer_product", List.of(new ItemKey("Food_Bread"))),
            new JobTags.TagFile("farmer_product_excluded", List.of(new ItemKey("Plant_Sapling_Oak"))),
            new JobTags.TagFile(JobTags.REDUCEABLE_INGREDIENT, List.of(RecipeFixtures.ESSENCE)),
            new JobTags.TagFile(JobTags.REDUCEABLE_PRODUCT_EXCLUDED, List.of(new ItemKey("Plant_Seeds_Wheat"))));

    final CraftingRules rules = CraftingRules.parse(json(JSON), w -> fail(w)).withTags(TAGS);

    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    private static JobTags tags(JobTags.TagFile... files) {
        return JobTags.merge(List.of(files), w -> fail(w));
    }

    @Test
    void jobMayLearnEveryCategoryOfAnAllowedBench() {
        assertTrue(rules.allows("farmer", "farmer", RecipeFixtures.at("Farmingbench", "Anything", "Plant_Seeds_Corn")));
    }

    @Test
    void excludedOutputIsRefusedEvenOnAnAllowedBench() {
        assertFalse(
                rules.allows("farmer", "farmer", RecipeFixtures.at("Farmingbench", "Saplings", "Plant_Sapling_Oak")));
    }

    @Test
    void includedOutputIsAllowedOnAnyBench() {
        assertTrue(rules.allows("farmer", "farmer", RecipeFixtures.at("Cookingbench", "Bread", "Food_Bread")));
    }

    @Test
    void exclusionWinsOverInclusionLikeMc() {
        CraftingRules both = CraftingRules.parse(json("{\"jobs\":{\"farmer\":{}}}"), w -> fail(w))
                .withTags(tags(
                        new JobTags.TagFile("farmer_product", List.of(new ItemKey("Food_Bread"))),
                        new JobTags.TagFile("farmer_product_excluded", List.of(new ItemKey("Food_Bread")))));
        assertFalse(both.allows("farmer", "farmer", RecipeFixtures.at("Cookingbench", "Bread", "Food_Bread")));
    }

    @Test
    void theTagsAreTheCrafterNamesNotTheJobIdsLikeMcChefReadingCookTags() {
        CraftingRules r = CraftingRules.parse(json("{\"jobs\":{\"hycolony:chef\":{}}}"), w -> fail(w))
                .withTags(tags(
                        new JobTags.TagFile("cook_product", List.of(new ItemKey("Food_Bread"))),
                        new JobTags.TagFile("chef_product", List.of(new ItemKey("Food_Pie_Apple")))));
        assertTrue(r.allows("hycolony:chef", "cook", RecipeFixtures.at("Cookingbench", "Bread", "Food_Bread")));
        assertFalse(r.allows("hycolony:chef", "cook", RecipeFixtures.at("Cookingbench", "Pie", "Food_Pie_Apple")));
    }

    @Test
    void withoutTagsNothingIsIncludedNorReduceable() {
        CraftingRules plain = CraftingRules.parse(json(JSON), w -> fail(w));
        assertFalse(plain.allows("farmer", "farmer", RecipeFixtures.at("Cookingbench", "Bread", "Food_Bread")));
        assertFalse(plain.isReduceable(RecipeFixtures.ESSENCE));
        assertTrue(plain.allows("farmer", "farmer", RecipeFixtures.at("Farmingbench", "Anything", "Plant_Seeds_Corn")));
    }

    @Test
    void oldTagListsInTheFileAreIgnoredWithAWarning() {
        List<String> warnings = new ArrayList<>();
        CraftingRules r = CraftingRules.parse(json("""
            {"jobs":{"farmer":{"includeItems":["Food_Bread"],"excludeItems":[]}},
             "reduceable":{"ingredients":["Ingredient_Life_Essence"]}}
            """), warnings::add);
        assertEquals(3, warnings.size(), warnings::toString);
        assertFalse(r.allows("farmer", "farmer", RecipeFixtures.at("Cookingbench", "Bread", "Food_Bread")));
        assertFalse(r.isReduceable(RecipeFixtures.ESSENCE));
    }

    @Test
    void fieldcraftRecipeFollowsItsCategories() {
        assertTrue(rules.allows("farmer", "farmer", RecipeFixtures.fieldcraft("Seeds", "Plant_Seeds_Wheat")));
        assertFalse(rules.allows("farmer", "farmer", RecipeFixtures.fieldcraft("Tools", "Tool_Hoe_Crude")));
    }

    @Test
    void allowedCategoriesMustCoverEveryCategoryOfTheRecipe() {
        Recipe seedsAndTools = new Recipe(
                List.of(new Ingredient.OfItem(RecipeFixtures.ESSENCE, 1)),
                new ItemAmount(new ItemKey("Plant_Seeds_Odd"), 1),
                List.of(),
                new BenchRequirement(BenchRequirement.FIELDCRAFT, List.of("Seeds", "Tools"), 0),
                Optional.empty(),
                new RecipeSource.Hytale("Plant_Seeds_Odd"),
                false);
        assertFalse(rules.allows("farmer", "farmer", seedsAndTools));
    }

    @Test
    void unknownJobMayLearnNothing() {
        assertFalse(rules.allows("miner", "miner", RecipeFixtures.at("Farmingbench", "Seeds", "Plant_Seeds_Corn")));
    }

    @Test
    void customRecipesAreListedPerJob() {
        assertEquals(
                List.of(new CraftingRules.CustomRecipe("farmer_wheat_seeds", "Plant_Seeds_Wheat", 1, 5)),
                rules.custom("farmer"));
        assertTrue(rules.custom("miner").isEmpty());
    }

    @Test
    void customRecipeLevelsDefaultToMcBounds() {
        CraftingRules r = CraftingRules.parse(
                json("{\"jobs\":{\"farmer\":{\"custom\":[{\"id\":\"a\",\"hytaleRecipe\":\"B\"}]}}}"), w -> fail(w));
        assertEquals(List.of(new CraftingRules.CustomRecipe("a", "B", 0, 5)), r.custom("farmer"));
    }

    @Test
    void reduceableIngredientsAndExcludedProductsAreRead() {
        assertTrue(rules.isReduceable(RecipeFixtures.ESSENCE));
        assertFalse(rules.isReduceable(new ItemKey("Rock_Stone")));
        assertTrue(rules.isExcludedFromReduction(new ItemKey("Plant_Seeds_Wheat")));
        assertFalse(rules.isExcludedFromReduction(new ItemKey("Plant_Seeds_Corn")));
    }

    @Test
    void emptyFileMeansNoRules() {
        CraftingRules none = CraftingRules.parse(json("{}"), w -> fail(w));
        assertFalse(none.allows("farmer", "farmer", RecipeFixtures.at("Farmingbench", "Seeds", "Plant_Seeds_Corn")));
        assertFalse(none.isReduceable(RecipeFixtures.ESSENCE));
        assertFalse(CraftingRules.EMPTY.allows(
                "farmer", "farmer", RecipeFixtures.fieldcraft("Seeds", "Plant_Seeds_Wheat")));
    }

    @Test
    void badEntryIsSkippedWithOneWarning() {
        List<String> warnings = new ArrayList<>();
        CraftingRules r =
                CraftingRules.parse(json("{\"jobs\":{\"farmer\":{\"allow\":[{\"categories\":[]}]}}}"), warnings::add);
        assertEquals(1, warnings.size());
        assertFalse(r.allows("farmer", "farmer", RecipeFixtures.at("Farmingbench", "Seeds", "Plant_Seeds_Corn")));
    }

    @Test
    void subPluginFragmentAddsJobsAndKeysButNeverRedefinesOne() {
        // As the plugin merges crafting.json (SubPlugins.CRAFTING_DEPTH): job by job, then key by key in a job.
        JsonFragments file = new JsonFragments(2);
        file.add("HyColony", json("""
            {"jobs":{"farmer":{"allow":[{"bench":"Farmingbench","categories":["*"]}]}}}
            """));

        List<JsonFragments.Conflict> conflicts = file.add("Pack", json("""
            {"jobs":{"farmer":{"allow":[]},
                     "baker":{"allow":[{"bench":"Cookingbench","categories":["*"]}]}}}
            """));
        CraftingRules merged = CraftingRules.parse(file.merged(), w -> fail(w));

        assertEquals(List.of(new JsonFragments.Conflict("jobs/farmer/allow", "HyColony", "Pack")), conflicts);
        assertTrue(merged.allows("farmer", "farmer", RecipeFixtures.at("Farmingbench", "Seeds", "Plant_Seeds_Corn")));
        assertTrue(merged.allows("baker", "baker", RecipeFixtures.at("Cookingbench", "Pie", "Food_Pie_Apple")));
    }

    @Test
    void eachBadEntryWarnsOnceAndTheRestIsKept() {
        List<String> warnings = new ArrayList<>();
        CraftingRules r = CraftingRules.parse(json("""
            {"jobs":{"farmer":{
                "allow":[5,{"bench":"Farmingbench","categories":["Seeds",3]},{"bench":"Fieldcraft"}],
                "includeItems":["Food_Bread",{}],
                "custom":[{"id":"a"},{"id":"b","hytaleRecipe":"B","minBuildingLevel":"x"}]},
              "miner":"nope"},
             "reduceable":{"ingredients":"Ingredient_Life_Essence"}}
            """), warnings::add);
        assertEquals(7, warnings.size(), warnings::toString);
        assertFalse(r.allows("farmer", "farmer", RecipeFixtures.at("Farmingbench", "Seeds", "Plant_Seeds_Corn")));
        assertTrue(r.custom("farmer").isEmpty());
    }
}
