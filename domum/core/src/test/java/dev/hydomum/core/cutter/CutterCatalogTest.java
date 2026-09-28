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
              {"id": "Shingle", "template": "HyDomum_Shingle", "group": "cshingle",
               "slots": ["shingles_roof", "shingles_support"], "cutterQuantity": 4},
              {"id": "TimberFrame_Plain", "template": "HyDomum_TimberFrame_Plain", "group": "btimberframe",
               "slots": ["timber_frames_frame", "timber_frames_center"], "cutterQuantity": 4},
              {"id": "Shingle_Flat", "template": "HyDomum_Shingle_Flat", "group": "cshingle",
               "slots": ["shingles_roof", "shingles_support"], "cutterQuantity": 4},
              {"id": "Slab", "template": "HyDomum_Slab", "group": "avanilla",
               "slots": ["slab_materials"], "cutterQuantity": 2}
            ]}""");

    /** Listed out of DO's order on purpose: sorting by id, or keeping this order, would both be wrong. */
    static final ShapeCatalog DO_ORDER = ShapeCatalog.parse("""
            {"schemaVersion": 1, "shapes": [
              {"id": "Custom", "template": "HyDomum_Custom", "group": "zzother", "slots": ["m"]},
              {"id": "Custom2", "template": "HyDomum_Custom2", "group": "aacustom", "slots": ["m"]},
              {"id": "Door_Full", "template": "HyDomum_Door_Full", "group": "ddoor", "slots": ["m"]},
              {"id": "Trapdoor_Waffle", "template": "HyDomum_Trapdoor_Waffle", "group": "etrapdoor",
               "slots": ["m"]},
              {"id": "Mystery", "template": "HyDomum_Mystery", "group": "avanilla", "slots": ["m"]},
              {"id": "Fence", "template": "HyDomum_Fence", "group": "avanilla", "slots": ["m"]},
              {"id": "Slab", "template": "HyDomum_Slab", "group": "avanilla", "slots": ["m"]},
              {"id": "Stairs", "template": "HyDomum_Stairs", "group": "avanilla", "slots": ["m"]},
              {"id": "TimberFrame_Plain", "template": "HyDomum_TimberFrame_Plain", "group": "btimberframe",
               "slots": ["m"]},
              {"id": "TimberFrame_Framed", "template": "HyDomum_TimberFrame_Framed", "group": "btimberframe",
               "slots": ["m"]},
              {"id": "PaperWall_Tiled", "template": "HyDomum_PaperWall_Tiled", "group": "hpaperwall",
               "slots": ["m"]},
              {"id": "PaperWall", "template": "HyDomum_PaperWall", "group": "hpaperwall", "slots": ["m"]}
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
