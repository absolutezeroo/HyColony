package dev.hycolony.core.ornament.cutter;

import static dev.hycolony.core.ornament.cutter.CutterCraftTest.OAK;
import static dev.hycolony.core.ornament.cutter.CutterCraftTest.STONE;
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

    private static final Map<String, Integer> NONE = Map.of();

    private final CutterActions actions = freshCutter();

    private static CutterActions freshCutter() {
        return new CutterActions(CutterCatalog.of(CutterCatalogTest.SHAPES), TAGS);
    }

    private static List<String> openTabs(CutterActions cutter) {
        return cutter.view(NONE).tabs().stream()
                .filter(CutterView.Tab::selected)
                .map(CutterView.Tab::group)
                .toList();
    }

    @Test
    void startsOnTheFirstGroupAndItsFirstShape() {
        CutterView view = actions.view(NONE);
        assertEquals(
                List.of(true, false, false),
                view.tabs().stream().map(CutterView.Tab::selected).toList());
        assertEquals(
                "hycolony.ornament.cutter.group.avanilla",
                view.tabs().getFirst().nameKey());
        assertEquals("HyColony_DO_Slab", view.tabs().getFirst().iconKey());
        assertEquals("Slab", view.shapes().getFirst().shapeId());
        assertEquals("HyColony_DO_Slab", view.shapes().getFirst().templateKey());
        assertTrue(view.shapes().getFirst().selected());
        assertEquals(
                List.of(new CutterView.Slot("hycolony.ornament.cutter.slot.slab_materials", "", 0, 1, true)),
                view.slots());
        assertInstanceOf(CutterView.Empty.class, view.preview());
    }

    @Test
    void selectingAGroupPicksItsFirstShape() {
        actions.selectGroup(2);
        actions.selectShape(1);
        assertEquals("Shingle_Flat", actions.shape().orElseThrow().id());
        actions.selectGroup(1);
        assertEquals("TimberFrame_Plain", actions.shape().orElseThrow().id());
        assertEquals(2, actions.view(NONE).slots().size());
    }

    @Test
    void badIndexesKeepTheChosenGroupShapeAndSlot() {
        actions.selectGroup(2);
        actions.selectShape(1);
        actions.selectGroup(-1);
        actions.selectGroup(9);
        actions.selectShape(2);
        actions.selectShape(-1);
        actions.selectSlot(1);
        actions.selectSlot(5);
        assertEquals("Shingle_Flat", actions.shape().orElseThrow().id());
        assertEquals(List.of("cshingle"), openTabs(actions));
        assertTrue(actions.view(NONE).slots().get(1).selected());
    }

    @Test
    void groupFollowsTheOpenTab() {
        assertEquals(0, actions.group());
        actions.selectGroup(2);
        assertEquals(2, actions.group());
        actions.selectShape(1);
        assertEquals(2, actions.group());
        actions.selectGroup(9);
        actions.selectGroup(-1);
        assertEquals(2, actions.group());
    }

    @Test
    void reopeningOnTheRememberedGroupChoosesItsFirstShape() {
        actions.selectGroup(2);
        actions.selectShape(1);
        CutterActions reopened = freshCutter();
        reopened.selectGroup(actions.group());
        assertEquals(List.of("cshingle"), openTabs(reopened));
        assertEquals("Shingle", reopened.shape().orElseThrow().id());
    }

    @Test
    void materialsListedAreTheOwnedOnesTheSelectedSlotAccepts() {
        actions.selectGroup(1);
        Map<String, Integer> inventory = Map.of(OAK, 3, STONE, 5, "Soil_Dirt", 9);
        assertEquals(
                List.of(new CutterView.Material(OAK, 3)),
                actions.view(inventory).materials());
        actions.selectSlot(1);
        assertEquals(
                List.of(new CutterView.Material(STONE, 5)),
                actions.view(inventory).materials());
    }

    @Test
    void choosingAMaterialFillsTheSelectedSlotThenMovesToTheNextEmptyOne() {
        actions.selectGroup(1);
        Map<String, Integer> inventory = Map.of(OAK, 3, STONE, 5);
        actions.choose(OAK);
        CutterView view = actions.view(inventory);
        assertEquals(
                new CutterView.Slot("hycolony.ornament.cutter.slot.timber_frames_frame", OAK, 3, 1, false),
                view.slots().get(0));
        assertTrue(view.slots().get(1).selected());
        actions.choose(STONE);
        var ready = (CutterView.Ready) actions.view(inventory).preview();
        assertEquals("HyColony_DO_TimberFrame_Plain__Wood_Hardwood_Planks__Rock_Stone_Brick", ready.itemId());
        assertEquals("HyColony_DO_TimberFrame_Plain", ready.templateKey());
        assertEquals(4, ready.quantity());
        assertEquals(3, ready.maxCrafts());
    }

    @Test
    void aChosenMaterialNoLongerOwnedIsRefusedAsMissing() {
        actions.selectGroup(1);
        actions.choose(OAK);
        actions.choose(STONE);
        var refused = (CutterView.Refused) actions.view(Map.of(OAK, 3)).preview();
        assertEquals("hycolony.ornament.cutter.emptySlot", refused.reasonKey());
        assertEquals(List.of("2"), refused.params());
    }

    @Test
    void chosenMaterialsSurviveAShapeChangeInTheSameGroup() {
        actions.selectGroup(2);
        actions.choose(OAK);
        actions.selectShape(1);
        assertEquals(OAK, actions.view(NONE).slots().getFirst().itemId());
    }

    @Test
    void materialOutsideTheTagIsPreviewedWithTheCuttersOwnKey() {
        actions.selectGroup(1);
        actions.choose(STONE);
        actions.choose(STONE);
        var refused = (CutterView.Refused) actions.view(Map.of(STONE, 2)).preview();
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
        cutter.choose(OAK);
        var refused = (CutterView.Refused) cutter.view(Map.of(OAK, 1)).preview();
        String listed = IntStream.range(0, CutterActions.LISTED)
                .mapToObj(i -> "Mat_%02d".formatted(i))
                .collect(Collectors.joining(", "));
        assertEquals(List.of("1", listed + ", ..."), refused.params());
    }

    @Test
    void emptyCatalogShowsNothingAndChoosesNoShape() {
        var cutter = new CutterActions(CutterCatalog.of(ShapeCatalog.parse("{}")), TAGS);
        cutter.selectGroup(0);
        cutter.selectShape(0);
        cutter.choose(OAK);
        CutterView view = cutter.view(Map.of(OAK, 1));
        assertTrue(cutter.shape().isEmpty());
        assertTrue(view.tabs().isEmpty());
        assertTrue(view.shapes().isEmpty());
        assertTrue(view.slots().isEmpty());
        assertInstanceOf(CutterView.Empty.class, view.preview());
    }
}
