package dev.hycolony.core.citizen.wander;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.BuildingTypes;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.permission.Permissions;
import dev.hycolony.core.colony.territory.TerritoryIndex;
import dev.hycolony.core.construction.blueprint.Blueprint;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.kernel.port.NavStatus;
import dev.hycolony.core.testing.FakeBlueprints;
import dev.hycolony.core.testing.TestContexts;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.random.RandomGenerator;

/**
 * A colony around a 41 x 41 town hall at the origin, claimed 4 cells around (blocks -64..79), and one idle citizen
 * whose random draws the test scripts.
 */
abstract class WanderFixture {
    static final BlockPos CENTRE = new BlockPos(0, 64, 0);
    static final BlockPos HOME = new BlockPos(20, 64, 20);
    /** The town hall's plan: 41 x 41 blocks around the hut, so a stroll can go more than 10 blocks. */
    private static final Blueprint SQUARE = new Blueprint(
            "square",
            List.of(FakeBlueprints.entry(0, 0, 0, FakeBlueprints.PLANKS)),
            new BlockPos(-20, 0, -20),
            new BlockPos(20, 2, 20));

    final TestContexts t = new TestContexts();
    final Script rolls = new Script();
    final List<Integer> delays = new ArrayList<>();
    final CitizenData data = new CitizenData(1);
    final Colony colony;
    final Building townHall;
    BodyId body;
    CitizenWander wander;

    WanderFixture() {
        t.random = () -> rolls;
        t.blueprints = new FakeBlueprints().put(BuildingTypes.TOWN_HALL.id(), 1, SQUARE);
        colony = new Colony(
                t.context(),
                new TerritoryIndex(),
                new Colony.Founding(1, "T", CENTRE, Permissions.createDefault(UUID.randomUUID(), "A")));
        colony.claimAround(CENTRE, 4);
        townHall = Building.create(BuildingTypes.TOWN_HALL, CENTRE, 0);
        townHall.setStyle(FakeBlueprints.STYLE);
        colony.buildings().add(townHall);
        body = t.bodies.existing(1, 1, new Vec3(10.5, 64, 10.5));
        wander = new CitizenWander(colony, data, body, delays::add);
    }

    /** The ints and doubles the wander draws, in order; 99 and 0 once the script is spent. */
    static final class Script implements RandomGenerator {
        final Deque<Integer> ints = new ArrayDeque<>();
        final Deque<Double> doubles = new ArrayDeque<>();

        @Override
        public long nextLong() {
            return 0;
        }

        @Override
        public int nextInt(int bound) {
            Integer next = ints.poll();
            return Math.min(next == null ? 99 : next, bound - 1);
        }

        @Override
        public double nextDouble(double bound) {
            Double next = doubles.poll();
            return next == null ? 0 : next;
        }
    }

    /** Puts the citizen at {@code at}, with a fresh wander. */
    void standAt(Vec3 at) {
        body = t.bodies.existing(1, 1, at);
        wander = new CitizenWander(colony, data, body, delays::add);
    }

    /** The walk under way ends at its target. */
    void arrive() {
        t.bodies.bodies.get(body).position = t.bodies.bodies.get(body).target;
        t.bodies.bodies.get(body).status = NavStatus.ARRIVED;
    }

    /** Leisure drawn, the walk to the site sent, then arrived there. */
    void atTheSite() {
        rolls.ints.add(0);
        wander.wander();
        wander.leisure();
        arrive();
        wander.leisure();
    }

    static Vec3 centre(BlockPos p) {
        return Vec3.center(p);
    }
}
