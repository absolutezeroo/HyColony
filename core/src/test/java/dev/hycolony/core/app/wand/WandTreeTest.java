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
    void aDeepFolderIsReachedOneLevelAtATime() {
        WandTree deep = new WandTree(Map.of("dock", "infrastructure/boardwalk/docks"));
        assertEquals(List.of("infrastructure/boardwalk"), deep.children("infrastructure"));
        assertEquals(List.of("infrastructure/boardwalk/docks"), deep.children("infrastructure/boardwalk"));
        assertEquals(List.of("dock"), deep.huts("infrastructure/boardwalk/docks"));
    }

    /**
     * A folder with huts and subfolders: ST StructurePacks.getCategories adds the folder itself as a terminal
     * subcategory ({@code folder/.}), which lists its own huts.
     */
    @Test
    void aFolderWithHutsAndSubfoldersOffersItselfAsASubfolder() {
        Map<String, String> huts = new LinkedHashMap<>();
        huts.put("mixed", "agriculture");
        huts.put("farmer", "agriculture/horticulture");
        WandTree mixed = new WandTree(huts);
        assertEquals(List.of("agriculture/.", "agriculture/horticulture"), mixed.children("agriculture"));
        assertTrue(mixed.contains("agriculture/."));
        assertEquals(List.of(), mixed.children("agriculture/."));
        assertEquals(List.of("mixed"), mixed.huts("agriculture/."));
        assertEquals("agriculture", WandTree.parent("agriculture/."));
        assertFalse(tree().contains("fundamentals/."), "only a mixed folder has it");
    }

    @Test
    void theParentOfATopFolderIsTheRoot() {
        assertEquals("agriculture", WandTree.parent("agriculture/horticulture"));
        assertEquals("", WandTree.parent("fundamentals"));
        assertEquals("", WandTree.parent(""));
    }
}
