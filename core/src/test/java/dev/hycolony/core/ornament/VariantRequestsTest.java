package dev.hycolony.core.ornament;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class VariantRequestsTest {
    private static final String STONE = "Rock_Stone_Brick";
    private static final String PLANKS = "Wood_Hardwood_Planks";
    private final OrnamentShape shingle =
            new OrnamentShape("Shingle", "HyColony_DO_Shingle", "cshingle", List.of("roof", "support"), false, 4);
    private final OrnamentShape fancyDoor = new OrnamentShape(
            "FancyDoor_Full", "HyColony_DO_FancyDoor_Full", "ddoor", List.of("fancy", "fancy"), true, 2);
    private final MaterialTags tags =
            new MaterialTags(Map.of("roof", Set.of(STONE), "support", Set.of(PLANKS), "fancy", Set.of(PLANKS)));

    @Test
    void acceptedRequestGivesAStableKey() {
        var result = VariantRequests.check(shingle, List.of(STONE, PLANKS), tags);
        VariantKey key = ((VariantRequests.Accepted) result).key();
        assertEquals("HyColony_DO_Shingle__Rock_Stone_Brick__Wood_Hardwood_Planks", key.blockTypeKey());
        assertEquals("Shingle|Rock_Stone_Brick|Wood_Hardwood_Planks", key.id());
    }

    @Test
    void requestOutsideTheTagIsRefused() {
        var result = VariantRequests.check(shingle, List.of("Soil_Dirt", PLANKS), tags);
        var refused = (VariantRequests.Refused) result;
        assertEquals("hycolony.ornament.badMaterial", refused.reasonKey());
        assertEquals(0, refused.slot());
        assertEquals(Set.of(STONE), refused.allowed());
    }

    @Test
    void optionalSecondSlotRepeatsTheFirst() {
        var result = VariantRequests.check(fancyDoor, List.of(PLANKS), tags);
        assertEquals(
                List.of(PLANKS, PLANKS),
                ((VariantRequests.Accepted) result).key().materials());
    }

    @Test
    void wrongMaterialCountIsRefused() {
        var result = VariantRequests.check(shingle, List.of(STONE), tags);
        assertEquals("hycolony.ornament.badCount", ((VariantRequests.Refused) result).reasonKey());
    }

    @Test
    void idReadsBackToTheSameKey() {
        ShapeCatalog catalog = ShapeCatalog.parse("""
                {"schemaVersion": 1, "shapes": [{"id": "Shingle", "template": "HyColony_DO_Shingle",
                 "group": "cshingle", "slots": ["roof", "support"], "optionalSecond": false, "cutterQuantity": 4}]}""");
        VariantKey key = VariantKey.parse("Shingle|Rock_Stone_Brick|Wood_Hardwood_Planks", catalog)
                .orElseThrow();
        assertEquals("HyColony_DO_Shingle__Rock_Stone_Brick__Wood_Hardwood_Planks", key.blockTypeKey());
        assertEquals(Optional.empty(), VariantKey.parse("Gone|X", catalog));
        assertTrue(VariantKey.parse("Shingle|OnlyOne", catalog).isEmpty());
    }
}
