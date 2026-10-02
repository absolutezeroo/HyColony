package dev.hycolony.core.app.wand;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.app.HutPlacement;
import dev.hycolony.core.app.ui.SuggestBuildToolView;
import dev.hycolony.core.building.BuildingTypes;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.permission.Action;
import dev.hycolony.core.colony.permission.Permissions;
import dev.hycolony.core.colony.permission.Rank;
import dev.hycolony.core.construction.hut.ConstructionBuildingTypes;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.BlockKey;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.port.Msg;
import dev.hycolony.core.testing.FakeBlueprints;
import dev.hycolony.core.testing.FakePreviews;
import dev.hycolony.core.testing.TestContexts;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** A hut block placed by hand (MC EventHandler.onPlayerInteract, WindowSuggestBuildTool). */
class HutHandPlacementTest {
    private static final String BUILDER = ConstructionBuildingTypes.BUILDER.id();
    private static final String TOWN_HALL = BuildingTypes.TOWN_HALL.id();
    private static final ItemKey BUILDER_ITEM = new ItemKey("item:hut.builder");
    private static final ItemKey TOWN_HALL_ITEM = new ItemKey("item:hut.townhall");
    private static final ItemKey TOOL = new ItemKey("item:build_tool");

    private final TestContexts t = contexts();
    private final ColonyManager manager = t.manager();
    private final WandActions wand = new WandActions(
            manager, new FakePreviews(), k -> new ItemKey("item:" + k), k -> new BlockKey("block:" + k));
    private final HutHandPlacement hand = new HutHandPlacement(manager, wand, TOOL);
    private final UUID alice = UUID.randomUUID();
    private final BlockPos spot = new BlockPos(10, 64, 10);
    private final Colony colony = found();

    private static TestContexts contexts() {
        TestContexts t = new TestContexts();
        t.blueprints = new FakeBlueprints().put(BUILDER, 1, FakeBlueprints.hut(false));
        return t;
    }

    private Colony found() {
        manager.foundation().begin(alice, "Alice", new BlockPos(0, 64, 0), 0);
        Colony c = manager.foundation().confirm(alice, "Rivendell").orElseThrow();
        t.notifier.sent.clear();
        t.ui.shown.clear();
        return c;
    }

    private void give(ItemKey... items) {
        Map<ItemKey, Integer> inv = new LinkedHashMap<>();
        for (ItemKey item : items) {
            inv.put(item, 1);
        }
        t.playerInventory.inventories.put(alice, inv);
    }

    private Optional<HutPlacement> place(UUID player, BlockPos at, String type, ItemKey item, boolean crouching) {
        return hand.handPlaced(player, at, type, item, crouching);
    }

    @Test
    void aHutPlacedByHandIsRefusedAndTheBuildToolSuggested() {
        assertTrue(place(alice, spot, BUILDER, BUILDER_ITEM, false).isEmpty());

        assertEquals(new SuggestBuildToolView(spot, BUILDER_ITEM), t.ui.shown.get(alice));
    }

    @Test
    void aCreativePlayerStandingIsSuggestedTheBuildToolToo() {
        t.players.creative.add(alice);

        assertTrue(place(alice, spot, BUILDER, BUILDER_ITEM, false).isEmpty());

        assertEquals(new SuggestBuildToolView(spot, BUILDER_ITEM), t.ui.shown.get(alice));
    }

    @Test
    void aCreativePlayerCrouchingPlacesTheHutAsIs() {
        t.players.creative.add(alice);

        assertEquals(Optional.of(new HutPlacement.Allowed(colony)), place(alice, spot, BUILDER, BUILDER_ITEM, true));

        assertFalse(t.ui.shown.containsKey(alice));
    }

    @Test
    void aSurvivalPlayerCrouchingIsStillSuggestedTheBuildTool() {
        assertTrue(place(alice, spot, BUILDER, BUILDER_ITEM, true).isEmpty());

        assertEquals(new SuggestBuildToolView(spot, BUILDER_ITEM), t.ui.shown.get(alice));
    }

    /** MC handleEventCancellation (onBlockHutPlaced) runs first: a refused placement says why, without a window. */
    @Test
    void aHutTheRulesRefuseSaysWhyWithoutTheWindow() {
        BlockPos far = new BlockPos(5000, 64, 5000);

        assertEquals(
                Optional.of(new HutPlacement.Denied(Msg.of("hycolony.hut.tooFar"))),
                place(alice, far, BUILDER, BUILDER_ITEM, false));
        assertEquals(
                Optional.of(new HutPlacement.Denied(Msg.of("hycolony.hut.townHallExists", "0 64 0"))),
                place(alice, spot, TOWN_HALL, TOWN_HALL_ITEM, false));
        assertFalse(t.ui.shown.containsKey(alice));
    }

