package dev.hycolony.core.citizen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.BuildingTypes;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.permission.Permissions;
import dev.hycolony.core.colony.territory.ClaimCell;
import dev.hycolony.core.colony.territory.TerritoryIndex;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.testing.TestContexts;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** MC spawnOrCreateCivilian(force = true), as /mc citizens spawnNew asks it (spec 2026-10-02 lot 2, § 4). */
class CitizenForcedArrivalTest {
    private final TestContexts t = new TestContexts();
    private final BlockPos hall = new BlockPos(0, 64, 0);

    private Colony colony(boolean townHall) {
        TerritoryIndex territory = new TerritoryIndex();
        territory.claimSquare(1, ClaimCell.of(hall), 4);
        Colony c = new Colony(
                t.context(),
                territory,
                new Colony.Founding(1, "Test", hall, Permissions.createDefault(UUID.randomUUID(), "A")));
        if (townHall) {
            c.buildings().add(Building.create(BuildingTypes.TOWN_HALL, hall, 0));
        }
        return c;
    }

    private long warnings() {
        return t.notifier.sent.stream()
                .filter(s -> s.msg().key().equals("hycolony.citizen.noArrivalSpace"))
                .count();
    }

    @Test
    void aForcedArrivalIgnoresNewCitizensOffAndTheInitialAmount() {
        Colony c = colony(true);
        c.settings().setMoveIn(false);

        for (int i = 0; i < 6; i++) {
            assertTrue(c.citizens().spawnForced());
        }

        assertEquals(6, c.citizens().all().size(), "beyond initialCitizenAmount (4)");
        assertEquals(6, t.bodies.aliveCount());
    }

    @Test
    void aForcedArrivalIsSavedJournaledAndAnnounced() {
        Colony c = colony(true);
        c.clearDirty();
        List<CitizenSpawned> heard = t.heard(CitizenSpawned.class);

        c.citizens().spawnForced();

        assertTrue(c.isDirty());
        assertEquals(1, heard.size());
    }

    @Test
    void noTownHallNoCitizen() {
        Colony c = colony(false);

        assertFalse(c.citizens().spawnForced());
        assertEquals(0, c.citizens().all().size());
    }

    @Test
    void anUnloadedTownHallCreatesNoneAndWarnsNobody() {
        Colony c = colony(true);
        t.players.online.put(c.permissions().owner(), hall);
        t.world.unloaded.add(hall);

        assertFalse(c.citizens().spawnForced());
        assertEquals(0, c.citizens().all().size());
        assertEquals(0, warnings());
    }

    @Test
    void noRoomAtTheTownHallCreatesNoneAndWarnsOnce() {
        Colony c = colony(true);
        t.players.online.put(c.permissions().owner(), hall);
        t.bodies.refuseSpawnAround.add(hall);

        assertFalse(c.citizens().spawnForced());
        assertEquals(0, c.citizens().all().size(), "MC creates the citizen only once its spawn point is found");
        assertEquals(1, warnings());
    }
}
