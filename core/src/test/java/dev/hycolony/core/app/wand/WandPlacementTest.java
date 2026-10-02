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
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.testing.FakeBlueprints;
import dev.hycolony.core.testing.TestContexts;
import java.util.LinkedHashMap;
import java.util.List;
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
    private final ColonyManager manager = t.manager();
    private final WandPlacement placement =
            new WandPlacement(manager, k -> new ItemKey("item:" + k), k -> new BlockKey("block:" + k));
    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private final BlockPos spot = new BlockPos(10, 64, 10);
    private final Colony colony = found();

    private static TestContexts contexts() {
        TestContexts t = new TestContexts();
        t.blueprints = new FakeBlueprints()
                .put(BUILDER, 1, FakeBlueprints.hut(false))
                .put(TOWN_HALL, 1, FakeBlueprints.hut(false));
        return t;
    }

    private Colony found() {
        manager.foundation().begin(alice, "Alice", new BlockPos(0, 64, 0), 0);
        return manager.foundation().confirm(alice, "Rivendell").orElseThrow();
    }

    private static WandSession session(BlockPos anchor, String type) {
        return new WandSession(Optional.of(anchor), FakeBlueprints.STYLE, type, 1, 2, WandNav.start());
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

    /** Stone under the hut's taped border, so that each tape finds its ground. */
    private void groundAround(BlockPos at) {
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                t.blocks.blocks.put(at.offset(x, -1, z), FakeBlueprints.state(FakeBlueprints.DIRT));
            }
        }
    }

    private long tapes() {
        return t.blocks.blocks.values().stream()
                .filter(s -> t.tape.isTape(s.key()))
                .count();
    }

    /** MC SurvivalHandler: a hut placed with the build tool in survival is taped around its level 1 plan. */
    @Test
    void survivalPlacementTapesTheSite() {
        groundAround(spot);
        give(alice, BUILDER_ITEM);

        placement.confirm(alice, "Alice", session(spot, BUILDER));

        assertEquals(16, tapes(), "the border of the 3 x 3 plan widened by one");
    }

    @Test
    void creativePlacementTapesNothing() {
        groundAround(spot);
        t.players.creative.add(alice);

        placement.confirm(alice, "Alice", session(spot, BUILDER));

        assertEquals(0, tapes());
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

    @Test
    void townHallFoundedWithTheWandKeepsItsStyle() {
        give(bob, TOWN_HALL_ITEM);
        BlockPos far = new BlockPos(5000, 64, 0);
        placement.confirm(bob, "Bob", session(far, TOWN_HALL));
        Colony founded = manager.foundation().confirm(bob, "Rivendell II").orElseThrow();
        Building townHall = founded.buildings().at(far).orElseThrow();
        assertEquals(FakeBlueprints.STYLE, townHall.style());
    }

    @Test
    void wandNeedsOnlyManageHutsNotPlaceHuts() {
        colony.permissions().setRank(bob, "Bob", Permissions.OFFICER);
        colony.permissions().ranks().get(Permissions.OFFICER).remove(Action.PLACE_HUTS);
        give(bob, BUILDER_ITEM);
        assertInstanceOf(WandPlacement.Placed.class, placement.confirm(bob, "Bob", session(spot, BUILDER)));
    }

    @Test
    void refusesWithoutAnAnchor() {
        WandSession s = WandSession.empty().withBuilding(BUILDER);
        assertEquals("hycolony.wand.missingPos", refusal(placement.confirm(alice, "Alice", s)));
    }

    @Test
    void refusesAndKeepsTheHutBlockWhenTheBlockCannotBePlaced() {
        give(alice, BUILDER_ITEM);
        t.blocks.refusePlace = true;
        assertEquals("hycolony.wand.placeFailed", refusal(placement.confirm(alice, "Alice", session(spot, BUILDER))));
        assertEquals(1, t.playerInventory.count(alice, BUILDER_ITEM));
        assertTrue(colony.buildings().at(spot).isEmpty());
    }

    @Test
    void townHallInsideItsOwnColonySkipsTheFootprintCheck() {
        colony.buildings().remove(new BlockPos(0, 64, 0));
        give(alice, TOWN_HALL_ITEM);
        BlockPos border = new BlockPos(79, 64, 0);
        assertInstanceOf(WandPlacement.Placed.class, placement.confirm(alice, "Alice", session(border, TOWN_HALL)));
    }

    @Test
    void townHallTooCloseToAnotherColonyIsRefusedBeforeTheFoundingRules() {
        give(alice, TOWN_HALL_ITEM);
        BlockPos nearby = new BlockPos(100, 64, 0);
        assertEquals(
                "hycolony.colony.tooClose", refusal(placement.confirm(alice, "Alice", session(nearby, TOWN_HALL))));
    }

    @Test
    void hutOutsideAnyColonyIsRefusedAsOutsideTheColony() {
        give(bob, BUILDER_ITEM);
        BlockPos far = new BlockPos(5000, 64, 0);
        assertEquals("hycolony.wand.outsideColony", refusal(placement.confirm(bob, "Bob", session(far, BUILDER))));
        give(alice, BUILDER_ITEM);
        assertEquals("hycolony.wand.outsideColony", refusal(placement.confirm(alice, "Alice", session(far, BUILDER))));
    }

    @Test
    void blockAlreadyAtTheAnchorIsBrokenAndItsDropsFallAtTheAnchor() {
        give(alice, BUILDER_ITEM);
        t.blocks.blocks.put(spot, FakeBlueprints.state(FakeBlueprints.DIRT));
        List<ItemAmount> drops = List.of(new ItemAmount(FakeBlueprints.DIRT_I, 1));
        t.blocks.drops.put(spot, drops);
        assertInstanceOf(WandPlacement.Placed.class, placement.confirm(alice, "Alice", session(spot, BUILDER)));
        assertEquals(drops, t.blocks.dropped.get(spot));
        assertEquals(0, t.playerInventory.count(alice, FakeBlueprints.DIRT_I));
        assertEquals(new BlockState(new BlockKey("block:hut.builder"), 2), t.blocks.blocks.get(spot));
    }

    @Test
    void hutBrokenAtTheAnchorIsUnregisteredWhenTheNewBlockCannotBePlaced() {
        manager.huts().place(colony, BUILDER, spot, 0, UUID.randomUUID());
        t.blocks.blocks.put(spot, new BlockState(new BlockKey("block:hut.builder"), 0));
        give(alice, BUILDER_ITEM);
        t.blocks.refusePlace = true;
        List<ColonyEvents.BuildingRemoved> removed = t.heard(ColonyEvents.BuildingRemoved.class);
        assertEquals("hycolony.wand.placeFailed", refusal(placement.confirm(alice, "Alice", session(spot, BUILDER))));
        assertTrue(colony.buildings().at(spot).isEmpty());
        assertEquals(Optional.of(alice), removed.getFirst().player(), "broken by alice's wand");
    }
}
