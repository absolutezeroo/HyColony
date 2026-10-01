package dev.hycolony.core.app.wand;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.app.ui.FoundColonyView;
import dev.hycolony.core.app.ui.WandView;
import dev.hycolony.core.building.BuildingTypes;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ColonyEvents;
import dev.hycolony.core.construction.blueprint.Blueprint;
import dev.hycolony.core.construction.blueprint.BlueprintSource;
import dev.hycolony.core.construction.hut.ConstructionBuildingTypes;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.BlockKey;
import dev.hycolony.core.kernel.item.BlockKind;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.port.Msg;
import dev.hycolony.core.testing.FakeBlueprints;
import dev.hycolony.core.testing.FakeNotifier;
import dev.hycolony.core.testing.FakePreviews;
import dev.hycolony.core.testing.TestContexts;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WandActionsTest {
    private static final String BUILDER = ConstructionBuildingTypes.BUILDER.id();
    private static final String TOWN_HALL = BuildingTypes.TOWN_HALL.id();
    private static final ItemKey BUILDER_ITEM = new ItemKey("item:hut.builder");
    /** A second style, with no plan at all. */
    private static final String NORDIC = "nordic";

    /** Every rotation the plan was loaded with. */
    private final List<Integer> rotations = new ArrayList<>();

    private final TestContexts t = contexts();
    private final ColonyManager manager = t.manager();
    private final FakePreviews previews = new FakePreviews();
    private final WandActions wand =
            new WandActions(manager, previews, k -> new ItemKey("item:" + k), k -> new BlockKey("block:" + k));
    private final UUID alice = UUID.randomUUID();
    private final BlockPos spot = new BlockPos(10, 64, 10);
    private final Colony colony = found();

    private TestContexts contexts() {
        TestContexts c = new TestContexts();
        FakeBlueprints plans = new FakeBlueprints()
                .put(BUILDER, 1, FakeBlueprints.hut(false))
                .put(TOWN_HALL, 1, FakeBlueprints.hut(false));
        c.blueprints = new BlueprintSource() {
            @Override
            public Optional<Blueprint> load(String style, String buildingTypeId, int level, int rotation) {
                rotations.add(rotation);
                return FakeBlueprints.STYLE.equals(style)
                        ? plans.load(style, buildingTypeId, level, rotation)
                        : Optional.empty();
            }

            @Override
            public List<String> styles() {
                return List.of(FakeBlueprints.STYLE, NORDIC);
            }
        };
        return c;
    }

    private Colony found() {
        manager.foundation().begin(alice, "Alice", new BlockPos(0, 64, 0), 0);
        Colony c = manager.foundation().confirm(alice, "Rivendell").orElseThrow();
        t.notifier.sent.clear();
        return c;
    }

    private void give(ItemKey item) {
        t.playerInventory.inventories.put(alice, new LinkedHashMap<>(Map.of(item, 1)));
    }

    private WandView view() {
        return assertInstanceOf(WandView.class, t.ui.shown.get(alice));
    }

    private Optional<FakePreviews.Shown> ghost() {
        return Optional.ofNullable(previews.of(alice).get("wand"));
    }

    private List<String> sentKeys() {
        return t.notifier.sent.stream()
                .map(FakeNotifier.Sent::msg)
                .map(Msg::key)
                .toList();
    }

    /** Opens on {@link #spot} with the medieval style and the builder hut chosen, in survival with the hut block. */
    private void chooseBuilder() {
        give(BUILDER_ITEM);
        wand.open(alice, Optional.of(spot));
        wand.selectStyle(alice, FakeBlueprints.STYLE);
        wand.selectBuilding(alice, BUILDER);
    }

    @Test
    void openOnABlockAnchorsThere() {
        assertTrue(wand.open(alice, Optional.of(spot)));
        chooseBuilder();
        assertEquals(spot, ghost().orElseThrow().origin());
    }

    @Test
    void openInTheAirWithoutAnchorSaysMissingPosAndOpensNothing() {
        assertFalse(wand.open(alice, Optional.empty()));
        assertEquals(List.of("hycolony.wand.missingPos"), sentKeys());
        assertFalse(t.ui.shown.containsKey(alice));
    }

    @Test
    void openInTheAirKeepsThePreviousAnchor() {
        chooseBuilder();
        t.ui.close(alice);
        assertTrue(wand.open(alice, Optional.empty()));
        assertEquals(BUILDER, view().buildingTypeId());
        assertEquals(spot, ghost().orElseThrow().origin());
    }

    @Test
    void offersOnlyHutsWithAPlanInTheStyle() {
        t.players.creative.add(alice);
        wand.open(alice, Optional.of(spot));
        wand.selectStyle(alice, FakeBlueprints.STYLE);
        wand.openCategory(alice, "fundamentals");
        assertEquals(Set.of(BUILDER, TOWN_HALL), Set.copyOf(view().hutIds()));
        assertTrue(wand.selectBuilding(alice, TOWN_HALL));
        wand.selectStyle(alice, NORDIC);
        assertTrue(view().categories().isEmpty());
        assertFalse(wand.selectBuilding(alice, BUILDER));
    }

    @Test
    void switchingToAStyleWithoutTheChosenHutClearsTheSelection() {
        chooseBuilder();
        assertTrue(wand.selectStyle(alice, FakeBlueprints.STYLE));
        assertEquals(BUILDER, view().buildingTypeId());
        assertTrue(wand.selectStyle(alice, NORDIC));
        assertEquals("", view().buildingTypeId());
        assertFalse(view().manipulate());
        assertTrue(ghost().isEmpty());
    }

    @Test
    void choosingAHutShowsTheFullPlanPreview() {
        t.catalog.kinds.put(FakeBlueprints.TORCH, BlockKind.AIR);
        // A block already in place stays in the ghost: MC draws the full plan, not what is left to build.
        t.blocks.blocks.put(spot.offset(1, 0, 1), FakeBlueprints.state(FakeBlueprints.PLANKS));
        chooseBuilder();
        List<?> blocks = ghost().orElseThrow().blocks();
        assertEquals(FakeBlueprints.hut(false).entries().size() - 1, blocks.size());
    }

    @Test
    void moveUsesThePlayerFacing() {
        chooseBuilder();
        t.players.setFacing(alice, 1);
        assertTrue(wand.move(alice, WandActions.Dir.FORWARD));
        assertEquals(spot.offset(1, 0, 0), ghost().orElseThrow().origin());
    }

    @Test
    void rotateReloadsThePlanRotated() {
        chooseBuilder();
        assertTrue(wand.rotate(alice, true));
        assertEquals(1, view().rotation());
        assertTrue(rotations.contains(1));
    }

    @Test
    void manipulationHiddenUntilAHutIsChosen() {
        give(BUILDER_ITEM);
        wand.open(alice, Optional.of(spot));
        wand.selectStyle(alice, FakeBlueprints.STYLE);
        assertFalse(view().manipulate());
        assertFalse(wand.move(alice, WandActions.Dir.UP));
        wand.selectBuilding(alice, BUILDER);
        assertTrue(view().manipulate());
    }

    @Test
    void cancelHidesThePreviewAndForgetsTheSession() {
        chooseBuilder();
        assertTrue(wand.cancel(alice));
        assertTrue(ghost().isEmpty());
        assertFalse(t.ui.shown.containsKey(alice));
        assertFalse(wand.open(alice, Optional.empty()));
        // The style outlives the window, like ST's selected pack.
        wand.open(alice, Optional.of(spot));
        assertEquals(FakeBlueprints.STYLE, view().style());
        assertEquals("", view().buildingTypeId());
    }

    @Test
    void disconnectForgetsEverything() {
        chooseBuilder();
        wand.disconnect(alice);
        assertTrue(ghost().isEmpty());
        assertFalse(wand.open(alice, Optional.empty()));
    }

    @Test
    void refusedConfirmKeepsSessionAndPreview() {
        chooseBuilder();
        t.playerInventory.inventories.clear();
        assertFalse(wand.confirm(alice, "Alice"));
        assertEquals(List.of("hycolony.wand.missingHut"), sentKeys());
        assertTrue(ghost().isPresent());
        assertEquals(BUILDER, view().buildingTypeId());
    }

    @Test
    void successfulConfirmClosesTheWindowAndHidesThePreview() {
        chooseBuilder();
        List<ColonyEvents.BuildingPlaced> placed = t.heard(ColonyEvents.BuildingPlaced.class);
        assertTrue(wand.confirm(alice, "Alice"));
        assertEquals(Optional.of(alice), placed.getFirst().player(), "placed by alice");
        assertTrue(colony.buildings().at(spot).isPresent());
        assertTrue(ghost().isEmpty());
        assertFalse(t.ui.shown.containsKey(alice));
        assertFalse(wand.open(alice, Optional.empty()));
    }

    @Test
    void confirmingATownHallOutsideAColonyLeavesTheFoundingWindowOpen() {
        UUID bob = UUID.randomUUID();
        BlockPos far = new BlockPos(5000, 64, 0);
        t.players.creative.add(bob);
        // Like the plugin's FoundColonyPage.onDismiss: closing the founding window cancels the foundation.
        t.ui.onClose = p -> manager.foundation().cancel(p);
        wand.open(bob, Optional.of(far));
        wand.selectStyle(bob, FakeBlueprints.STYLE);
        wand.selectBuilding(bob, TOWN_HALL);
        assertTrue(wand.confirm(bob, "Bob"));
        assertEquals(Optional.of(far), manager.foundation().pendingPositionOf(bob));
        assertInstanceOf(FoundColonyView.class, t.ui.shown.get(bob));
        assertTrue(previews.of(bob).isEmpty());
    }

    @Test
    void survivalPlayerCannotPaste() {
        chooseBuilder();
        assertFalse(view().creative());
        assertFalse(wand.paste(alice, "Alice"));
        assertTrue(colony.buildings().at(spot).isEmpty());
        assertTrue(ghost().isPresent());
    }

    @Test
    void creativePasteBuildsTheHutKeepsTheWindowAndFillsTheBlocksOverTicks() {
        t.players.creative.add(alice);
        chooseBuilder();
        assertTrue(view().creative());
        assertTrue(wand.paste(alice, "Alice"));
        assertTrue(colony.buildings().at(spot).orElseThrow().isBuilt());
        assertEquals(BUILDER, view().buildingTypeId());
        assertTrue(ghost().isPresent());
        wand.tick();
        assertTrue(t.blocks.blocks.containsKey(spot.offset(1, 0, 1)));
    }
}
