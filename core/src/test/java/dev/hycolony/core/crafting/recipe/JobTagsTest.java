package dev.hycolony.core.crafting.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import dev.hycolony.core.kernel.item.FoodInfo;
import dev.hycolony.core.kernel.item.ItemKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class JobTagsTest {
    private static final ItemKey BREAD = new ItemKey("Food_Bread");
    private static final ItemKey PIE = new ItemKey("Food_Pie_Apple");
    private static final FoodInfo BREAD_FOOD = new FoodInfo(6, 1, false);

    @Test
    void filesOfOneTagAddUpLikeMinecraftTags() {
        JobTags tags = JobTags.merge(
                List.of(
                        new JobTags.TagFile("chef_product", List.of(BREAD)),
                        new JobTags.TagFile("chef_product", List.of(PIE))),
                w -> fail(w));
        assertEquals(Set.of(BREAD, PIE), tags.get("chef_product"));
    }

    @Test
    void unknownTagIsSkippedWithOneWarning() {
        List<String> warnings = new ArrayList<>();
        JobTags tags = JobTags.merge(List.of(new JobTags.TagFile("chef_tools", List.of(BREAD))), warnings::add);
        assertEquals(1, warnings.size(), warnings::toString);
        assertTrue(tags.get("chef_tools").isEmpty());
    }

    @Test
    void productTagsAreFoundByTheCrafterNameLikeMcTagConstants() {
        JobTags tags = JobTags.merge(
                List.of(
                        new JobTags.TagFile("cook_product", List.of(BREAD)),
                        new JobTags.TagFile("cook_product_excluded", List.of(PIE))),
                w -> fail(w));
        assertEquals(Set.of(BREAD), tags.products("cook"));
        assertEquals(Set.of(PIE), tags.excludedProducts("cook"));
        assertTrue(tags.products("chef").isEmpty());
    }

    @Test
    void aTagIncludesAnotherTagLikeMcBakerExcludingCookProducts() {
        JobTags tags = JobTags.merge(
                List.of(
                        new JobTags.TagFile("cook_product", List.of(BREAD)),
                        new JobTags.TagFile("baker_product_excluded", List.of(PIE), List.of("cook_product"))),
                w -> fail(w));
        assertEquals(Set.of(BREAD, PIE), tags.excludedProducts("baker"));
    }

    @Test
    void tagsIncludingEachOtherStopAtTheLoop() {
        JobTags tags = JobTags.merge(
                List.of(
                        new JobTags.TagFile("cook_product", List.of(BREAD), List.of("baker_product")),
                        new JobTags.TagFile("baker_product", List.of(PIE), List.of("cook_product"))),
                w -> fail(w));
        assertEquals(Set.of(BREAD, PIE), tags.products("cook"));
        assertEquals(Set.of(BREAD, PIE), tags.products("baker"));
    }

    @Test
    void includeOfAnUnknownTagIsSkippedWithOneWarning() {
        List<String> warnings = new ArrayList<>();
        JobTags tags = JobTags.merge(
                List.of(new JobTags.TagFile("cook_product", List.of(BREAD), List.of("cook_tools"))), warnings::add);
        assertEquals(1, warnings.size(), warnings::toString);
        assertEquals(Set.of(BREAD), tags.products("cook"));
    }

    @Test
    void includeOfAKnownTagNoFileNamesIsEmptyWithOneWarning() {
        List<String> warnings = new ArrayList<>();
        JobTags tags = JobTags.merge(
                List.of(new JobTags.TagFile("cook_product", List.of(BREAD), List.of("chef_product"))), warnings::add);
        assertEquals(1, warnings.size(), warnings::toString);
        assertEquals(Set.of(BREAD), tags.products("cook"));
    }

    @Test
    void poisonousFoodTagIsReadLikeMcPoisonousfood() {
        JobTags tags = JobTags.merge(List.of(new JobTags.TagFile(JobTags.POISONOUS_FOOD, List.of(PIE))), w -> fail(w));
        assertEquals(Set.of(PIE), tags.get(JobTags.POISONOUS_FOOD));
    }

    @Test
    void anExcludedFoodIsNoFoodLikeMcIsFood() {
        JobTags tags = JobTags.merge(List.of(new JobTags.TagFile(JobTags.EXCLUDED_FOOD, List.of(PIE))), w -> fail(w));
        Map<ItemKey, FoodInfo> foods = tags.applyToFoods(Map.of(BREAD, BREAD_FOOD, PIE, new FoodInfo(12, 3, false)));
        assertEquals(Map.of(BREAD, BREAD_FOOD), foods);
    }

    @Test
    void aPoisonousFoodKeepsItsValueAndBecomesPoisonous() {
        JobTags tags =
                JobTags.merge(List.of(new JobTags.TagFile(JobTags.POISONOUS_FOOD, List.of(BREAD))), w -> fail(w));
        assertEquals(Map.of(BREAD, new FoodInfo(6, 1, true)), tags.applyToFoods(Map.of(BREAD, BREAD_FOOD)));
    }

    @Test
    void foodsAreUnchangedWithoutTags() {
        assertEquals(Map.of(BREAD, BREAD_FOOD), JobTags.EMPTY.applyToFoods(Map.of(BREAD, BREAD_FOOD)));
    }

    @Test
    void excludedFoodTagIsRead() {
        JobTags tags = JobTags.merge(List.of(new JobTags.TagFile(JobTags.EXCLUDED_FOOD, List.of(PIE))), w -> fail(w));
        assertEquals(Set.of(PIE), tags.get(JobTags.EXCLUDED_FOOD));
    }

    @Test
    void absentTagIsEmpty() {
        assertTrue(JobTags.EMPTY.get(JobTags.REDUCEABLE_INGREDIENT).isEmpty());
        assertTrue(JobTags.EMPTY.products("farmer").isEmpty());
    }

    @Test
    void mcTagsAreKnownButABareSuffixIsNot() {
        assertTrue(JobTags.isKnown(JobTags.REDUCEABLE_INGREDIENT));
        assertTrue(JobTags.isKnown(JobTags.REDUCEABLE_PRODUCT_EXCLUDED));
        assertTrue(JobTags.isKnown(JobTags.EXCLUDED_FOOD));
        assertTrue(JobTags.isKnown("farmer_product_excluded"));
        assertFalse(JobTags.isKnown("_product"));
        assertFalse(JobTags.isKnown(""));
    }
}
