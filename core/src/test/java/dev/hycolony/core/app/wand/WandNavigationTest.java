package dev.hycolony.core.app.wand;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.app.ui.WandView;
import dev.hycolony.core.building.BuildingTypes;
import dev.hycolony.core.construction.hut.ConstructionBuildingTypes;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.BlockKey;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.port.Msg;
import dev.hycolony.core.logistics.warehouse.WarehouseBuilding;
import dev.hycolony.core.testing.FakeBlueprints;
import dev.hycolony.core.testing.FakePreviews;
import dev.hycolony.core.testing.TestContexts;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** ST WindowExtendedBuildTool: the pack's folders, their huts, the levels, the icons, the tree and the locks. */
class WandNavigationTest {
    private static final String BUILDER = ConstructionBuildingTypes.BUILDER.id();
    private static final String TOWN_HALL = BuildingTypes.TOWN_HALL.id();
    private static final String WAREHOUSE = WarehouseBuilding.TYPE_ID;
    private static final String STORAGE = "craftsmanship/storage";
    private static final BlockPos FAR = new BlockPos(5_000, 64, 5_000);

    private final FakeBlueprints plans = new FakeBlueprints()
            .put(BUILDER, 1, FakeBlueprints.hut(false))
            .put(BUILDER, 2, FakeBlueprints.hut(false))
            .put(TOWN_HALL, 1, FakeBlueprints.hut(false))
            .put(WAREHOUSE, 1, FakeBlueprints.hut(false));
    private final TestContexts t = contexts();
    private final ColonyManager manager = t.manager();
    private final FakePreviews previews = new FakePreviews();
    private final WandActions wand =
            new WandActions(manager, previews, k -> new ItemKey("item:" + k), k -> new BlockKey("block:" + k));
    private final UUID alice = UUID.randomUUID();
    private final BlockPos spot = new BlockPos(10, 64, 10);

    private TestContexts contexts() {
        TestContexts c = new TestContexts();
        plans.categories.put(WAREHOUSE, STORAGE);
        c.blueprints = plans;
        return c;
    }

    WandNavigationTest() {
        manager.foundation().begin(alice, "Alice", new BlockPos(0, 64, 0), 0);
        manager.foundation().confirm(alice, "Rivendell").orElseThrow();
    }

    private WandView view() {
        return assertInstanceOf(WandView.class, t.ui.shown.get(alice));
    }

    /** Puts the hut block of each of {@code typeIds} in {@code player}'s inventory. */
    private void carry(UUID player, String... typeIds) {
        Map<ItemKey, Integer> inv = new LinkedHashMap<>();
        for (String id : typeIds) {
            String key =
                    manager.context().buildingTypes().byId(id).orElseThrow().hutBlockKey();
            inv.put(new ItemKey("item:" + key), 1);
        }
        t.playerInventory.inventories.put(player, inv);
    }

    private Map<String, WandView.Hut> hutsById() {
        Map<String, WandView.Hut> huts = new LinkedHashMap<>();
        view().huts().forEach(h -> huts.put(h.buildingTypeId(), h));
        return huts;
    }

    private void openAt(BlockPos at) {
        t.players.creative.add(alice);
        wand.open(alice, Optional.of(at));
    }

    @Test
    void theWindowOpensAtTheRootWithTheTopFoldersOnly() {
        openAt(spot);
        assertEquals("", view().depth());
        assertEquals(List.of("craftsmanship", "fundamentals"), view().categories());
        assertTrue(view().folders().isEmpty());
        assertTrue(view().huts().isEmpty(), "ST shows nothing below the icons at first");
        assertFalse(view().panel().back());
        assertEquals("", view().panel().treePath());
    }

    @Test
    void aFolderShowsItsSubfoldersThenItsHutsAndBackGoesUp() {
        openAt(spot);
        assertTrue(wand.openCategory(alice, "craftsmanship"));
        assertEquals(List.of(STORAGE), view().folders());
        assertTrue(view().panel().back());
        assertTrue(wand.openCategory(alice, STORAGE));
        assertEquals(List.of(WAREHOUSE), view().hutIds());
        assertTrue(wand.back(alice));
        assertEquals("craftsmanship", view().depth());
        assertTrue(wand.back(alice));
        assertEquals("", view().depth());
        assertEquals("/", view().panel().treePath(), "ST: pack/ after going back to the root");
        assertFalse(wand.back(alice), "no back button at the root");
    }

