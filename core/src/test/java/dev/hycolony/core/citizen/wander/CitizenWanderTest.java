package dev.hycolony.core.citizen.wander;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.BuildingTypes;
import dev.hycolony.core.citizen.CitizenData;
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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;

/** MC EntityAICitizenWander: the wander decision, the leisure walk, and the territory bound asked for. */
class CitizenWanderTest {
    private static final BlockPos CENTRE = new BlockPos(0, 64, 0);
    private static final BlockPos HOME = new BlockPos(20, 64, 20);

    private final TestContexts t = new TestContexts();
    private final Script rolls = new Script();
    private final List<Integer> delays = new ArrayList<>();
    private final CitizenData data = new CitizenData(1);
    private final Colony colony;
    private final Building townHall;
    private BodyId body;
    private CitizenWander wander;

    CitizenWanderTest() {
        t.random = () -> rolls;
        colony = new Colony(
                t.context(),
                new TerritoryIndex(),
                new Colony.Founding(1, "T", CENTRE, Permissions.createDefault(UUID.randomUUID(), "A")));
        colony.claimAround(CENTRE, 4); // cells -4..4: blocks -64..79
        townHall = Building.create(BuildingTypes.TOWN_HALL, CENTRE, 0);
        colony.buildings().add(townHall);
        standAt(new Vec3(10.5, 64, 10.5));
    }

    /** The ints and doubles the wander draws, in order; 99 and 0 once the script is spent. */
    private static final class Script implements RandomGenerator {
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

    private void standAt(Vec3 at) {
        body = t.bodies.existing(1, 1, at);
        wander = new CitizenWander(colony, data, body, delays::add);
    }

    private void arrive() {
        t.bodies.bodies.get(body).position = t.bodies.bodies.get(body).target;
        t.bodies.bodies.get(body).status = NavStatus.ARRIVED;
    }

    private static Vec3 centre(BlockPos p) {
        return new Vec3(p.x() + 0.5, p.y(), p.z() + 0.5);
    }

    @Test
    void fiveTimesInAHundredItGoesForLeisureToItsHomeAndTheAiPausesAMinute() {
        data.setHomeBuilding(HOME);
        rolls.ints.add(4); // < LEISURE_CHANCE

        wander.wander();
        wander.leisure();

        assertEquals(List.of(CitizenWander.LEISURE_DELAY_TICKS), delays);
        assertEquals(List.of(centre(HOME)), t.bodies.moves);
    }

    @Test
    void otherwiseItWandersAroundWhereItStands() {
        rolls.ints.add(5);

        wander.wander();

        assertEquals(1, t.bodies.moves.size());
        assertEquals(new Vec3(21.5, 64, 10.5), t.bodies.moves.getFirst(), "11 blocks away, angle 0");
        assertTrue(delays.isEmpty());
    }

    @Test
    void withoutAHomeTheLeisureSiteIsTheColonysCentre() {
        rolls.ints.add(0);

        wander.wander();
        wander.leisure();

        assertEquals(List.of(centre(CENTRE)), t.bodies.moves);
    }

    /** MC getRandomLeisureSite: one time in 4, a town hall of level 3 or more. */
    @Test
    void aLevelThreeTownHallIsTheLeisureSiteOneTimeInFour() {
        data.setHomeBuilding(HOME);
        townHall.setLevel(3);
        rolls.ints.addAll(List.of(0, 0)); // leisure, then the town hall's draw

        wander.wander();
        wander.leisure();

        assertEquals(List.of(centre(CENTRE)), t.bodies.moves);
    }

    @Test
    void aLowerTownHallIsNoLeisureSite() {
        data.setHomeBuilding(HOME);
        townHall.setLevel(2);
        rolls.ints.addAll(List.of(0, 0));

        wander.wander();
        wander.leisure();

        assertEquals(List.of(centre(HOME)), t.bodies.moves);
    }

    /** MC getRandomLeisureSite: in the rain, the town hall whatever its level. */
    @Test
    void inTheRainTheLeisureSiteIsTheTownHall() {
        data.setHomeBuilding(HOME);
        t.world.raining = true;
        rolls.ints.addAll(List.of(0, 3));

        wander.wander();
        wander.leisure();

        assertEquals(List.of(centre(CENTRE)), t.bodies.moves);
    }

