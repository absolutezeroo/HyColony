package dev.hycolony.core.app.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.app.ui.BuildOptionsView;
import dev.hycolony.core.app.ui.BuildOptionsView.BuilderChoice;
import dev.hycolony.core.app.ui.BuildingView;
import dev.hycolony.core.app.ui.TownHallView;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.construction.hut.ConstructionBuildingTypes;
import dev.hycolony.core.construction.workorder.WorkOrder;
import dev.hycolony.core.construction.workorder.WorkOrderType;
import dev.hycolony.core.job.WorkerModule;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemKey;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** MC WindowBuildBuilding: its builders, the resources of the level it would build, and where it leads back. */
class BuildOptionsViewsTest {
    private final TownHallFixture f = new TownHallFixture();
    private final BlockPos residencePos = new BlockPos(20, 64, 0);

    private BuildOptionsView open(UUID player, BlockPos hut) {
        f.manager.windows().openBuildOptions(player, hut);
        return (BuildOptionsView) f.t.ui.shown.get(player);
    }

    @Test
    void buildersAreTheColonyBuilderHutsWithAWorkerSortedByDistance() {
        Building far = f.hut(ConstructionBuildingTypes.BUILDER, new BlockPos(60, 64, 0), 1);
        assertTrue(far.module(WorkerModule.class).orElseThrow().hire(f.colony, far, f.citizen(2, "Ann")));
        f.hut(ConstructionBuildingTypes.BUILDER, new BlockPos(22, 64, 0), 1); // no worker: not offered
        Building near = f.hut(ConstructionBuildingTypes.BUILDER, new BlockPos(21, 64, 4), 1); // built after Bob's
        assertTrue(near.module(WorkerModule.class).orElseThrow().hire(f.colony, near, f.citizen(3, "Cid")));
        f.hut(ConstructionBuildingTypes.RESIDENCE, residencePos, 0);

        List<BuilderChoice> builders = open(f.alice, residencePos).builders();

        assertEquals(
                List.of("Cid", "Bob", "Ann"),
                builders.stream().map(BuilderChoice::workerName).toList());
        assertEquals(near.position(), builders.getFirst().hut());
    }

    @Test
    void resourcesListTheNextLevelBlueprintItems() {
        f.t.catalog.itemForBlock.put(TownHallFixture.STONE, new ItemKey("hytale:stone"));
        f.hut(ConstructionBuildingTypes.RESIDENCE, residencePos, 0).setStyle("desert");
        f.loads.clear();

        BuildOptionsView v = open(f.alice, residencePos);

        assertEquals(List.of("desert/hycolony:residence/1"), f.loads);
        assertEquals(1, v.resources().size());
        assertEquals(1, v.resources().getFirst().count());
        assertEquals("desert", v.style());
    }

    @Test
    void resourcesListTheCurrentLevelAtMaxLevel() {
        f.townHall().setLevel(5);
        f.townHall().setBuilt(true);
        f.loads.clear();

        open(f.alice, f.hall);

        assertEquals(List.of("medieval/hycolony:townhall/5"), f.loads);
    }

    @Test
    void missingBlueprintGivesNoResources() {
        f.hut(ConstructionBuildingTypes.RESIDENCE, residencePos, 0);
        f.noBlueprints = true;
        assertTrue(open(f.alice, residencePos).resources().isEmpty());
    }

    @Test
    void theStyleIsTheHutsOwnAsMcOffersNoOther() {
        f.hut(ConstructionBuildingTypes.RESIDENCE, residencePos, 0).setStyle("desert");
        assertEquals("desert", open(f.alice, residencePos).style());
    }

    @Test
    void anUpgradeListsTheResourcesOfTheHutsOwnStyle() {
        Building res = f.hut(ConstructionBuildingTypes.RESIDENCE, residencePos, 2);
        res.setBuilt(true);
        res.setStyle("medieval");
        f.loads.clear();
        open(f.alice, residencePos);
        assertEquals(List.of("medieval/hycolony:residence/3"), f.loads);
    }

    @Test
    void aHutWithoutStyleShowsTheColonyStyle() {
        f.colony.settings().setStyle("desert");
        f.hut(ConstructionBuildingTypes.RESIDENCE, residencePos, 0).setStyle("");
        assertEquals("desert", open(f.alice, residencePos).style());
    }

