package dev.hycolony.core.ornament.cutter;

import static dev.hycolony.core.ornament.cutter.CutterCraftTest.OAK;
import static dev.hycolony.core.ornament.cutter.CutterCraftTest.STONE;
import static dev.hycolony.core.ornament.cutter.CutterCraftTest.one;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.ornament.MaterialTags;
import dev.hycolony.core.ornament.ShapeCatalog;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class CutterActionsTest {
    /** The tags of {@link CutterCatalogTest#SHAPES}' timber frame and slab slots. */
    private static final MaterialTags TAGS = new MaterialTags(Map.of(
            "timber_frames_frame",
            Set.of(OAK),
            "timber_frames_center",
            Set.of(STONE),
            "slab_materials",
            Set.of(STONE)));

    private final CutterActions actions = new CutterActions(CutterCatalog.of(CutterCatalogTest.SHAPES), TAGS);

    @Test
    void startsOnTheFirstGroupAndItsFirstShape() {
        CutterView view = actions.view(List.of());
        assertEquals(
                List.of(true, false, false),
                view.tabs().stream().map(CutterView.Tab::selected).toList());
        assertEquals(
                "hycolony.ornament.cutter.group.avanilla",
                view.tabs().getFirst().nameKey());
        assertEquals("Slab", view.shapes().getFirst().shapeId());
        assertEquals("HyColony_DO_Slab", view.shapes().getFirst().templateKey());
        assertTrue(view.shapes().getFirst().selected());
        assertEquals(List.of("hycolony.ornament.cutter.slot.slab_materials"), view.slotLabelKeys());
        assertInstanceOf(CutterView.Empty.class, view.preview());
    }

    @Test
    void selectingAGroupPicksItsFirstShapeAndBadIndexesChangeNothing() {
        actions.selectGroup(2);
        actions.selectShape(1);
        assertEquals("Shingle_Flat", actions.shape().orElseThrow().id());
        actions.selectGroup(1);
        assertEquals("TimberFrame_Plain", actions.shape().orElseThrow().id());
        actions.selectGroup(9);
        actions.selectShape(-1);
        assertEquals("TimberFrame_Plain", actions.shape().orElseThrow().id());
        assertEquals(2, actions.view(List.of()).slotLabelKeys().size());
    }

    @Test
    void previewShowsTheVariantOrWhyNot() {
        actions.selectGroup(1);
        var ready =
                (CutterView.Ready) actions.view(List.of(one(OAK), one(STONE))).preview();
        assertEquals("HyColony_DO_TimberFrame_Plain__Wood_Hardwood_Planks__Rock_Stone_Brick", ready.itemId());
        assertEquals("HyColony_DO_TimberFrame_Plain", ready.templateKey());
        assertEquals(4, ready.quantity());
        var refused = (CutterView.Refused)
                actions.view(List.of(one(OAK), SlotContent.EMPTY)).preview();
        assertEquals("hycolony.ornament.cutter.emptySlot", refused.reasonKey());
        assertEquals(List.of("2"), refused.params());
    }

    @Test
    void materialOutsideTheTagIsPreviewedWithTheCuttersOwnKey() {
        actions.selectGroup(1);
        var refused = (CutterView.Refused)
                actions.view(List.of(one(STONE), one(STONE))).preview();
        assertEquals("hycolony.ornament.cutter.badMaterial", refused.reasonKey());
        assertEquals(List.of("1", OAK), refused.params());
    }

    @Test
    void refusalListsAtMostTwelveAcceptedMaterialsInOrder() {
        Set<String> many = IntStream.rangeClosed(0, 12)
                .mapToObj(i -> "Mat_%02d".formatted(i))
                .collect(Collectors.toSet());
        var cutter = new CutterActions(
                CutterCatalog.of(CutterCatalogTest.SHAPES), new MaterialTags(Map.of("slab_materials", many)));
        var refused = (CutterView.Refused) cutter.view(List.of(one(OAK))).preview();
        String listed = IntStream.range(0, CutterActions.LISTED)
                .mapToObj(i -> "Mat_%02d".formatted(i))
                .collect(Collectors.joining(", "));
        assertEquals(List.of("1", listed + ", ..."), refused.params());
    }

    @Test
    void itemsPastTheShapesSlotsLeaveThePreviewEmpty() {
        assertInstanceOf(
                CutterView.Empty.class,
                actions.view(List.of(SlotContent.EMPTY, one(STONE))).preview());
    }

    @Test
    void emptyCatalogShowsNothingAndChoosesNoShape() {
        var cutter = new CutterActions(CutterCatalog.of(ShapeCatalog.parse("{}")), TAGS);
        cutter.selectGroup(0);
        cutter.selectShape(0);
        CutterView view = cutter.view(List.of(one(OAK)));
        assertTrue(cutter.shape().isEmpty());
        assertTrue(view.tabs().isEmpty());
        assertTrue(view.shapes().isEmpty());
        assertTrue(view.slotLabelKeys().isEmpty());
        assertInstanceOf(CutterView.Empty.class, view.preview());
    }
}
