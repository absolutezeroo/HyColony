package dev.hycolony.core.colony.action;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ColonyManager;
import dev.hycolony.core.colony.ui.BuildingView;
import dev.hycolony.core.construction.hut.ConstructionBuildingTypes;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.testing.TestContexts;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StaleHireWindowTest {
    private final TestContexts t = new TestContexts();
    private final UUID alice = UUID.randomUUID();
    private final ColonyManager manager = new ColonyManager(t.context());

    @Test
    void hireClickedOnAWindowMadeStaleByAutoHiringReShowsTheHutWithItsWorker() {
        manager.foundation().begin(alice, "Alice", new BlockPos(0, 64, 0), 0);
        Colony colony = manager.foundation().confirm(alice, "A").orElseThrow();
        BlockPos pos = new BlockPos(10, 64, 0);
        manager.huts().place(colony, ConstructionBuildingTypes.BUILDER.id(), pos, 0);
        Building hut = colony.buildings().at(pos).orElseThrow();
        CitizenData ann = new CitizenData(1);
        ann.setName("Ann");
        colony.citizens().restore(ann);
        manager.windows().openBuilding(alice, pos);
        assertEquals(List.of(new BuildingView.WorkerRow(1, "Ann")), ((BuildingView) t.ui.shown.get(alice)).hireable());

        colony.buildings().onColonyTick(colony); // DEFAULT hiring mode auto-hires Ann behind the open window
        t.ui.shown.clear();

        assertFalse(manager.huts().hire(alice, hut.position(), ann.id()), "Ann is already employed");
        BuildingView shown = (BuildingView) t.ui.shown.get(alice);
        assertTrue(shown != null, "the stale window is re-shown");
        assertEquals(List.of(new BuildingView.WorkerRow(1, "Ann")), shown.workers());
        assertEquals(List.of(), shown.hireable());
    }

    @Test
    void fireClickedOnAWorkerWhoAlreadyLeftReShowsTheHutWithoutAMessage() {
        manager.foundation().begin(alice, "Alice", new BlockPos(0, 64, 0), 0);
        Colony colony = manager.foundation().confirm(alice, "A").orElseThrow();
        BlockPos pos = new BlockPos(10, 64, 0);
        manager.huts().place(colony, ConstructionBuildingTypes.BUILDER.id(), pos, 0);
        manager.windows().openBuilding(alice, pos);
        t.ui.shown.clear();
        int messages = t.notifier.sent.size();

        assertFalse(manager.huts().fire(alice, pos, 1), "no citizen 1 works here");

        assertTrue(t.ui.shown.get(alice) instanceof BuildingView, "MC's client redraws its hire window anyway");
        assertEquals(messages, t.notifier.sent.size(), "MC's HireFireMessage ignores it silently");
    }
}