    @Test
    void pickUpShowsAtLevelZeroDeconstructedOrWithoutPlanAsMc() {
        Building res = f.hut(ConstructionBuildingTypes.RESIDENCE, residencePos, 0);
        assertTrue(open(f.alice, residencePos).showPickUp(), "level 0");
        res.setLevel(2);
        res.setBuilt(true);
        assertFalse(open(f.alice, residencePos).showPickUp(), "standing with its plan");
        res.setDeconstructed(true);
        assertTrue(open(f.alice, residencePos).showPickUp(), "deconstructed");
        res.setDeconstructed(false);
        f.noBlueprints = true;
        BuildOptionsView noPlan = open(f.alice, residencePos);
        assertTrue(noPlan.showPickUp(), "no plan");
        assertFalse(noPlan.blueprintFound());
    }

    @Test
    void theTownHallNeverShowsPickUp() {
        assertFalse(open(f.alice, f.hall).showPickUp());
    }

    @Test
    void aRefusedOrderStillShowsTheBuildingWindowAsMc() {
        f.hut(ConstructionBuildingTypes.RESIDENCE, residencePos, 0); // never built: REPAIR is refused
        f.t.ui.shown.clear();
        assertTrue(f.manager
                .workOrders()
                .order(f.alice, residencePos, WorkOrderType.REPAIR, "medieval", Optional.empty())
                .isPresent());
        assertInstanceOf(BuildingView.class, f.t.ui.shown.get(f.alice));
    }

    @Test
    void openingNeedsAccessHuts() {
        f.hut(ConstructionBuildingTypes.RESIDENCE, residencePos, 0);
        assertNull(open(UUID.randomUUID(), residencePos));
    }

    @Test
    void orderWithAChosenBuilderIsClaimedByIt() {
        Building other = f.hut(ConstructionBuildingTypes.BUILDER, new BlockPos(60, 64, 0), 5);
        assertTrue(other.module(WorkerModule.class).orElseThrow().hire(f.colony, other, f.citizen(2, "Ann")));
        f.hut(ConstructionBuildingTypes.RESIDENCE, residencePos, 0);

        assertEquals(
                Optional.empty(),
                f.manager
                        .workOrders()
                        .order(f.alice, residencePos, WorkOrderType.BUILD, "medieval", Optional.of(other.position())));

        WorkOrder o = f.colony.work().byBuilding(residencePos).orElseThrow();
        assertEquals(Optional.of(other.position()), o.claimedBy());
    }

    @Test
    void orderFromTheTownHallReshowsTheTownHall() {
        f.townHall().setLevel(1);
        f.townHall().setBuilt(true);
        assertEquals(
                Optional.empty(),
                f.manager.workOrders().order(f.alice, f.hall, WorkOrderType.UPGRADE, "medieval", Optional.empty()));
        assertInstanceOf(TownHallView.class, f.t.ui.shown.get(f.alice));
    }

    @Test
    void cancellingTheTownHallsOrderReshowsTheTownHall() {
        f.townHall().setLevel(1);
        f.townHall().setBuilt(true);
        f.manager.workOrders().order(f.alice, f.hall, WorkOrderType.UPGRADE, "medieval", Optional.empty());
        f.t.ui.shown.clear();
        assertTrue(f.manager.workOrders().cancel(f.alice, f.hall));
        assertInstanceOf(TownHallView.class, f.t.ui.shown.get(f.alice));
    }

    @Test
    void closingReopensTheTownHallForTheTownHall() {
        f.manager.windows().openBuildingGui(f.alice, f.hall);
        assertInstanceOf(TownHallView.class, f.t.ui.shown.get(f.alice));
    }

    @Test
    void closingReopensTheHutWindowForAnyOtherHut() {
        f.hut(ConstructionBuildingTypes.RESIDENCE, residencePos, 0);
        f.manager.windows().openBuildingGui(f.alice, residencePos);
        assertInstanceOf(BuildingView.class, f.t.ui.shown.get(f.alice));
    }

    @Test
    void closingNeedsAccessHuts() {
        f.manager.windows().openBuildingGui(UUID.randomUUID(), f.hall);
        assertTrue(f.t.ui.shown.isEmpty());
    }

    @Test
    void aStandingHutMayBePickedUpAsMcServer() {
        f.hut(ConstructionBuildingTypes.RESIDENCE, residencePos, 2).setBuilt(true);
        assertTrue(f.manager.huts().pickUp(f.alice, residencePos, () -> true));
        assertTrue(f.colony.buildings().at(residencePos).isEmpty());
    }
}
