package dev.hydomum.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.hydomum.api.MaterialTags;
import dev.hydomum.api.ShapeCatalog;
import dev.hydomum.api.VariantKey;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The variants created at boot: the saved ones, then those another mod requires (a style's prefabs). */
class BootVariantsTest {
    private static final String STONE = "Rock_Stone_Brick";
    private static final String PLANKS = "Wood_Hardwood_Planks";
    private final ShapeCatalog shapes = ShapeCatalog.parse("""
            {"schemaVersion": 1, "shapes": [{"id": "Shingle", "template": "HyDomum_Shingle", "group": "c",
             "slots": ["roof", "support"], "optionalSecond": false, "cutterQuantity": 4}]}""");
    private final MaterialTags tags = new MaterialTags(Map.of("roof", Set.of(STONE), "support", Set.of(PLANKS)));

    private VariantKey key(String id) {
        return VariantKey.parse(id, shapes).orElseThrow();
    }

    @Test
    void requiredVariantsComeAfterTheSavedOnesOnce() {
        VariantKey saved = key("Shingle|" + STONE + "|" + PLANKS);
        BootVariants.Result r =
                BootVariants.merge(List.of(saved), List.of(saved.id(), "Shingle|" + STONE + "|" + STONE), shapes, tags);
        assertEquals(List.of(saved), r.keys(), "the stone support is refused by its slot");
        assertEquals(List.of("Shingle|" + STONE + "|" + STONE), r.refused());
    }

    @Test
    void anUnknownShapeOrAMalformedIdIsRefusedAndTheOthersKept() {
        String good = "Shingle|" + STONE + "|" + PLANKS;
        BootVariants.Result r = BootVariants.merge(List.of(), List.of("Gone|" + STONE, "", good, good), shapes, tags);
        assertEquals(List.of(key(good)), r.keys());
        assertEquals(List.of("Gone|" + STONE, ""), r.refused());
    }
}
