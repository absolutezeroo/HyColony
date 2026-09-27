package dev.hycolony.core.construction.wand;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.BuildingTypes;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ColonyManager;
import dev.hycolony.core.construction.hut.ConstructionBuildingTypes;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.BlockKey;
import dev.hycolony.core.kernel.item.BlockState;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.testing.FakeBlueprints;
import dev.hycolony.core.testing.TestContexts;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WandPlacementTest {
    private static final String BUILDER = ConstructionBuildingTypes.BUILDER.id();
    private static final String TOWN_HALL = BuildingTypes.TOWN_HALL.id();
    private static final ItemKey BUILDER_ITEM = new ItemKey("item:hut.builder");
    private static final ItemKey TOWN_HALL_ITEM = new ItemKey("item:hut.townhall");

    private final TestContexts t = contexts();
    private final ColonyManager manager = new ColonyManager(t.context());
    private final WandPlacement placement =
            new WandPlacement(manager, k -> new ItemKey("item:" + k), k -> new BlockKey("block:" + k));
    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private final BlockPos spot = new BlockPos(10, 64, 10);
    private final Colony colony = found();

    private static TestContexts contexts() {
        TestContexts t = new TestContexts();
        t.blueprints = new FakeBlueprints().put(BUILDER, 1, FakeBlueprints.hut(false));
        return t;
    }

    private Colony found() {
        manager.foundation().begin(alice, "Alice", new BlockPos(0, 64, 0), 0);
        return manager.foundation().confirm(alice, "Rivendell").orElseThrow();
    }

    private static WandSession session(BlockPos anchor, String type) {
        return new WandSession(Optional.of(anchor), FakeBlueprints.STYLE, type, 1, 2);
    }

    private void give(UUID player, ItemKey item) {
        t.playerInventory.inventories.put(player, new LinkedHashMap<>(Map.of(item, 1)));
    }

    private String refusal(WandPlacement.Result result) {
        return assertInstanceOf(WandPlacement.Refused.class, result).reason().key();
    }

    @Test
    void refusesWhenNoBuildingIsChosen() {
        WandSession s = WandSession.empty().withAnchor(spot);
        assertEquals("hycolony.wand.noBuilding", refusal(placement.confirm(alice, "Alice", s)));
    }

    @Test
    void refusesWithoutManageHuts() {
        give(bob, BUILDER_ITEM);
        assertEquals("hycolony.wand.noPermission", refusal(placement.confirm(bob, "Bob", session(spot, BUILDER))));
        assertTrue(t.blocks.placed.isEmpty());
    }

    @Test
    void refusesASecondTownHall() {
        give(alice, TOWN_HALL_ITEM);
        assertEquals(
                "hycolony.hut.townHallExists", refusal(placement.confirm(alice, "Alice", session(spot, TOWN_HALL))));
    }

    @Test
    void refusesWhenTheFootprintLeavesTheColony() {
        give(alice, BUILDER_ITEM);
        // The colony claims cells -4..4 (x -64..79); the plan reaches one block past the anchor.
        BlockPos border = new BlockPos(79, 64, 0);
        assertEquals(
                "hycolony.wand.outsideColony", refusal(placement.confirm(alice, "Alice", session(border, BUILDER))));
        assertEquals(1, t.playerInventory.count(alice, BUILDER_ITEM));
    }

    @Test
    void refusesWhenTheHutBlockIsNoLongerInTheInventory() {
        assertEquals("hycolony.wand.missingHut", refusal(placement.confirm(alice, "Alice", session(spot, BUILDER))));
        assertTrue(t.blocks.placed.isEmpty());
    }

    @Test
    void survivalPlacementTakesOneHutBlock() {
        give(alice, BUILDER_ITEM);
        assertInstanceOf(WandPlacement.Placed.class, placement.confirm(alice, "Alice", session(spot, BUILDER)));
        assertEquals(0, t.playerInventory.count(alice, BUILDER_ITEM));
        assertEquals(new BlockState(new BlockKey("block:hut.builder"), 2), t.blocks.blocks.get(spot));
    }

    @Test
    void creativePlacementTakesNothing() {
        t.players.creative.add(alice);
        give(alice, BUILDER_ITEM);
        assertInstanceOf(WandPlacement.Placed.class, placement.confirm(alice, "Alice", session(spot, BUILDER)));
        assertEquals(1, t.playerInventory.count(alice, BUILDER_ITEM));
        assertTrue(t.blocks.blocks.containsKey(spot));
    }

    @Test
    void placedHutIsLevelZeroWithTheChosenStyleAndRotation() {
        give(alice, BUILDER_ITEM);
        WandPlacement.Result result = placement.confirm(alice, "Alice", session(spot, BUILDER));
        Building b = assertInstanceOf(WandPlacement.Placed.class, result).building();
        assertEquals(0, b.level());
        assertEquals(FakeBlueprints.STYLE, b.style());
        assertEquals(2, b.rotation());
        assertEquals(Optional.of(b), colony.buildings().at(spot));
    }

    @Test
    void placementCreatesNoWorkOrder() {
        give(alice, BUILDER_ITEM);
        placement.confirm(alice, "Alice", session(spot, BUILDER));
        assertTrue(colony.work().ordered().isEmpty());
    }

    @Test
    void townHallOutsideAnyColonyStartsTheFoundation() {
        give(bob, TOWN_HALL_ITEM);
        BlockPos far = new BlockPos(5000, 64, 0);
        WandPlacement.Result result = placement.confirm(bob, "Bob", session(far, TOWN_HALL));
        assertInstanceOf(WandPlacement.FoundColony.class, result);
        assertTrue(t.blocks.blocks.containsKey(far));
        assertEquals(Optional.of(far), manager.foundation().pendingPositionOf(bob));
        assertEquals(0, t.playerInventory.count(bob, TOWN_HALL_ITEM));
    }
}
