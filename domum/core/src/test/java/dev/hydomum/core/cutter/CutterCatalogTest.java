package dev.hydomum.core.cutter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hydomum.api.OrnamentShape;
import dev.hydomum.api.ShapeCatalog;
import java.util.List;
import org.junit.jupiter.api.Test;

class CutterCatalogTest {
    static final ShapeCatalog SHAPES = ShapeCatalog.parse("""
            {"schemaVersion": 1, "shapes": [
              {"id": "Shingle", "template": "HyColony_DO_Shingle", "group": "cshingle",
               "slots": ["shingles_roof", "shingles_support"], "cutterQuantity": 4},
              {"id": "TimberFrame_Plain", "template": "HyColony_DO_TimberFrame_Plain", "group": "btimberframe",
               "slots": ["timber_frames_frame", "timber_frames_center"], "cutterQuantity": 4},
              {"id": "Shingle_Flat", "template": "HyColony_DO_Shingle_Flat", "group": "cshingle",
               "slots": ["shingles_roof", "shingles_support"], "cutterQuantity": 4},
              {"id": "Slab", "template": "HyColony_DO_Slab", "group": "avanilla",
               "slots": ["slab_materials"], "cutterQuantity": 2}
            ]}""");

    /** Listed out of DO's order on purpose: sorting by id, or keeping this order, would both be wrong. */
    static final ShapeCatalog DO_ORDER = ShapeCatalog.parse("""
            {"schemaVersion": 1, "shapes": [
              {"id": "Custom", "template": "HyColony_DO_Custom", "group": "zzother", "slots": ["m"]},
              {"id": "Custom2", "template": "HyColony_DO_Custom2", "group": "aacustom", "slots": ["m"]},
              {"id": "Door_Full", "template": "HyColony_DO_Door_Full", "group": "ddoor", "slots": ["m"]},
              {"id": "Trapdoor_Waffle", "template": "HyColony_DO_Trapdoor_Waffle", "group": "etrapdoor",
               "slots": ["m"]},
              {"id": "Mystery", "template": "HyColony_DO_Mystery", "group": "avanilla", "slots": ["m"]},
              {"id": "Fence", "template": "HyColony_DO_Fence", "group": "avanilla", "slots": ["m"]},
              {"id": "Slab", "template": "HyColony_DO_Slab", "group": "avanilla", "slots": ["m"]},
              {"id": "Stairs", "template": "HyColony_DO_Stairs", "group": "avanilla", "slots": ["m"]},
              {"id": "TimberFrame_Plain", "template": "HyColony_DO_TimberFrame_Plain", "group": "btimberframe",
               "slots": ["m"]},
              {"id": "TimberFrame_Framed", "template": "HyColony_DO_TimberFrame_Framed", "group": "btimberframe",
               "slots": ["m"]},
              {"id": "PaperWall_Tiled", "template": "HyColony_DO_PaperWall_Tiled", "group": "hpaperwall",
               "slots": ["m"]},
              {"id": "PaperWall", "template": "HyColony_DO_PaperWall", "group": "hpaperwall", "slots": ["m"]}
            ]}""");

    private static List<String> ids(CutterCatalog catalog, String group) {
        return catalog.shapes(group).stream().map(OrnamentShape::id).toList();
    }

    @Test
    void groupsAndShapesFollowDosOrder() {
        CutterCatalog catalog = CutterCatalog.of(SHAPES);
        assertEquals(List.of("avanilla", "btimberframe", "cshingle"), catalog.groups());
        assertEquals(List.of("Shingle", "Shingle_Flat"), ids(catalog, "cshingle"));
    }

    @Test
    void unknownGroupHasNoShapes() {
        assertTrue(CutterCatalog.of(SHAPES).shapes("ilight").isEmpty());
    }

    @Test
    void groupsFollowDosSortingIndexWithTrapdoorsBeforeDoorsAndUnknownGroupsLast() {
        assertEquals(
                List.of("avanilla", "btimberframe", "etrapdoor", "ddoor", "hpaperwall", "aacustom", "zzother"),
                CutterCatalog.of(DO_ORDER).groups());
    }

    @Test
    void shapesFollowDosIndexWithinAGroup() {
        CutterCatalog catalog = CutterCatalog.of(DO_ORDER);
        assertEquals(List.of("TimberFrame_Framed", "TimberFrame_Plain"), ids(catalog, "btimberframe"));
        assertEquals(List.of("Stairs", "Slab", "Fence", "Mystery"), ids(catalog, "avanilla"));
    }

    @Test
    void shapesWithoutADoIndexGoLastInManifestOrder() {
        CutterCatalog catalog = CutterCatalog.of(DO_ORDER);
        assertEquals("Mystery", ids(catalog, "avanilla").getLast());
        assertEquals(List.of("PaperWall_Tiled", "PaperWall"), ids(catalog, "hpaperwall"));
    }
}