    /** MC wanderAtLeisureSite: arrived within 3 blocks, it strolls one time in 10 in the building's box. */
    @Test
    void atTheSiteItStrollsInTheBuilding() {
        rolls.ints.add(0);
        wander.wander();
        wander.leisure();
        arrive();
        wander.leisure(); // arrived
        rolls.ints.addAll(List.of(1, 0, 0, 0)); // stays, strolls, x and z at the box's corner

        wander.leisure();

        assertEquals(centre(CENTRE), t.bodies.moves.getLast(), "the town hall's box is its block here");
        assertEquals(List.of(CitizenWander.LEISURE_DELAY_TICKS, LeisureWalk.STROLL_DELAY_TICKS), delays);
    }

    /** MC PathJobRandomPos never ends on a dangerous block: a stroll spot beside fire is not walked to. */
    @Test
    void itNeverStrollsBesideFire() {
        BlockState fire = new BlockState(new BlockKey("fire"), 0);
        t.catalog.kinds.put(fire.key(), BlockKind.NON_SOLID);
        t.catalog.harmful.add(fire.key());
        t.blocks.blocks.put(new BlockPos(1, 64, 0), fire);
        rolls.ints.add(0);
        wander.wander();
        wander.leisure();
        arrive();
        wander.leisure();
        int moves = t.bodies.moves.size();
        rolls.ints.addAll(List.of(1, 0, 0, 0));

        wander.leisure();

        assertEquals(moves, t.bodies.moves.size());
        assertEquals(List.of(CitizenWander.LEISURE_DELAY_TICKS), delays);
    }

    /** MC wanderAtLeisureSite: one time in 300 the citizen leaves; the next decision is a plain wander again. */
    @Test
    void itLeavesTheSiteOneTimeInThreeHundred() {
        rolls.ints.add(0);
        wander.wander();
        wander.leisure();
        arrive();
        wander.leisure();
        rolls.ints.add(0); // leaves
        wander.leisure();
        int moves = t.bodies.moves.size();
        rolls.ints.add(5); // no leisure

        wander.wander();

        assertEquals(moves + 1, t.bodies.moves.size(), "wanders again");
    }

    /** MC: a leisure site that is no building ends the leisure once there. */
    @Test
    void aSiteThatIsNoBuildingEndsTheLeisureOnArrival() {
        colony.buildings().remove(CENTRE);
        rolls.ints.add(0);
        wander.wander();
        wander.leisure();
        arrive();
        wander.leisure();
        rolls.ints.add(1);

        wander.leisure();
        rolls.ints.add(5);
        wander.wander();

        assertEquals(2, t.bodies.moves.size(), "back to wandering");
    }

    @Test
    void theWalkToTheSiteIsGivenUpAfterItsTimeout() {
        t.bodies.frozen = true;
        rolls.ints.add(0);
        wander.wander();
        wander.leisure();
        t.clock.tick += CitizenWander.WANDER_TIMEOUT_TICKS;
        wander.leisure();
        t.bodies.bodies.get(body).status = NavStatus.ARRIVED;
        rolls.ints.add(5);

        wander.wander();

        assertEquals(2, t.bodies.moves.size(), "back to wandering");
    }

    @Test
    void leavingIdleEndsTheLeisure() {
        rolls.ints.add(0);
        wander.wander();

        wander.leftIdle();
        rolls.ints.add(5);
        wander.wander();

        assertEquals(1, t.bodies.moves.size(), "a plain wander, no walk to the site");
    }

    /** Asked for: a wander spot outside the colony's territory is not taken. */
    @Test
    void itNeverWandersOutOfTheTerritory() {
        standAt(new Vec3(75.5, 64, 10.5)); // 4 blocks from the eastern border
        rolls.ints.add(5);
        rolls.doubles.addAll(List.of(0.0, Math.PI)); // east, out; then west, in

        wander.wander();

        assertEquals(List.of(new Vec3(64.5, 64, 10.5)), t.bodies.moves);
    }

    /** Asked for: an idle citizen outside the territory walks back to its home. */
    @Test
    void outsideTheTerritoryItWalksBackHome() {
        data.setHomeBuilding(HOME);
        standAt(new Vec3(200.5, 64, 10.5));

        wander.wander();

        assertEquals(List.of(centre(HOME)), t.bodies.moves);
        assertFalse(colony.contains(new BlockPos(200, 64, 10)));
    }
}