    /** A first town hall passes the rules outside any colony, then is suggested the build tool like any hut. */
    @Test
    void aFirstTownHallOutsideAnyColonyIsSuggestedTheBuildTool() {
        UUID carol = UUID.randomUUID();
        BlockPos far = new BlockPos(5000, 64, 5000);

        assertTrue(place(carol, far, TOWN_HALL, TOWN_HALL_ITEM, false).isEmpty());

        assertEquals(new SuggestBuildToolView(far, TOWN_HALL_ITEM), t.ui.shown.get(carol));
    }

    /** MC onBlockHutPlaced: a town hall outside colonies passes; owning a colony is refused only when founding. */
    @Test
    void aColonyOwnersTownHallOutsideColoniesIsSuggestedTheBuildToolToo() {
        BlockPos far = new BlockPos(5000, 64, 5000);

        assertTrue(place(alice, far, TOWN_HALL, TOWN_HALL_ITEM, false).isEmpty());

        assertEquals(new SuggestBuildToolView(far, TOWN_HALL_ITEM), t.ui.shown.get(alice));
    }

    /** Placed as is, the town hall meets the founding rules there, as MC's colony creation does. */
    @Test
    void aCreativeOwnerCrouchingWithATownHallOutsideColoniesIsToldTheFoundingRefusal() {
        t.players.creative.add(alice);
        BlockPos far = new BlockPos(5000, 64, 5000);

        assertEquals(
                Optional.of(new HutPlacement.Denied(Msg.of("hycolony.colony.alreadyOwner"))),
                place(alice, far, TOWN_HALL, TOWN_HALL_ITEM, true));
        assertFalse(t.ui.shown.containsKey(alice));
    }

    @Test
    void aStrangerWithoutPlaceHutsIsToldSoWithoutTheWindow() {
        UUID bob = UUID.randomUUID(); // not a member: neutral, without PLACE_HUTS

        assertEquals(
                Optional.of(new HutPlacement.Denied(Msg.of("hycolony.permission.placeHuts", "Rivendell"))),
                place(bob, spot, BUILDER, BUILDER_ITEM, false));

        assertFalse(t.ui.shown.containsKey(bob));
    }

    /** MC: with PLACE_HUTS but without ACCESS_HUTS, the placement is cancelled without a word nor a window. */
    @Test
    void aMemberAllowedToPlaceHutsButNotToAccessThemIsRefusedSilently() {
        UUID officer = UUID.randomUUID();
        colony.permissions().addPlayer(officer, "O", Permissions.OFFICER);
        Rank owner = colony.permissions().rankOf(alice);
        Rank officers = colony.permissions().rankOf(officer);
        colony.permissions().alterPermission(owner, officers, Action.PLACE_HUTS, true);
        colony.permissions().alterPermission(owner, officers, Action.ACCESS_HUTS, false);

        assertTrue(place(officer, spot, BUILDER, BUILDER_ITEM, false).isEmpty());

        assertFalse(t.ui.shown.containsKey(officer));
        assertTrue(t.notifier.sent.isEmpty());
    }

    @Test
    void useBuildToolWithoutOneSaysItIsMissing() {
        give(BUILDER_ITEM);

        assertFalse(hand.useBuildTool(alice, spot, BUILDER_ITEM));

        assertEquals(
                List.of(Msg.of("hycolony.wand.missingTool")),
                t.notifier.sent.stream().map(s -> s.msg()).toList());
        assertTrue(t.playerInventory.swaps.isEmpty());
    }

    @Test
    void useBuildToolSwapsTheHutWithTheToolAndOpensTheToolAtThePlacedSpot() {
        give(BUILDER_ITEM, TOOL);

        assertTrue(hand.useBuildTool(alice, spot, BUILDER_ITEM));

        assertEquals(List.of(BUILDER_ITEM + "<->" + TOOL), t.playerInventory.swaps);
        wand.selectBuilding(alice, BUILDER);
        wand.confirm(alice, "Alice");
        assertTrue(colony.buildings().at(spot).isPresent(), "the tool's ghost stood on the spot the hut was aimed at");
    }

    /** ST AbstractBlueprintManipulationWindow: the ghost moves to the given spot only when it has none yet. */
    @Test
    void useBuildToolKeepsAGhostThatAlreadyStandsElsewhere() {
        give(BUILDER_ITEM, TOOL);
        BlockPos elsewhere = new BlockPos(14, 64, 14);
        wand.open(alice, Optional.of(elsewhere));

        hand.useBuildTool(alice, spot, BUILDER_ITEM);

        wand.selectBuilding(alice, BUILDER);
        wand.confirm(alice, "Alice");
        assertTrue(colony.buildings().at(elsewhere).isPresent());
        assertTrue(colony.buildings().at(spot).isEmpty());
    }
}