    @Test
    void aFolderWithoutAHutOfThePackIsRefused() {
        openAt(spot);
        assertFalse(wand.openCategory(alice, "military"));
        assertFalse(wand.openCategory(alice, ""));
        assertEquals("", view().depth());
    }

    @Test
    void aClickedIconStaysDisabledUntilAHutIsChosenAndASubfolderKeepsIt() {
        openAt(spot);
        wand.openCategory(alice, "craftsmanship");
        assertEquals("craftsmanship", view().panel().disabledCategory());
        wand.openCategory(alice, STORAGE);
        assertEquals("craftsmanship", view().panel().disabledCategory());
        wand.selectBuilding(alice, WAREHOUSE);
        assertEquals("", view().panel().disabledCategory());
    }

    @Test
    void aHutWithSeveralLevelsShowsItsLevelsAndOnlyABackButtonToItsFolder() {
        openAt(spot);
        wand.openCategory(alice, "fundamentals");
        assertTrue(wand.selectBuilding(alice, BUILDER));
        assertTrue(view().panel().levels());
        assertTrue(view().panel().back());
        assertTrue(view().huts().isEmpty(), "ST updateFolders(empty, depth): the back button alone");
        assertEquals(1, view().level(), "ST setBlueprint(leveled.get(0))");
        assertEquals(2, view().maxLevel());
        assertTrue(wand.back(alice));
        assertEquals("fundamentals", view().depth(), "back shows the folder's huts again");
        assertEquals(Set.of(TOWN_HALL, BUILDER), Set.copyOf(view().hutIds()));
        assertFalse(view().panel().levels());
    }

    @Test
    void aSingleLevelHutHidesTheLevelsAndKeepsTheList() {
        openAt(spot);
        wand.openCategory(alice, "fundamentals");
        wand.selectBuilding(alice, TOWN_HALL);
        assertFalse(view().panel().levels());
        assertTrue(hutsById().get(TOWN_HALL).selected());
        assertFalse(hutsById().get(BUILDER).selected());
    }

    @Test
    void choosingAHutAgainStartsAtLevelOne() {
        openAt(spot);
        wand.openCategory(alice, "fundamentals");
        wand.selectBuilding(alice, BUILDER);
        assertTrue(wand.selectLevel(alice, 2));
        wand.back(alice);
        wand.selectBuilding(alice, BUILDER);
        assertEquals(1, view().level());
    }

    @Test
    void theTreeNamesTheFolderThenTheBlueprintFile() {
        openAt(spot);
        wand.openCategory(alice, "fundamentals");
        assertEquals("/fundamentals", view().panel().treePath());
        wand.selectBuilding(alice, BUILDER);
        assertEquals("/fundamentals/builder1", view().panel().treePath());
        wand.selectLevel(alice, 2);
        assertEquals("/fundamentals/builder2", view().panel().treePath());
        wand.back(alice);
        assertEquals("/fundamentals", view().panel().treePath());
    }

    @Test
    void aReopenedWindowShowsTheChosenHutsLevelsWithEveryIconEnabled() {
        openAt(spot);
        wand.openCategory(alice, "fundamentals");
        wand.selectBuilding(alice, BUILDER);
        wand.openCategory(alice, "fundamentals");
        t.ui.close(alice);
        wand.open(alice, Optional.empty());
        assertTrue(view().panel().levels());
        assertTrue(view().panel().back());
        assertEquals("", view().panel().disabledCategory());
        assertEquals("/fundamentals/builder1", view().panel().treePath());
    }

    @Test
    void creativeConfirmShowsThePlacementListAndHidesTheOtherLists() {
        openAt(spot);
        wand.openCategory(alice, "fundamentals");
        wand.selectBuilding(alice, BUILDER);
        assertTrue(wand.openPlacement(alice));
        assertTrue(view().panel().placing());
        assertFalse(view().panel().levels());
        assertFalse(view().panel().back());
        assertTrue(view().huts().isEmpty());
        assertTrue(wand.openCategory(alice, "fundamentals"));
        assertFalse(view().panel().placing());
    }

