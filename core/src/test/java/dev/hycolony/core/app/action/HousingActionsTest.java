package dev.hycolony.core.app.action;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.app.ui.BuildingView;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.home.LivingModule;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.construction.hut.ConstructionBuildingTypes;
import dev.hycolony.core.job.HiringMode;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.kernel.port.Msg;
import dev.hycolony.core.testing.TestContexts;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The residence's Residents tab buttons (MC AssignUnassignMessage, hiring mode, RecallCitizenHutMessage). */
class HousingActionsTest {
    private static final BlockPos HOUSE = new BlockPos(10, 64, 0);
    private final TestContexts t = new TestContexts();
    private final UUID alice = UUID.randomUUID();
    private final ColonyManager manager = t.manager();
    private final HousingActions housing = new HousingActions(manager);
    private Colony colony;

    private Building residence(int level) {
        manager.foundation().begin(alice, "Alice", new BlockPos(0, 64, 0), 0);
        colony = manager.foundation().confirm(alice, "A").orElseThrow();
        manager.huts().place(colony, ConstructionBuildingTypes.RESIDENCE.id(), HOUSE, 0, UUID.randomUUID());
        Building b = colony.buildings().at(HOUSE).orElseThrow();
        b.setLevel(level);
        b.setBuilt(level > 0);
        return b;
    }

    private CitizenData citizen(int id) {
        CitizenData d = new CitizenData(id);
        colony.citizens().restore(d);
        return d;
    }

    private static LivingModule living(Building b) {
        return b.module(LivingModule.class).orElseThrow();
    }

    @Test
    void assignWorksWhateverTheModeAsMcServer() {
        Building b = residence(1);
        citizen(1);

        assertTrue(housing.assign(alice, HOUSE, 1)); // DEFAULT with auto-housing: the window greys it, not MC's server

        assertEquals(List.of(1), living(b).residents());
        assertTrue(t.ui.shown.get(alice) instanceof BuildingView, "the window is shown again");
        assertFalse(housing.assign(alice, HOUSE, 1), "already its home");
    }

    @Test
    void assignAtLevelZeroSaysNotBuilt() {
        Building b = residence(0);
        citizen(1);

        assertFalse(housing.assign(alice, HOUSE, 1));

        assertTrue(living(b).residents().isEmpty());
        assertEquals(
                Msg.of("hycolony.hut.notBuiltYet"), t.notifier.sent.getLast().msg());
    }

    @Test
    void assignWithoutManageHutsDoesNothing() {
        Building b = residence(1);
        citizen(1);

        assertFalse(housing.assign(UUID.randomUUID(), HOUSE, 1));

        assertTrue(living(b).residents().isEmpty());
    }

    @Test
    void unassignAndCycleMode() {
        Building b = residence(1);
        living(b).assign(colony, b, citizen(1));

        assertTrue(housing.unassign(alice, HOUSE, 1));
        assertTrue(living(b).residents().isEmpty());

        for (HiringMode next : List.of(HiringMode.AUTO, HiringMode.MANUAL, HiringMode.LOCKED, HiringMode.DEFAULT)) {
            assertTrue(housing.cycleMode(alice, HOUSE));
            assertEquals(next, living(b).hiringMode());
        }
    }

    @Test
    void recallTeleportsLivingResidentsToTheHut() {
        Building b = residence(1);
        living(b).assign(colony, b, citizen(1));
        colony.citizens().respawnBody(1);

        assertTrue(housing.recall(alice, HOUSE));

        assertEquals(List.of(Vec3.center(HOUSE)), t.bodies.teleports);
    }

    @Test
    void recallRespawnsABodilessResidentAtTheHut() {
        Building b = residence(1);
        CitizenData d = citizen(1);
        d.setLastPosition(Vec3.center(new BlockPos(60, 64, 0)));
        living(b).assign(colony, b, d);

        housing.recall(alice, HOUSE);

        BodyId body = colony.citizens().bodyOf(1).orElseThrow();
        assertEquals(HOUSE, t.bodies.bodies.get(body).position.toBlockPos());
        assertNull(d.respawnPosition(), "used once, as MC's nextRespawnPos");
    }

    /** MC clears nextRespawnPos after any spawn: a failed recall does not pin every later respawn to the hut. */
    @Test
    void failedRecallDoesNotPinLaterRespawns() {
        Building b = residence(1);
        CitizenData d = citizen(1);
        living(b).assign(colony, b, d);
        t.bodies.refuseSpawn = true;
        housing.recall(alice, HOUSE);
        t.bodies.refuseSpawn = false;

        assertTrue(colony.citizens().respawnBody(1));

        assertNull(d.respawnPosition());
    }

    @Test
    void recallRespawnsABodilessResidentAndSaysWhenItFails() {
        Building b = residence(1);
        living(b).assign(colony, b, citizen(1));
        t.bodies.refuseSpawn = true;

        assertTrue(housing.recall(alice, HOUSE));

        assertEquals(
                Msg.of("hycolony.hut.recallFail"), t.notifier.sent.getLast().msg());
        t.bodies.refuseSpawn = false;
        housing.recall(alice, HOUSE);
        assertEquals(1, t.bodies.aliveCount());
    }
}
