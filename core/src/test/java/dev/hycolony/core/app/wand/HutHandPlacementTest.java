package dev.hycolony.core.app.wand;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.app.ui.SuggestBuildToolView;
import dev.hycolony.core.colony.Colony;
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
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** A hut block placed by hand (MC EventHandler.onPlayerInteract, WindowSuggestBuildTool). */
class HutHandPlacementTest {
    private static final String BUILDER = ConstructionBuildingTypes.BUILDER.id();
    private static final ItemKey BUILDER_ITEM = new ItemKey("item:hut.builder");
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

    @Test
    void aHutPlacedByHandIsRefusedAndTheBuildToolSuggested() {
        assertFalse(hand.handPlaced(alice, spot, BUILDER_ITEM, false));

        assertEquals(new SuggestBuildToolView(spot, BUILDER_ITEM), t.ui.shown.get(alice));
    }

    @Test
    void aCreativePlayerStandingIsSuggestedTheBuildToolToo() {
        t.players.creative.add(alice);

        assertFalse(hand.handPlaced(alice, spot, BUILDER_ITEM, false));

        assertEquals(new SuggestBuildToolView(spot, BUILDER_ITEM), t.ui.shown.get(alice));
    }

    @Test
    void aCreativePlayerCrouchingPlacesTheHutAsIs() {
        t.players.creative.add(alice);

        assertTrue(hand.handPlaced(alice, spot, BUILDER_ITEM, true));

        assertFalse(t.ui.shown.containsKey(alice));
    }

    @Test
    void aSurvivalPlayerCrouchingIsStillSuggestedTheBuildTool() {
        assertFalse(hand.handPlaced(alice, spot, BUILDER_ITEM, true));

        assertEquals(new SuggestBuildToolView(spot, BUILDER_ITEM), t.ui.shown.get(alice));
    }

    @Test
    void aPlayerWithoutAccessToTheColonysHutsIsRefusedWithoutAWindow() {
        UUID bob = UUID.randomUUID(); // not a member: neutral, without ACCESS_HUTS

        assertFalse(hand.handPlaced(bob, spot, BUILDER_ITEM, false));

        assertFalse(t.ui.shown.containsKey(bob));
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
}
