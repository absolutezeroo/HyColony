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
import dev.hycolony.core.construction.blueprint.Blueprint;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.item.BlockKey;
import dev.hycolony.core.kernel.item.BlockKind;
import dev.hycolony.core.kernel.item.BlockState;
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
import org.junit.jupiter.api.Test;

/** MC EntityAICitizenWander: the wander decision, the leisure walk, and the territory bound asked for. */
class CitizenWanderTest {
    private static final BlockPos CENTRE = new BlockPos(0, 64, 0);
    private static final BlockPos HOME = new BlockPos(20, 64, 20);
    /** The town hall's plan: 41 x 41 blocks around the hut, so a stroll can go more than 10 blocks. */
    private static final Blueprint SQUARE = new Blueprint(
            "square",
            List.of(FakeBlueprints.entry(0, 0, 0, FakeBlueprints.PLANKS)),
            new BlockPos(-20, 0, -20),
            new BlockPos(20, 2, 20));

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
        t.blueprints = new FakeBlueprints().put(BuildingTypes.TOWN_HALL.id(), 1, SQUARE);
        colony = new Colony(
                t.context(),
                new TerritoryIndex(),
                new Colony.Founding(1, "T", CENTRE, Permissions.createDefault(UUID.randomUUID(), "A")));
        colony.claimAround(CENTRE, 4); // cells -4..4: blocks -64..79
        townHall = Building.create(BuildingTypes.TOWN_HALL, CENTRE, 0);
        townHall.setStyle(FakeBlueprints.STYLE);
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

    /** Leisure drawn, the walk to the site sent, then arrived there. */
    private void atTheSite() {
        rolls.ints.add(0);
        wander.wander();
        wander.leisure();
        arrive();
        wander.leisure();
    }

    private static Vec3 centre(BlockPos p) {
        return new Vec3(p.x() + 0.5, p.y(), p.z() + 0.5);
    }

    @Test
    void fiveTimesInAHundredItGoesForLeisureToItsHome() {
        data.setHomeBuilding(HOME);
        rolls.ints.add(4); // < LEISURE_CHANCE

        wander.wander();
        wander.leisure();

        assertEquals(List.of(centre(HOME)), t.bodies.moves);
    }

    @Test
    void otherwiseItWandersAroundWhereItStands() {
        rolls.ints.add(5);

        wander.wander();

        assertEquals(List.of(new Vec3(21.5, 64, 10.5)), t.bodies.moves, "11 blocks away, angle 0");
    }

