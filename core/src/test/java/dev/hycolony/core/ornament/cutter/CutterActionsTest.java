package dev.hycolony.core.ornament.cutter;

import static dev.hycolony.core.ornament.cutter.CutterCraftTest.OAK;
import static dev.hycolony.core.ornament.cutter.CutterCraftTest.STONE;
import static dev.hycolony.core.ornament.cutter.CutterCraftTest.one;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.ornament.MaterialTags;
import dev.hycolony.core.ornament.ShapeCatalog;
import dev.hycolony.core.ornament.VariantKey;
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

    private final CutterActions actions = freshCutter();

    private static CutterActions freshCutter() {
        return new CutterActions(CutterCatalog.of(CutterCatalogTest.SHAPES), TAGS);
    }

    private static List<String> openTabs(CutterActions cutter) {
        return cutter.view(List.of()).tabs().stream()
                .filter(CutterView.Tab::selected)
                .map(CutterView.Tab::group)
                .toList();
    }

    @Test
    void startsOnTheFirstGroupAndItsFirstShape() {
        CutterView view = actions.view(List.of());
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
        assertEquals(List.of(new CutterView.Slot("hycolony.ornament.cutter.slot.slab_materials", false)), view.slots());
        assertInstanceOf(CutterView.Empty.class, view.preview());
    }

    @Test
    void selectingAGroupPicksItsFirstShape() {
        actions.selectGroup(2);
        actions.selectShape(1);
        assertEquals("Shingle_Flat", actions.shape().orElseThrow().id());
        actions.selectGroup(1);
        assertEquals("TimberFrame_Plain", actions.shape().orElseThrow().id());
        assertEquals(2, actions.view(List.of()).slots().size());
    }

    @Test
    void badIndexesKeepTheChosenGroupAndShape() {
        actions.selectGroup(2);
        actions.selectShape(1);
        actions.selectGroup(-1);
        actions.selectGroup(9);
        actions.selectShape(2);
        actions.selectShape(-1);
        assertEquals("Shingle_Flat", actions.shape().orElseThrow().id());
        assertEquals(List.of("cshingle"), openTabs(actions));
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
    void previewShowsTheVariantOrWhyNot() {
        actions.selectGroup(1);
        var ready =
                (CutterView.Ready) actions.view(List.of(one(OAK), one(STONE))).preview();
        assertEquals("HyColony_DO_TimberFrame_Plain__Wood_Hardwood_Planks__Rock_Stone_Brick", ready.itemId());
        assertEquals("HyColony_DO_TimberFrame_Plain", ready.templateKey());
        assertEquals(4, ready.quantity());
        assertEquals(1, ready.maxCrafts());
        var refused = (CutterView.Refused)
                actions.view(List.of(one(OAK), SlotContent.EMPTY)).preview();
        assertEquals("hycolony.ornament.cutter.emptySlot", refused.reasonKey());
        assertEquals(List.of("2"), refused.params());
    }

    @Test
    void readyPreviewCountsTheCraftsTheSlotsAllowAndAFullBatchInCreative() {
        actions.selectGroup(1);
        List<SlotContent> slots = List.of(new SlotContent(OAK, 5), new SlotContent(STONE, 3));
        assertEquals(3, ((CutterView.Ready) actions.view(slots).preview()).maxCrafts());
        assertEquals(
                CutterCraft.MAX_BATCH,
                ((CutterView.Ready) actions.view(slots, true).preview()).maxCrafts());
    }

    @Test
    void shapesShowTheVariantTheSlotsMakeOrTheirTemplate() {
        actions.selectGroup(1);
        assertEquals(
                "HyColony_DO_TimberFrame_Plain",
                actions.view(List.of()).shapes().getFirst().itemId());
        assertEquals(
                "HyColony_DO_TimberFrame_Plain__Wood_Hardwood_Planks__Rock_Stone_Brick",
                actions.view(List.of(one(OAK), one(STONE))).shapes().getFirst().itemId());
    }

    @Test
    void groupVariantsAreTheOpenGroupsShapesTheSlotsMake() {
        actions.selectGroup(2);
        assertEquals(List.of(), actions.groupVariants(List.of(one(OAK), one(STONE))), "shingles refuse these");
        actions.selectGroup(1);
        assertEquals(
                List.of("HyColony_DO_TimberFrame_Plain__Wood_Hardwood_Planks__Rock_Stone_Brick"),
                actions.groupVariants(List.of(one(OAK), one(STONE))).stream()
                        .map(VariantKey::blockTypeKey)
                        .toList());
        assertEquals(List.of(), actions.groupVariants(List.of()));
    }

    @Test
    void aSlotIsReadyWhenItHoldsAMaterialItsTagAccepts() {
        actions.selectGroup(1);
        assertEquals(List.of(true, true), ready(actions.view(List.of(one(OAK), one(STONE)))));
        assertEquals(List.of(false, true), ready(actions.view(List.of(one(STONE), one(STONE)))));
        assertEquals(List.of(true, false), ready(actions.view(List.of(one(OAK), SlotContent.EMPTY))));
        assertEquals(List.of(false, false), ready(actions.view(List.of())));
    }

    private static List<Boolean> ready(CutterView view) {
        return view.slots().stream().map(CutterView.Slot::ready).toList();
    }

    @Test
    void aSlotAcceptsOnlyItsTagForTheChosenShape() {
        actions.selectGroup(1);
        assertTrue(actions.accepts(0, OAK));
        assertFalse(actions.accepts(0, STONE));
        assertTrue(actions.accepts(1, STONE));
        assertFalse(actions.accepts(2, STONE));
        assertFalse(actions.accepts(-1, OAK));
        actions.selectGroup(0);
        assertFalse(actions.accepts(1, STONE), "the slab has one slot");
        var empty = new CutterActions(CutterCatalog.of(ShapeCatalog.parse("{}")), TAGS);
        assertFalse(empty.accepts(0, OAK));
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
        assertTrue(view.slots().isEmpty());
        assertInstanceOf(CutterView.Empty.class, view.preview());
    }
}
