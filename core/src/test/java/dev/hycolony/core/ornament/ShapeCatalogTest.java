package dev.hycolony.core.ornament;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ShapeCatalogTest {
    private static final String JSON = """
            {"schemaVersion": 1, "shapes": [
              {"id": "Shingle", "template": "HyColony_DO_Shingle", "group": "cshingle",
               "slots": ["shingles_roof", "shingles_support"], "optionalSecond": false, "cutterQuantity": 4},
              {"id": "Broken"},
              42
            ]}""";

    @Test
    void catalogReadsCompleteShapesAndSkipsBrokenOnes() {
        ShapeCatalog catalog = ShapeCatalog.parse(JSON);
        assertEquals(1, catalog.all().size());
        OrnamentShape shingle = catalog.shape("shingle").orElseThrow();
        assertEquals("HyColony_DO_Shingle", shingle.templateKey());
        assertEquals(2, shingle.slotCount());
        assertEquals(4, shingle.cutterQuantity());
    }

    @Test
    void newerManifestGivesAnEmptyCatalog() {
        assertTrue(ShapeCatalog.parse("{\"schemaVersion\": 2, \"shapes\": []}")
                .all()
                .isEmpty());
    }

    @Test
    void unreadableManifestGivesAnEmptyCatalog() {
        assertTrue(ShapeCatalog.parse("not json").all().isEmpty());
        assertTrue(ShapeCatalog.parse("[]").all().isEmpty());
    }

    @Test
    void materialTagsAcceptTheirMaterialsOnly() {
        MaterialTags tags = new MaterialTags(Map.of("roof", Set.of("Rock_Stone_Brick")));
        assertTrue(tags.accepts("roof", "Rock_Stone_Brick"));
        assertFalse(tags.accepts("roof", "Soil_Dirt"));
        assertFalse(tags.accepts("unknown", "Rock_Stone_Brick"));
        assertTrue(tags.materials("unknown").isEmpty());
    }
}
