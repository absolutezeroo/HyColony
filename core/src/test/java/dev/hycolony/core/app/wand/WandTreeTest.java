package dev.hycolony.core.app.wand;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The pack folders the build tool walks (ST StructurePacks categories), built from the huts' folders. */
class WandTreeTest {
    private static WandTree tree() {
        Map<String, String> huts = new LinkedHashMap<>();
        huts.put("townhall", "fundamentals");
        huts.put("farmer", "agriculture/horticulture");
        huts.put("builder", "fundamentals");
        huts.put("warehouse", "craftsmanship/storage");
        return new WandTree(huts);
    }

    @Test
    void theRootFoldersAreTheFirstSegmentsInNameOrder() {
        assertEquals(List.of("agriculture", "craftsmanship", "fundamentals"), tree().roots());
    }

    @Test
    void aFolderListsItsSubfoldersOrItsHuts() {
        assertEquals(List.of("agriculture/horticulture"), tree().children("agriculture"));
        assertEquals(List.of(), tree().children("fundamentals"));
        assertEquals(List.of("townhall", "builder"), tree().huts("fundamentals"));
        assertEquals(List.of("farmer"), tree().huts("agriculture/horticulture"));
        assertEquals(List.of(), tree().huts("agriculture"));
    }

    @Test
    void onlyFoldersLeadingToAHutExist() {
        assertTrue(tree().contains("agriculture"));
        assertTrue(tree().contains("agriculture/horticulture"));
        assertFalse(tree().contains("military"));
        assertFalse(tree().contains("agri"));
        assertFalse(tree().contains(""));
    }

    @Test
    void theParentOfATopFolderIsTheRoot() {
        assertEquals("agriculture", WandTree.parent("agriculture/horticulture"));
        assertEquals("", WandTree.parent("fundamentals"));
        assertEquals("", WandTree.parent(""));
    }
}
