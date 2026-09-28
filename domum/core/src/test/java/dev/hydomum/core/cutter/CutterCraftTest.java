package dev.hydomum.core.cutter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import dev.hydomum.api.MaterialTags;
import dev.hydomum.api.OrnamentShape;
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
            "TimberFrame_Plain", "HyDomum_TimberFrame_Plain", "btimberframe", List.of("frame", "centre"), false, 4);
    private final OrnamentShape paperWall =
            new OrnamentShape("PaperWall", "HyDomum_PaperWall", "hpaperwall", List.of("frame", "centre"), false, 6);
    private final OrnamentShape fancyDoor =
            new OrnamentShape("FancyDoor_Full", "HyDomum_FancyDoor_Full", "ddoor", List.of("fancy", "fancy"), true, 2);
    private final OrnamentShape slab = new OrnamentShape("Slab", "HyDomum_Slab", "avanilla", List.of("slab"), false, 2);

    static SlotContent one(String id) {
        return new SlotContent(id, 1);
    }

    @Test
    void validMaterialsGiveDosQuantityAndConsumeOneOfEachRequiredSlot() {
        var ready = (CutterCraft.Ready) CutterCraft.check(frame, List.of(one(OAK), one(STONE)), TAGS);
        assertEquals(
                "HyDomum_TimberFrame_Plain__Wood_Hardwood_Planks__Rock_Stone_Brick",
                ready.key().blockTypeKey());
        assertEquals(4, ready.quantity());
        assertEquals(List.of(0, 1), ready.consumed());
        var wall = (CutterCraft.Ready) CutterCraft.check(paperWall, List.of(one(OAK), one(STONE)), TAGS);
        assertEquals(6, wall.quantity());
    }

    @Test
    void emptyRequiredSlotIsRefusedAndNamed() {
        var refused = (CutterCraft.Refused) CutterCraft.check(frame, List.of(one(OAK), SlotContent.EMPTY), TAGS);
        assertEquals("hydomum.ornament.cutter.emptySlot", refused.reasonKey());
        assertEquals(1, refused.slot());
        assertEquals(Set.of(STONE), refused.allowed());
    }

    @Test
    void materialOutsideTheTagIsRefusedWithItsSlot() {
        var refused = (CutterCraft.Refused) CutterCraft.check(frame, List.of(one(STONE), one(STONE)), TAGS);
        assertEquals("hydomum.ornament.badMaterial", refused.reasonKey());
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

    @Test
    void quantityIsAtLeastTheMaterialCountAsInDo() {
        OrnamentShape two = new OrnamentShape("Two", "HyDomum_Two", "g", List.of("frame", "centre"), false, 1);
        var ready = (CutterCraft.Ready) CutterCraft.check(two, List.of(one(OAK), one(STONE)), TAGS);
        assertEquals(2, ready.quantity());
    }

    @Test
    void oneMaterialShapeOfQuantityOneGivesOne() {
        OrnamentShape single = new OrnamentShape("One", "HyDomum_One", "g", List.of("slab"), false, 1);
        var ready = (CutterCraft.Ready) CutterCraft.check(single, List.of(one(STONE)), TAGS);
        assertEquals(1, ready.quantity());
    }

    @Test
    void emptyFirstSlotIsRefusedEvenWhenTheShapeCallsItOptional() {
        OrnamentShape odd = new OrnamentShape("Odd", "HyDomum_Odd", "g", List.of("slab"), true, 1);
        var refused = (CutterCraft.Refused) CutterCraft.check(odd, List.of(SlotContent.EMPTY), TAGS);
        assertEquals("hydomum.ornament.cutter.emptySlot", refused.reasonKey());
        assertEquals(0, refused.slot());
    }

    @Test
    void itemWithNoQuantityCountsAsEmpty() {
        var refused =
                (CutterCraft.Refused) CutterCraft.check(frame, List.of(one(OAK), new SlotContent(STONE, 0)), TAGS);
        assertEquals("hydomum.ornament.cutter.emptySlot", refused.reasonKey());
        assertEquals(1, refused.slot());
    }

    @Test
    void maxCraftsIsLimitedByTheSmallestConsumedSlot() {
        var ready = (CutterCraft.Ready) CutterCraft.check(frame, List.of(one(OAK), one(STONE)), TAGS);
        assertEquals(3, CutterCraft.maxCrafts(ready, List.of(new SlotContent(OAK, 3), new SlotContent(STONE, 7))));
        assertEquals(0, CutterCraft.maxCrafts(ready, List.of(new SlotContent(OAK, 3))));
        var fancy = (CutterCraft.Ready) CutterCraft.check(fancyDoor, List.of(one(OAK), one(OAK)), TAGS);
        assertEquals(
                2,
                CutterCraft.maxCrafts(fancy, List.of(new SlotContent(OAK, 5), new SlotContent(OAK, 2))),
                "each slot gives from its own stack");
        var creative = (CutterCraft.Ready) CutterCraft.check(frame, List.of(one(OAK), one(STONE)), TAGS, true);
        assertEquals(CutterCraft.MAX_BATCH, CutterCraft.maxCrafts(creative, List.of()));
    }

    @Test
    void creativePlayerCraftsWithoutConsumingTheMaterials() {
        var ready = (CutterCraft.Ready) CutterCraft.check(frame, List.of(one(OAK), one(STONE)), TAGS, true);
        assertEquals(List.of(), ready.consumed());
        assertEquals(4, ready.quantity());
        assertInstanceOf(
                CutterCraft.Refused.class, CutterCraft.check(frame, List.of(one(OAK), SlotContent.EMPTY), TAGS, true));
    }
}
