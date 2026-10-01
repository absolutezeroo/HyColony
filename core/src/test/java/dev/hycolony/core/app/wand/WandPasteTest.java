package dev.hycolony.core.app.wand;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.BuildingTypes;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ColonyEvents;
import dev.hycolony.core.colony.permission.Action;
import dev.hycolony.core.colony.permission.Permissions;
import dev.hycolony.core.construction.hut.ConstructionBuildingTypes;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.BlockKey;
import dev.hycolony.core.kernel.item.BlockState;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.testing.FakeBlueprints;
import dev.hycolony.core.testing.TestContexts;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WandPasteTest {
    private static final String BUILDER = ConstructionBuildingTypes.BUILDER.id();
    private static final String TOWN_HALL = BuildingTypes.TOWN_HALL.id();
    private static final int PASTE_TICKS = 100;

    private final TestContexts t = contexts();
    private final ColonyManager manager = t.manager();
    private final PasteQueue queue = new PasteQueue(manager);
    private final WandPaste paste = new WandPaste(
            manager, new WandPlacement(manager, k -> new ItemKey("item:" + k), k -> new BlockKey("block:" + k)), queue);
    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private final BlockPos spot = new BlockPos(10, 64, 10);
    private final Colony colony = found();

    private static TestContexts contexts() {
        TestContexts t = new TestContexts();
        FakeBlueprints.registerBlocks(t.catalog);
        t.blueprints = new FakeBlueprints()
                .put(BUILDER, 1, FakeBlueprints.hut(false))
                .put(BUILDER, 2, FakeBlueprints.hut(true))
                .put(TOWN_HALL, 1, FakeBlueprints.hut(false))
                .put(TOWN_HALL, 2, FakeBlueprints.hut(true));
        return t;
    }

    private Colony found() {
        manager.foundation().begin(alice, "Alice", new BlockPos(0, 64, 0), 0);
        Colony c = manager.foundation().confirm(alice, "Rivendell").orElseThrow();
        t.effects.celebrated.clear();
        return c;
    }

    private static WandSession session(BlockPos anchor, String type, int level) {
        return new WandSession(Optional.of(anchor), FakeBlueprints.STYLE, type, level, 2, "");
    }

    private void runQueue() {
        for (int i = 0; i < PASTE_TICKS; i++) {
            queue.tick();
        }
    }

    @Test
    void pastedHutIsBuiltAtTheChosenLevelWithItsStyleAndRotation() {
        List<ColonyEvents.BuildingPlaced> placed = t.heard(ColonyEvents.BuildingPlaced.class);
        List<ColonyEvents.BuildingLevelChanged> levels = t.heard(ColonyEvents.BuildingLevelChanged.class);

        WandPlacement.Result result = paste.paste(alice, "Alice", session(spot, BUILDER, 2));

        assertEquals(Optional.of(alice), placed.getFirst().player(), "pasted by alice");
        assertEquals(Optional.of(alice), levels.getFirst().player(), "its level too, not its builder's");
        Building b = assertInstanceOf(WandPlacement.Placed.class, result).building();
        assertEquals(2, b.level());
        assertTrue(b.isBuilt());
        assertEquals(FakeBlueprints.STYLE, b.style());
        assertEquals(2, b.rotation());
        assertEquals(new BlockState(new BlockKey("block:hut.builder"), 2), t.blocks.blocks.get(spot));
        assertEquals(List.of(spot), t.effects.celebrated);
    }

    @Test
    void pasteCreatesNoWorkOrderAndPlacesTheChosenLevelsBlocks() {
        paste.paste(alice, "Alice", session(spot, BUILDER, 2));
        runQueue();
        assertTrue(colony.work().ordered().isEmpty());
        assertEquals(FakeBlueprints.state(FakeBlueprints.GLASS), t.blocks.blocks.get(spot.offset(1, 1, 0)));
        assertTrue(colony.buildings()
                .at(spot)
                .orElseThrow()
                .registeredBlocks()
                .containers()
                .contains(spot.offset(0, 2, 0)));
    }

    @Test
    void pasteNeedsPlaceHutsPermission() {
        colony.permissions().setRank(bob, "Bob", Permissions.OFFICER);
        colony.permissions().ranks().get(Permissions.OFFICER).remove(Action.PLACE_HUTS);
        WandPlacement.Result result = paste.paste(bob, "Bob", session(spot, BUILDER, 1));
        assertEquals(
                "hycolony.permission.placeHuts",
                assertInstanceOf(WandPlacement.Refused.class, result).reason().key());
        assertTrue(t.blocks.placed.isEmpty());
        assertTrue(queue.isEmpty());
    }

    @Test
    void hutOutsideAnyColonyIsRefused() {
        WandPlacement.Result result = paste.paste(bob, "Bob", session(new BlockPos(5000, 64, 0), BUILDER, 1));
        assertEquals(
                "hycolony.hut.noTownHall",
                assertInstanceOf(WandPlacement.Refused.class, result).reason().key());
    }

    @Test
    void pastedTownHallOutsideAColonyFoundsItAtThePastedLevel() {
        BlockPos far = new BlockPos(5000, 64, 0);
        WandPlacement.Result result = paste.paste(bob, "Bob", session(far, TOWN_HALL, 2));
        assertInstanceOf(WandPlacement.FoundColony.class, result);
        runQueue();
        List<ColonyEvents.BuildingLevelChanged> levels = t.heard(ColonyEvents.BuildingLevelChanged.class);
        Colony founded = manager.foundation().confirm(bob, "Rivendell II").orElseThrow();
        Building townHall = founded.buildings().at(far).orElseThrow();
        assertEquals(Optional.of(bob), levels.getFirst().player(), "founded at its level by bob");
        assertEquals(2, townHall.level());
        assertTrue(townHall.isBuilt());
        assertEquals(FakeBlueprints.STYLE, townHall.style());
    }
}
