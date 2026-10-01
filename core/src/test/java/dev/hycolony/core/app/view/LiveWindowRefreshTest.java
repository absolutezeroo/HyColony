package dev.hycolony.core.app.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.app.ui.BuildingView;
import dev.hycolony.core.app.ui.CitizenView;
import dev.hycolony.core.app.ui.RequestsView;
import dev.hycolony.core.app.ui.TownHallView;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.permission.Permissions;
import dev.hycolony.core.construction.hut.ConstructionBuildingTypes;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.request.model.StackRequest;
import dev.hycolony.core.testing.TestContexts;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LiveWindowRefreshTest {
    private final TestContexts t = new TestContexts();
    private final UUID alice = UUID.randomUUID();
    private final ColonyManager manager = t.manager();
    private final BlockPos pos = new BlockPos(10, 64, 0);
    private Colony colony;

    @BeforeEach
    void colonyWithAnIdleCitizen() {
        manager.foundation().begin(alice, "Alice", new BlockPos(0, 64, 0), 0);
        colony = manager.foundation().confirm(alice, "A").orElseThrow();
        manager.huts().place(colony, ConstructionBuildingTypes.BUILDER.id(), pos, 0, UUID.randomUUID());
        CitizenData ann = new CitizenData(1);
        ann.setName("Ann");
        colony.citizens().restore(ann);
    }

    private void tickWindows(int ticks) {
        for (int i = 0; i < ticks; i++) {
            manager.windows().tick();
        }
    }

    @Test
    void openHutWindowIsRedrawnOnTheNextSubscriberUpdateAfterAutoHiringFillsIt() {
        manager.windows().openBuilding(alice, pos);
        colony.buildings().onColonyTick(colony);

        tickWindows(OpenWindows.UPDATE_SUBSCRIBERS_INTERVAL_TICKS - 1);
        assertTrue(t.ui.redrawn.isEmpty(), "MC sends views every UPDATE_SUBSCRIBERS_INTERVAL ticks only");
        tickWindows(1);

        BuildingView redrawn = (BuildingView) t.ui.redrawn.get(0);
        assertEquals(List.of(new BuildingView.WorkerLine(1, "Ann", "hycolony:builder")), redrawn.workers());
    }

    @Test
    void unchangedWindowIsNotRedrawn() {
        manager.windows().openBuilding(alice, pos);

        tickWindows(OpenWindows.UPDATE_SUBSCRIBERS_INTERVAL_TICKS * 3);

        assertTrue(t.ui.redrawn.isEmpty());
    }

    @Test
    void windowThePlayerClosedIsNeverRedrawnNorReopened() {
        manager.windows().openBuilding(alice, pos);
        t.ui.close(alice);
        colony.buildings().onColonyTick(colony);

        tickWindows(OpenWindows.UPDATE_SUBSCRIBERS_INTERVAL_TICKS * 2);

        assertTrue(t.ui.redrawn.isEmpty());
        assertFalse(t.ui.shown.containsKey(alice));
    }

    @Test
    void anotherWindowOpenedSinceIsLeftAlone() {
        manager.windows().openBuilding(alice, pos);
        manager.windows().openTownHall(alice, colony.center());
        t.ui.shown.clear();
        colony.buildings().onColonyTick(colony);

        tickWindows(OpenWindows.UPDATE_SUBSCRIBERS_INTERVAL_TICKS);

        assertTrue(t.ui.redrawn.stream().noneMatch(BuildingView.class::isInstance), "the hut window is not back");
        assertTrue(t.ui.shown.isEmpty(), "nothing is opened by a refresh");
    }

    @Test
    void openTownHallWindowIsRedrawnWhenTheColonyChanges() {
        manager.windows().openTownHall(alice, colony.center());
        colony.buildings().onColonyTick(colony);

        tickWindows(OpenWindows.UPDATE_SUBSCRIBERS_INTERVAL_TICKS);

        assertEquals(1, t.ui.redrawn.size());
        assertTrue(t.ui.redrawn.get(0) instanceof TownHallView);
    }

    @Test
    void windowOfARemovedHutIsDroppedNotRedrawn() {
        manager.windows().openBuilding(alice, pos);
        manager.huts().onRemoved(pos, UUID.randomUUID());

        tickWindows(OpenWindows.UPDATE_SUBSCRIBERS_INTERVAL_TICKS);

        assertTrue(t.ui.redrawn.isEmpty());
    }

    @Test
    void closedWindowIsForgottenEvenWhenItsViewNeverChanges() {
        manager.windows().openBuilding(alice, pos);
        t.ui.close(alice);

        tickWindows(OpenWindows.UPDATE_SUBSCRIBERS_INTERVAL_TICKS);
        int checks = t.ui.showingChecks;
        tickWindows(OpenWindows.UPDATE_SUBSCRIBERS_INTERVAL_TICKS * 2);

        assertEquals(1, checks);
        assertEquals(checks, t.ui.showingChecks, "the closed window is no longer watched");
    }

    @Test
    void openCitizenWindowIsRedrawnWhenTheCitizenChanges() {
        manager.windows().openCitizen(alice, colony.id(), 1);
        colony.citizens().get(1).orElseThrow().setName("Bea");

        tickWindows(OpenWindows.UPDATE_SUBSCRIBERS_INTERVAL_TICKS);

        assertEquals("Bea", ((CitizenView) t.ui.redrawn.get(0)).name());
    }

    @Test
    void openClipboardIsRedrawnWhenARequestComesAsMcRefreshesItsTree() {
        manager.windows().openRequests(alice, colony.id(), false);
        Building hut = colony.buildings().at(pos).orElseThrow();
        colony.requests().createAndAssign(hut, new StackRequest(new ItemKey("plank"), 2, 2, true), 1);

        tickWindows(OpenWindows.REQUEST_TREE_REFRESH_TICKS - 1);
        assertTrue(t.ui.redrawn.isEmpty(), "MC rebuilds the tree every AUTO_REFRESH_TICKS only");
        tickWindows(1);

        RequestsView redrawn = (RequestsView) t.ui.redrawn.get(0);
        assertEquals(1, redrawn.rows().size());
        assertFalse(redrawn.showImportant(), "the window keeps its \"!\" state");
    }

    @Test
    void windowOfAPlayerWhoLostHutAccessIsDroppedNotRedrawn() {
        UUID bob = UUID.randomUUID();
        colony.permissions().setRank(bob, "Bob", Permissions.OFFICER);
        manager.windows().openBuilding(bob, pos);
        colony.permissions().setRank(bob, "Bob", Permissions.NEUTRAL);
        colony.buildings().onColonyTick(colony);

        tickWindows(OpenWindows.UPDATE_SUBSCRIBERS_INTERVAL_TICKS);
        int checks = t.ui.showingChecks;
        tickWindows(OpenWindows.UPDATE_SUBSCRIBERS_INTERVAL_TICKS);

        assertTrue(t.ui.redrawn.isEmpty());
        assertEquals(checks, t.ui.showingChecks, "dropped");
    }

    @Test
    void windowOfADeletedColonyIsDroppedNotRedrawn() {
        manager.windows().openTownHall(alice, colony.center());
        assertTrue(manager.deleteColony(colony.id(), UUID.randomUUID()));

        tickWindows(OpenWindows.UPDATE_SUBSCRIBERS_INTERVAL_TICKS);
        int checks = t.ui.showingChecks;
        tickWindows(OpenWindows.UPDATE_SUBSCRIBERS_INTERVAL_TICKS);

        assertTrue(t.ui.redrawn.isEmpty());
        assertEquals(checks, t.ui.showingChecks, "dropped");
    }

    @Test
    void aWindowThatFailsDoesNotStopTheOthers() {
        UUID bob = UUID.randomUUID();
        colony.permissions().setRank(bob, "Bob", Permissions.OFFICER);
        manager.windows().openBuilding(bob, pos);
        manager.windows().openBuilding(alice, pos);
        t.ui.failing.add(bob);
        colony.buildings().onColonyTick(colony);

        tickWindows(OpenWindows.UPDATE_SUBSCRIBERS_INTERVAL_TICKS);

        assertEquals(1, t.ui.redrawn.size(), "alice's window is redrawn despite bob's failure");
    }
}