    @Test
    void survivalHasNoPlacementList() {
        carry(alice, BUILDER);
        wand.open(alice, Optional.of(spot));
        wand.selectBuilding(alice, BUILDER);
        assertFalse(wand.openPlacement(alice));
    }

    @Test
    void inSurvivalAHutWhoseBlockIsNotCarriedIsLockedWithItsCost() {
        carry(alice, BUILDER);
        wand.open(alice, Optional.of(spot));
        wand.openCategory(alice, "fundamentals");
        Map<String, WandView.Hut> huts = hutsById();
        assertFalse(huts.get(BUILDER).locked());
        assertEquals(
                List.of(Msg.of("hycolony.wand.requirement.cost", "%hycolony.ui.building.type.townhall")),
                huts.get(TOWN_HALL).requirements());
        assertTrue(wand.selectBuilding(alice, TOWN_HALL), "ST lets a locked blueprint be previewed");
        assertTrue(view().manipulate());
        assertFalse(view().canConfirm(), "ST hides Confirm when the predicate fails");
        wand.back(alice);
        wand.selectBuilding(alice, BUILDER);
        assertTrue(view().canConfirm());
    }

    @Test
    void confirmShowsBeforeAHutIsChosenAsInStructurize() {
        carry(alice, BUILDER);
        wand.open(alice, Optional.of(spot));
        assertTrue(view().canConfirm());
    }

    /** MC getClosestColonyView: the colony at the position, else the nearest colony the client knows. */
    @Test
    void aColonyMemberIsNotLockedOutsideItsBordersButAStrangerIs() {
        carry(alice, BUILDER, TOWN_HALL);
        wand.open(alice, Optional.of(FAR));
        wand.openCategory(alice, "fundamentals");
        assertFalse(hutsById().get(BUILDER).locked(), "alice's client knows her colony");

        UUID bob = UUID.randomUUID();
        carry(bob, BUILDER, TOWN_HALL);
        wand.open(bob, Optional.of(FAR));
        wand.openCategory(bob, "fundamentals");
        WandView bobs = assertInstanceOf(WandView.class, t.ui.shown.get(bob));
        Map<String, List<Msg>> req = new LinkedHashMap<>();
        bobs.huts().forEach(h -> req.put(h.buildingTypeId(), h.requirements()));
        assertEquals(List.of(Msg.of("hycolony.wand.requirement.inColony")), req.get(BUILDER));
        assertEquals(List.of(), req.get(TOWN_HALL), "BlockHutTownHall only asks for its block");
    }

    @Test
    void creativeLocksNothing() {
        openAt(FAR);
        wand.openCategory(alice, "fundamentals");
        assertTrue(view().huts().stream().noneMatch(WandView.Hut::locked));
    }

    /** ST: the tip shows when the window opens with no position set before (a new preview). */
    @Test
    void theTipShowsWhenAWindowOpensWithANewPositionOnly() {
        openAt(spot);
        assertTrue(view().tip());
        wand.openCategory(alice, "fundamentals");
        assertFalse(view().tip());
        wand.open(alice, Optional.of(spot));
        assertFalse(view().tip(), "the position was already set");
    }

    /** ST AbstractBlueprintManipulationWindow: the position is only set while there is none. */
    @Test
    void anotherClickedBlockKeepsThePositionUntilCancel() {
        openAt(spot);
        wand.open(alice, Optional.of(FAR));
        wand.selectBuilding(alice, TOWN_HALL);
        assertEquals(spot, ghostOrigin());
        assertTrue(wand.cancel(alice));
        wand.open(alice, Optional.of(FAR));
        assertTrue(view().tip(), "a new position after cancel");
        wand.selectBuilding(alice, TOWN_HALL);
        assertEquals(FAR, ghostOrigin());
    }

    @Test
    void aClickInTheAirWithoutAPositionPutsItTenBlocksAhead() {
        t.players.creative.add(alice);
        t.players.online.put(alice, new BlockPos(100, 70, 100));
        t.players.setFacing(alice, 1); // east
        assertTrue(wand.open(alice, Optional.empty()));
        wand.selectBuilding(alice, TOWN_HALL);
        assertEquals(new BlockPos(110, 70, 100), ghostOrigin());
    }

    private BlockPos ghostOrigin() {
        return previews.of(alice).get("wand").origin();
    }
}