    /**
     * MC setCurrentDelay(60 * 20) on the decision: its countdown is frozen during the leisure states, so the next
     * wander decision comes 1200 IDLE ticks after the leisure walk ends.
     */
    @Test
    void theNextWanderDecisionWaitsAMinuteAfterTheLeisure() {
        atTheSite();
        for (int i = 0; i < 30; i++) {
            wander.wander(); // during the leisure: no decision, no countdown
        }
        rolls.ints.add(0); // leaves
        wander.leisure();
        int moves = t.bodies.moves.size();

        for (int i = 0; i < 11; i++) {
            rolls.ints.add(5);
            wander.wander();
        }
        assertEquals(moves, t.bodies.moves.size(), "11 decisions of 100 ticks skipped");
        rolls.ints.clear();
        rolls.ints.add(5);
        wander.wander();
        assertEquals(moves + 1, t.bodies.moves.size(), "the twelfth, 1200 ticks on");
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
    void aLevelThreeTownHallIsNoLeisureSiteOnTheOtherDraws() {
        data.setHomeBuilding(HOME);
        townHall.setLevel(3);
        rolls.ints.addAll(List.of(0, 1));

        wander.wander();
        wander.leisure();

        assertEquals(List.of(centre(HOME)), t.bodies.moves);
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

    /** MC walkToPos: before any walk, a citizen within 1.5 blocks of the site is already there. */
    @Test
    void aCitizenAlreadyAtTheSiteDoesNotWalk() {
        standAt(new Vec3(1, 64, 0.5));
        rolls.ints.add(0);
        wander.wander();

        wander.leisure();
        rolls.ints.addAll(List.of(1, 0, 0, 0)); // stays, strolls to the box's corner
        wander.leisure();

        assertEquals(List.of(new Vec3(-19.5, 64, -19.5)), t.bodies.moves, "only the stroll");
    }

    /** MC walkToPos: arrived only once the walk is over, within 3 blocks; else sent again. */
    @Test
    void theWalkToTheSiteEndsWithin3BlocksOnceStopped() {
        rolls.ints.add(0);
        wander.wander();
        wander.leisure();
        t.bodies.bodies.get(body).position = new Vec3(4.5, 64, 0.5);
        t.bodies.bodies.get(body).status = NavStatus.ARRIVED; // stopped 4 blocks short

        wander.leisure();

        assertEquals(List.of(centre(CENTRE), centre(CENTRE)), t.bodies.moves, "sent again");
    }

    @Test
    void stoppedThreeBlocksFromTheSiteItIsThere() {
        rolls.ints.add(0);
        wander.wander();
        wander.leisure();
        t.bodies.bodies.get(body).position = new Vec3(3.5, 64, 0.5);
        t.bodies.bodies.get(body).status = NavStatus.ARRIVED;

        wander.leisure();
        rolls.ints.addAll(List.of(1, 0, 0, 0));
        wander.leisure();

        assertEquals(List.of(centre(CENTRE), new Vec3(-19.5, 64, -19.5)), t.bodies.moves, "there: it strolls");
    }

    /** MC walkToPos: a walk under way to the site is left to go on. */
    @Test
    void theWalkToTheSiteIsNotSentAgainWhileUnderWay() {
        t.bodies.frozen = true;
        rolls.ints.add(0);
        wander.wander();

        for (int i = 0; i < 10; i++) {
            wander.leisure();
        }

        assertEquals(List.of(centre(CENTRE)), t.bodies.moves);
    }

    /** MC wanderAtLeisureSite: one time in 10, a stroll more than 10 blocks off in the building; then 30 ticks. */
    @Test
    void atTheSiteItStrollsInTheBuilding() {
        atTheSite();
        rolls.ints.addAll(List.of(1, 0, 0, 0)); // stays, strolls, x and z at the box's corner

        wander.leisure();

        assertEquals(new Vec3(-19.5, 64, -19.5), t.bodies.moves.getLast());
        assertEquals(List.of(LeisureWalk.STROLL_DELAY_TICKS), delays);
    }

    /** MC PathJobRandomPos ends more than 10 blocks away: a building too small gives no stroll, the 30 ticks hold. */
    @Test
    void aStrollDrawnInASmallBuildingDoesNotWalkButStillWaits() {
        townHall.setStyle(""); // no plan: the box is the hut block
        atTheSite();
        int moves = t.bodies.moves.size();
        rolls.ints.addAll(List.of(1, 0));

        wander.leisure();

        assertEquals(moves, t.bodies.moves.size());
        assertEquals(List.of(LeisureWalk.STROLL_DELAY_TICKS), delays);
    }

    /** MC walkToRandomPosWithin: a stroll drawn while the citizen still walks starts nothing; the 30 ticks hold. */
    @Test
    void aStrollDrawnWhileWalkingStartsNothing() {
        atTheSite();
        t.bodies.bodies.get(body).status = NavStatus.MOVING;
        int moves = t.bodies.moves.size();
        rolls.ints.addAll(List.of(1, 0, 0, 0));

        wander.leisure();

        assertEquals(moves, t.bodies.moves.size());
        assertEquals(List.of(LeisureWalk.STROLL_DELAY_TICKS), delays);
    }

    /** MC PathJobRandomPos never ends on a dangerous block: a stroll spot beside fire is passed for another. */
    @Test
    void itNeverStrollsBesideFire() {
        BlockState fire = new BlockState(new BlockKey("fire"), 0);
        t.catalog.kinds.put(fire.key(), BlockKind.NON_SOLID);
        t.catalog.harmful.add(fire.key());
        t.blocks.blocks.put(new BlockPos(-19, 64, -20), fire);
        atTheSite();
        rolls.ints.addAll(List.of(1, 0, 0, 0, 40, 40)); // the corner beside the fire, then the opposite one

        wander.leisure();

        assertEquals(new Vec3(20.5, 64, 20.5), t.bodies.moves.getLast());
    }

    /** MC wanderAtLeisureSite: one time in 300 the citizen leaves. */
    @Test
    void itLeavesTheSiteOneTimeInThreeHundred() {
        atTheSite();
        int moves = t.bodies.moves.size();

        rolls.ints.add(0); // leaves
        wander.leisure();
        rolls.ints.addAll(List.of(1, 0, 0, 0));
        wander.leisure();

        assertEquals(moves, t.bodies.moves.size(), "no more strolls");
    }

    /** MC: a leisure site that is no building ends the leisure once there. */
    @Test
    void aSiteThatIsNoBuildingEndsTheLeisureOnArrival() {
        colony.buildings().remove(CENTRE);
        atTheSite();
        rolls.ints.add(1);
        wander.leisure();
        int moves = t.bodies.moves.size();

        rolls.ints.addAll(List.of(1, 0, 0, 0));
        wander.leisure();

        assertEquals(moves, t.bodies.moves.size(), "no stroll: the leisure is over");
    }

    @Test
    void theWalkToTheSiteIsGivenUpAfterItsTimeout() {
        t.bodies.frozen = true;
        rolls.ints.add(0);
        wander.wander();
        wander.leisure();
        t.clock.tick += CitizenWander.WANDER_TIMEOUT_TICKS;
        wander.leisure();
        int moves = t.bodies.moves.size();

        t.bodies.bodies.get(body).status = NavStatus.ARRIVED;
        wander.leisure();

        assertEquals(moves, t.bodies.moves.size(), "not sent again: given up");
    }

    @Test
    void leavingIdleEndsTheLeisure() {
        rolls.ints.add(0);
        wander.wander();

        wander.leftIdle();
        wander.leisure();

        assertTrue(t.bodies.moves.isEmpty(), "no walk to the site");
    }

    /** MC canUse: a child does not wander. */
    @Test
    void aChildDoesNotWander() {
        data.setChild(true);
        rolls.ints.add(5);

        wander.wander();

        assertTrue(t.bodies.moves.isEmpty());
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

    @Test
    void outsideTheTerritoryWithoutAHomeItWalksBackToTheCentre() {
        standAt(new Vec3(200.5, 64, 10.5));

        wander.wander();

        assertEquals(List.of(centre(CENTRE)), t.bodies.moves);
    }
}
