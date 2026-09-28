package dev.hycolony.core.ornament.cutter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.ornament.OrnamentShape;
import dev.hycolony.core.ornament.ShapeCatalog;
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

    @Test
    void groupsFollowDosOrderAndKeepManifestOrderWithin() {
        CutterCatalog catalog = CutterCatalog.of(SHAPES);
        assertEquals(List.of("avanilla", "btimberframe", "cshingle"), catalog.groups());
        assertEquals(
                List.of("Shingle", "Shingle_Flat"),
                catalog.shapes("cshingle").stream().map(OrnamentShape::id).toList());
    }

    @Test
    void unknownGroupHasNoShapes() {
        assertTrue(CutterCatalog.of(SHAPES).shapes("ilight").isEmpty());
    }
}
