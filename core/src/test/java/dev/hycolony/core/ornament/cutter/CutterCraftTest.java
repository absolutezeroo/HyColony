package dev.hycolony.core.ornament.cutter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import dev.hycolony.core.ornament.MaterialTags;
import dev.hycolony.core.ornament.OrnamentShape;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CutterCraftTest {
    static final String STONE = "Rock_Stone_Brick";
    static final String OAK = "Wood_Hardwood_Planks";
    static final MaterialTags TAGS = new MaterialTags(
            Map.of("frame", Set.of(OAK), "centre", Set.of(STONE), "fancy", Set.of(OAK), "slab", Set.of(STONE)));
    private final OrnamentShape frame = new OrnamentShape(
            "TimberFrame_Plain", "HyColony_DO_TimberFrame_Plain", "btimberframe", List.of("frame", "centre"), false, 4);
    private final OrnamentShape paperWall =
            new OrnamentShape("PaperWall", "HyColony_DO_PaperWall", "hpaperwall", List.of("frame", "centre"), false, 6);
    private final OrnamentShape fancyDoor = new OrnamentShape(
            "FancyDoor_Full", "HyColony_DO_FancyDoor_Full", "ddoor", List.of("fancy", "fancy"), true, 2);
    private final OrnamentShape slab =
            new OrnamentShape("Slab", "HyColony_DO_Slab", "avanilla", List.of("slab"), false, 2);

    static SlotContent one(String id) {
        return new SlotContent(id, 1);
    }

    @Test
    void validMaterialsGiveDosQuantityAndConsumeOneOfEachRequiredSlot() {
        var ready = (CutterCraft.Ready) CutterCraft.check(frame, List.of(one(OAK), one(STONE)), TAGS);
        assertEquals(
                "HyColony_DO_TimberFrame_Plain__Wood_Hardwood_Planks__Rock_Stone_Brick",
                ready.key().blockTypeKey());
        assertEquals(4, ready.quantity());
        assertEquals(List.of(0, 1), ready.consumed());
        var wall = (CutterCraft.Ready) CutterCraft.check(paperWall, List.of(one(OAK), one(STONE)), TAGS);
        assertEquals(6, wall.quantity());
    }

    @Test
    void emptyRequiredSlotIsRefusedAndNamed() {
        var refused = (CutterCraft.Refused) CutterCraft.check(frame, List.of(one(OAK), SlotContent.EMPTY), TAGS);
        assertEquals("hycolony.ornament.cutter.emptySlot", refused.reasonKey());
        assertEquals(1, refused.slot());
        assertEquals(Set.of(STONE), refused.allowed());
    }

    @Test
    void materialOutsideTheTagIsRefusedWithItsSlot() {
        var refused = (CutterCraft.Refused) CutterCraft.check(frame, List.of(one(STONE), one(STONE)), TAGS);
        assertEquals("hycolony.ornament.badMaterial", refused.reasonKey());
        assertEquals(0, refused.slot());
        assertEquals(Set.of(OAK), refused.allowed());
    }

    @Test
    void emptyOptionalSecondRepeatsTheFirstAndConsumesOnlyIt() {
        var ready = (CutterCraft.Ready) CutterCraft.check(fancyDoor, List.of(one(OAK), SlotContent.EMPTY), TAGS);
        assertEquals(List.of(OAK, OAK), ready.key().materials());
        assertEquals(List.of(0), ready.consumed());
        assertEquals(2, ready.quantity());
    }

    @Test
    void secondSlotOfAOneMaterialShapeIsIgnoredAndKept() {
        var ready = (CutterCraft.Ready) CutterCraft.check(slab, List.of(one(STONE), one("Soil_Dirt")), TAGS);
        assertEquals(List.of(0), ready.consumed());
        assertEquals(2, ready.quantity());
    }

    @Test
    void missingSlotsCountAsEmpty() {
        assertInstanceOf(CutterCraft.Refused.class, CutterCraft.check(frame, List.of(one(OAK)), TAGS));
    }

    @Test
    void reValidationOnChangedSlotsGivesAnotherAnswer() {
        assertInstanceOf(CutterCraft.Ready.class, CutterCraft.check(frame, List.of(one(OAK), one(STONE)), TAGS));
        assertInstanceOf(
                CutterCraft.Refused.class, CutterCraft.check(frame, List.of(one(OAK), SlotContent.EMPTY), TAGS));
    }
}
