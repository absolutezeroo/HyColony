package dev.hycolony.core.citizen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import dev.hycolony.core.testing.TestContexts;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CitizenAITest {
    private final TestContexts t = new TestContexts();

    /** MC EntityAICitizenWander: every 100 ticks, once the last walk is over, a spot within 10 of where it stands. */
    @Test
    void wandersAroundItsOwnPositionOnceTheLastWalkIsOver() {
        BodyId body = t.bodies.existing(1, 1, new Vec3(100, 64, 100));
        CitizenAI ai = new CitizenAI(colonyAt(new BlockPos(0, 64, 0)), new CitizenData(1), body);

        for (int i = 0; i < 100; i++) {
            ai.tick();
        }
        assertEquals(1, t.bodies.moves.size());
        Vec3 first = t.bodies.moves.getFirst();
        assertTrue(
                Math.abs(first.x() - 100.5) <= 10 && Math.abs(first.z() - 100.5) <= 10,
                "around itself, not the town hall: " + first);
        assertEquals(CitizenState.IDLE, ai.state());

        for (int i = 0; i < 300; i++) {
            ai.tick();
        }
        assertEquals(1, t.bodies.moves.size(), "not while the walk goes on");

        t.bodies.bodies.get(body).status = NavStatus.ARRIVED;
        for (int i = 0; i < 100; i++) {
            ai.tick();
        }
        assertEquals(2, t.bodies.moves.size());
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
            if (t.bodies.bodies.get(body).status == NavStatus.MOVING) {
                t.bodies.bodies.get(body).status = NavStatus.ARRIVED;
            }
        }

        assertFalse(t.bodies.moves.isEmpty(), "still wanders on the safe half");
        assertTrue(t.bodies.moves.stream().allMatch(v -> v.x() >= 100), "never into the fire: " + t.bodies.moves);
    }

    @Test
    void wanderNeverTargetsACellBesideABrazier() {
        BlockState brazier = new BlockState(new BlockKey("Furniture_Crude_Brazier"), 0); // solid, 0.3 high, burns
        t.catalog.harmful.add(brazier.key());
        for (int z = 90; z <= 110; z++) {
            t.blocks.blocks.put(new BlockPos(95, 64, z), brazier); // a row of braziers across the wander square
        }
        BodyId body = t.bodies.existing(1, 1, new Vec3(100, 64, 100));
        CitizenAI ai = new CitizenAI(colonyAt(new BlockPos(100, 64, 100)), new CitizenData(1), body);

        for (int i = 0; i < 20_000; i++) {
            ai.tick();
            if (t.bodies.bodies.get(body).status == NavStatus.MOVING) {
                t.bodies.bodies.get(body).status = NavStatus.ARRIVED;
            }
        }

        assertFalse(t.bodies.moves.isEmpty(), "still wanders");
        assertTrue(
                t.bodies.moves.stream().allMatch(v -> Math.abs(Math.floor(v.x()) - 95) > 1),
                "never beside a brazier: " + t.bodies.moves);
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
