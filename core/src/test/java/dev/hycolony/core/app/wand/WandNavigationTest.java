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
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** ST WindowExtendedBuildTool: the pack's folders, their huts, the levels and the locks. */
class WandNavigationTest {
    private static final String BUILDER = ConstructionBuildingTypes.BUILDER.id();
    private static final String TOWN_HALL = BuildingTypes.TOWN_HALL.id();
    private static final String WAREHOUSE = WarehouseBuilding.TYPE_ID;
    private static final String STORAGE = "craftsmanship/storage";

    private final FakeBlueprints plans = new FakeBlueprints()
            .put(BUILDER, 1, FakeBlueprints.hut(false))
            .put(BUILDER, 2, FakeBlueprints.hut(false))
            .put(TOWN_HALL, 1, FakeBlueprints.hut(false))
            .put(WAREHOUSE, 1, FakeBlueprints.hut(false));
    private final TestContexts t = contexts();
    private final ColonyManager manager = t.manager();
    private final WandActions wand = new WandActions(
            manager, new FakePreviews(), k -> new ItemKey("item:" + k), k -> new BlockKey("block:" + k));
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

    /** Puts the hut block of each of {@code typeIds} in alice's inventory. */
    private void carry(String... typeIds) {
        Map<ItemKey, Integer> inv = new LinkedHashMap<>();
        for (String id : typeIds) {
            String key =
                    manager.context().buildingTypes().byId(id).orElseThrow().hutBlockKey();
            inv.put(new ItemKey("item:" + key), 1);
        }
        t.playerInventory.inventories.put(alice, inv);
    }

    private Map<String, WandView.Hut> hutsById() {
        Map<String, WandView.Hut> huts = new LinkedHashMap<>();
        view().huts().forEach(h -> huts.put(h.buildingTypeId(), h));
        return huts;
    }

    private void openWithStyle(BlockPos at) {
        wand.open(alice, Optional.of(at));
        wand.selectStyle(alice, FakeBlueprints.STYLE);
    }

    @Test
    void aChosenPackOpensAtTheRootWithItsTopFolders() {
        openWithStyle(spot);
        assertEquals("", view().depth());
        assertEquals(List.of("craftsmanship", "fundamentals"), view().categories());
        assertTrue(view().folders().isEmpty());
        assertTrue(view().huts().isEmpty(), "ST shows nothing below the icons at the root");
    }

    @Test
    void aFolderShowsItsSubfoldersThenItsHutsAndBackGoesUp() {
        openWithStyle(spot);
        assertTrue(wand.openCategory(alice, "craftsmanship"));
        assertEquals(List.of(STORAGE), view().folders());
        assertTrue(view().huts().isEmpty());
        assertTrue(wand.openCategory(alice, STORAGE));
        assertEquals(List.of(WAREHOUSE), view().hutIds());
        assertTrue(wand.back(alice));
        assertEquals("craftsmanship", view().depth());
        assertTrue(wand.back(alice));
        assertEquals("", view().depth());
        assertFalse(wand.back(alice), "no folder above the root");
    }

    @Test
    void aFolderWithoutAHutOfThePackIsRefused() {
        openWithStyle(spot);
        assertFalse(wand.openCategory(alice, "military"));
        assertFalse(wand.openCategory(alice, ""));
        assertEquals("", view().depth());
    }

    @Test
    void choosingAHutStartsAtLevelOneAsStructurize() {
        t.players.creative.add(alice);
        openWithStyle(spot);
        wand.openCategory(alice, "fundamentals");
        assertTrue(wand.selectBuilding(alice, BUILDER));
        assertTrue(wand.selectLevel(alice, 2));
        assertTrue(wand.selectBuilding(alice, TOWN_HALL));
        assertEquals(1, view().level());
        assertTrue(wand.selectBuilding(alice, BUILDER));
        assertEquals(1, view().level(), "ST setBlueprint(leveled.get(0))");
        assertEquals(2, view().maxLevel());
    }

    @Test
    void inSurvivalAHutWhoseBlockIsNotCarriedIsLockedWithItsCost() {
        carry(BUILDER);
        openWithStyle(spot);
        wand.openCategory(alice, "fundamentals");
        Map<String, WandView.Hut> huts = hutsById();
        assertFalse(huts.get(BUILDER).locked());
        assertEquals(
                List.of(Msg.of("hycolony.wand.requirement.cost", "%hycolony.ui.building.type.townhall")),
                huts.get(TOWN_HALL).requirements());
        assertTrue(wand.selectBuilding(alice, TOWN_HALL), "ST lets a locked blueprint be previewed");
        assertTrue(view().manipulate());
        assertFalse(view().canConfirm(), "ST hides Confirm when the predicate fails");
        wand.selectBuilding(alice, BUILDER);
        assertTrue(view().canConfirm());
    }

    @Test
    void outsideAColonyEveryHutButTheTownHallAsksForOne() {
        carry(BUILDER, TOWN_HALL);
        openWithStyle(new BlockPos(5_000, 64, 5_000));
        wand.openCategory(alice, "fundamentals");
        Map<String, WandView.Hut> huts = hutsById();
        assertEquals(
                List.of(Msg.of("hycolony.wand.requirement.inColony")),
                huts.get(BUILDER).requirements());
        assertEquals(List.of(), huts.get(TOWN_HALL).requirements(), "BlockHutTownHall only asks for its block");
    }

    @Test
    void creativeLocksNothing() {
        t.players.creative.add(alice);
        openWithStyle(new BlockPos(5_000, 64, 5_000));
        wand.openCategory(alice, "fundamentals");
        assertTrue(view().huts().stream().noneMatch(WandView.Hut::locked));
    }

    @Test
    void theChosenHutIsMarkedSelected() {
        t.players.creative.add(alice);
        openWithStyle(spot);
        wand.openCategory(alice, "fundamentals");
        wand.selectBuilding(alice, BUILDER);
        assertTrue(hutsById().get(BUILDER).selected());
        assertFalse(hutsById().get(TOWN_HALL).selected());
    }

    /** ST: the tip shows when the window opens with no position set before (a new preview), until it goes away. */
    @Test
    void theTipShowsWhenAWindowOpensWithANewPositionOnly() {
        t.players.creative.add(alice);
        openWithStyle(spot);
        wand.cancel(alice); // forgets the position, keeps the pack
        wand.open(alice, Optional.of(spot));
        assertTrue(view().tip());
        wand.openCategory(alice, "fundamentals");
        assertFalse(view().tip());
        wand.open(alice, Optional.of(spot));
        assertFalse(view().tip(), "the position was already set");
    }
}
