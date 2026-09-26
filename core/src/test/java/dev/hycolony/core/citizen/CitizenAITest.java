package dev.hycolony.core.citizen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.BuildingTypes;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.permission.Permissions;
import dev.hycolony.core.colony.territory.TerritoryIndex;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.item.BlockKey;
import dev.hycolony.core.kernel.item.BlockKind;
import dev.hycolony.core.kernel.item.BlockState;
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

    @Test
    void wanderNeverTargetsAColumnWithFire() {
        BlockState fire = new BlockState(new BlockKey("fire"), 0);
        t.catalog.kinds.put(fire.key(), BlockKind.NON_SOLID);
        t.catalog.harmful.add(fire.key());
        for (int x = 90; x < 100; x++) {
            for (int z = 90; z <= 110; z++) {
                t.blocks.blocks.put(new BlockPos(x, 64, z), fire); // the western half of the wander square burns
            }
        }
        BodyId body = t.bodies.existing(1, 1, new Vec3(100, 64, 100));
        CitizenAI ai = new CitizenAI(colonyAt(new BlockPos(100, 64, 100)), new CitizenData(1), body);

        for (int i = 0; i < 20_000; i++) {
            ai.tick();
            if (ai.state() == CitizenState.WANDERING) {
                t.bodies.bodies.get(body).status = NavStatus.ARRIVED;
            }
        }

        assertFalse(t.bodies.moves.isEmpty(), "still wanders on the safe half");
        assertTrue(t.bodies.moves.stream().allMatch(v -> v.x() >= 100), "never into the fire: " + t.bodies.moves);
    }

    @Test
    void staysIdleWhenEveryWanderColumnIsHarmful() {
        BlockState fire = new BlockState(new BlockKey("fire"), 0);
        t.catalog.kinds.put(fire.key(), BlockKind.NON_SOLID);
        t.catalog.harmful.add(fire.key());
        for (int x = 90; x <= 110; x++) {
            for (int z = 90; z <= 110; z++) {
                t.blocks.blocks.put(new BlockPos(x, 63, z), fire); // a campfire field under the feet level
            }
        }
        BodyId body = t.bodies.existing(1, 1, new Vec3(100, 64, 100));
        CitizenAI ai = new CitizenAI(colonyAt(new BlockPos(100, 64, 100)), new CitizenData(1), body);

        for (int i = 0; i < 2_000; i++) {
            ai.tick();
        }

        assertEquals(CitizenState.IDLE, ai.state());
        assertTrue(t.bodies.moves.isEmpty(), "no walk into the fire: " + t.bodies.moves);
    }

    private Colony colonyAt(BlockPos hall) {
        Colony c = new Colony(
                t.context(),
                new TerritoryIndex(),
                new Colony.Founding(1, "T", hall, Permissions.createDefault(UUID.randomUUID(), "A")));
        c.buildings().add(Building.create(BuildingTypes.TOWN_HALL, hall, 0));
        return c;
    }
}
