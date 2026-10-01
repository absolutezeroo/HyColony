package dev.hycolony.core.app.action;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.construction.hut.ConstructionBuildingTypes;
import dev.hycolony.core.job.HiringMode;
import dev.hycolony.core.job.WorkerModule;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.testing.TestContexts;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** MC WindowHireWorker's buttons: Hire takes a citizen from its old hut, the mode cycles without LOCKED. */
class HireWorkerActionsTest {
    private static final BlockPos HUT = new BlockPos(30, 64, 0);
    private static final BlockPos OTHER = new BlockPos(0, 64, 30);
    private final TestContexts t = new TestContexts();
    private final UUID alice = UUID.randomUUID();
    private final ColonyManager manager = t.manager();
    private final Colony colony;
    private final Building hut;
    private final Building other;

    HireWorkerActionsTest() {
        manager.foundation().begin(alice, "Alice", new BlockPos(0, 64, 0), 0);
        colony = manager.foundation().confirm(alice, "A").orElseThrow();
        manager.huts().place(colony, ConstructionBuildingTypes.BUILDER.id(), HUT, 0, alice);
        manager.huts().place(colony, ConstructionBuildingTypes.BUILDER.id(), OTHER, 0, alice);
        hut = colony.buildings().at(HUT).orElseThrow();
        other = colony.buildings().at(OTHER).orElseThrow();
    }

    private static WorkerModule workers(Building b) {
        return b.module(WorkerModule.class).orElseThrow();
    }

    @Test
    void hiringACitizenEmployedElsewhereMovesItHereAsMc() {
        CitizenData bob = new CitizenData(1);
        bob.setName("Bob");
        colony.citizens().restore(bob);
        assertTrue(workers(other).hire(colony, other, bob));

        assertTrue(manager.huts().hire(alice, HUT, bob.id()));

        assertEquals(List.of(bob.id()), workers(hut).workers());
        assertTrue(workers(other).workers().isEmpty(), "MC fires it from its old hut first");
        assertEquals(HUT, bob.workBuilding());
    }

    @Test
    void aFullHutKeepsTheCitizenWhereItWorks() {
        CitizenData ann = new CitizenData(1);
        colony.citizens().restore(ann);
        assertTrue(workers(hut).hire(colony, hut, ann));
        CitizenData bob = new CitizenData(2);
        colony.citizens().restore(bob);
        assertTrue(workers(other).hire(colony, other, bob));

        assertFalse(manager.huts().hire(alice, HUT, bob.id()));

        assertEquals(OTHER, bob.workBuilding());
    }

    @Test
    void aWorkplaceModeCyclesWithoutLockedAsMc() {
        HutWindowActions actions = manager.hutWindows();
        assertEquals(HiringMode.DEFAULT, workers(hut).hiringMode());
        assertTrue(actions.cycleHiring(alice, HUT));
        assertEquals(HiringMode.AUTO, workers(hut).hiringMode());
        assertTrue(actions.cycleHiring(alice, HUT));
        assertEquals(HiringMode.MANUAL, workers(hut).hiringMode());
        assertTrue(actions.cycleHiring(alice, HUT));
        assertEquals(HiringMode.DEFAULT, workers(hut).hiringMode(), "LOCKED is for homes only");
    }

    @Test
    void cyclingTheModeNeedsManageHuts() {
        assertFalse(manager.hutWindows().cycleHiring(UUID.randomUUID(), HUT));
        assertEquals(HiringMode.DEFAULT, workers(hut).hiringMode());
    }
}
