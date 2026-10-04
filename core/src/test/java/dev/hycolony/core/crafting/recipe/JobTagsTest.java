package dev.hycolony.core.crafting.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import dev.hycolony.core.kernel.item.ItemKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class JobTagsTest {
    private static final ItemKey BREAD = new ItemKey("Food_Bread");
    private static final ItemKey PIE = new ItemKey("Food_Pie_Apple");

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
    void productTagsAreFoundByTheJobIdWithoutItsNamespace() {
        JobTags tags = JobTags.merge(
                List.of(
                        new JobTags.TagFile("chef_product", List.of(BREAD)),
                        new JobTags.TagFile("chef_product_excluded", List.of(PIE))),
                w -> fail(w));
        assertEquals(Set.of(BREAD), tags.products("hycolony:chef"));
        assertEquals(Set.of(PIE), tags.excludedProducts("hycolony:chef"));
        assertEquals(Set.of(BREAD), tags.products("chef"));
    }

    @Test
    void absentTagIsEmpty() {
        assertTrue(JobTags.EMPTY.get(JobTags.REDUCEABLE_INGREDIENT).isEmpty());
        assertTrue(JobTags.EMPTY.products("hycolony:farmer").isEmpty());
    }

    @Test
    void reduceableAndJobTagsAreKnownButABareSuffixIsNot() {
        assertTrue(JobTags.isKnown(JobTags.REDUCEABLE_INGREDIENT));
        assertTrue(JobTags.isKnown(JobTags.REDUCEABLE_PRODUCT_EXCLUDED));
        assertTrue(JobTags.isKnown("farmer_product_excluded"));
        assertFalse(JobTags.isKnown("_product"));
        assertFalse(JobTags.isKnown(""));
    }
}
