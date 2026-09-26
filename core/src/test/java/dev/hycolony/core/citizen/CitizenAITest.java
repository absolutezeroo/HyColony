package dev.hycolony.core.citizen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.BuildingTypes;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.permission.Permissions;
import dev.hycolony.core.colony.territory.TerritoryIndex;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.kernel.port.NavStatus;
import dev.hycolony.core.testing.FakeBodies;
import dev.hycolony.core.testing.TestContexts;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CitizenAITest {
    private final TestContexts t = new TestContexts();

    @Test
    void wandersNearTownHallThenReturnsToIdleOnArrival() {
        BlockPos hall = new BlockPos(100, 64, 100);
        Colony c = new Colony(
                t.context(),
                new TerritoryIndex(),
                new Colony.Founding(1, "T", hall, Permissions.createDefault(UUID.randomUUID(), "A")));
        c.buildings().add(Building.create(BuildingTypes.TOWN_HALL, hall, 0));
        CitizenData d = new CitizenData(1);
        BodyId body = t.bodies.existing(1, 1, new Vec3(100, 64, 100));
        CitizenAI ai = new CitizenAI(c, d, body);

        for (int i = 0; i < 420 && ai.state() == CitizenState.IDLE; i++) {
            ai.tick();
        }
        assertEquals(CitizenState.WANDERING, ai.state());
        FakeBodies.Body b = t.bodies.bodies.get(body);
        assertNotNull(b.target);
        assertTrue(Math.abs(b.target.x() - 100.5) <= 10 && Math.abs(b.target.z() - 100.5) <= 10);

        b.status = NavStatus.ARRIVED;
        for (int i = 0; i < 10; i++) {
            ai.tick();
        }
        assertEquals(CitizenState.IDLE, ai.state());
    }

    @Test
    void wanderTimesOutAfter30Seconds() {
        BlockPos hall = new BlockPos(0, 64, 0);
        Colony c = new Colony(
                t.context(),
                new TerritoryIndex(),
                new Colony.Founding(1, "T", hall, Permissions.createDefault(UUID.randomUUID(), "A")));
        BodyId body = t.bodies.existing(1, 1, new Vec3(0, 64, 0));
        CitizenAI ai = new CitizenAI(c, new CitizenData(1), body);
        for (int i = 0; i < 420 && ai.state() == CitizenState.IDLE; i++) {
            ai.tick();
        }
        assertEquals(CitizenState.WANDERING, ai.state()); // no town hall: anchor = own position
        for (int i = 0; i < 610; i++) {
            ai.tick();
        }
        assertEquals(CitizenState.IDLE, ai.state());
    }
}
