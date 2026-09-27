package dev.hycolony.core.colony.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ColonyManager;
import dev.hycolony.core.colony.ui.BuildingView;
import dev.hycolony.core.colony.ui.TownHallView;
import dev.hycolony.core.construction.hut.ConstructionBuildingTypes;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.testing.TestContexts;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LiveWindowRefreshTest {
    private final TestContexts t = new TestContexts();
    private final UUID alice = UUID.randomUUID();
    private final ColonyManager manager = new ColonyManager(t.context());
    private final BlockPos pos = new BlockPos(10, 64, 0);
    private Colony colony;

    @BeforeEach
    void colonyWithAnIdleCitizen() {
        manager.foundation().begin(alice, "Alice", new BlockPos(0, 64, 0), 0);
        colony = manager.foundation().confirm(alice, "A").orElseThrow();
        manager.huts().place(colony, ConstructionBuildingTypes.BUILDER.id(), pos, 0);
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
        assertEquals(List.of(new BuildingView.WorkerRow(1, "Ann")), redrawn.workers());
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
        manager.huts().onRemoved(pos);

        tickWindows(OpenWindows.UPDATE_SUBSCRIBERS_INTERVAL_TICKS);

        assertTrue(t.ui.redrawn.isEmpty());
    }
}
