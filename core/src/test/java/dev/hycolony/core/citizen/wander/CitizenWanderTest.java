package dev.hycolony.core.citizen.wander;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.item.BlockKey;
import dev.hycolony.core.kernel.item.BlockKind;
import dev.hycolony.core.kernel.item.BlockState;
import dev.hycolony.core.testing.TestJobs;
import java.util.List;
import org.junit.jupiter.api.Test;

/** MC EntityAICitizenWander.decide and getRandomLeisureSite, and the territory bound asked for. */
class CitizenWanderTest extends WanderFixture {
    private static final BlockKey DIRT = new BlockKey("dirt");
    private static final BlockKey TRUNK = new BlockKey("trunk");
    private static final BlockKey LEAVES = new BlockKey("leaves");
    private static final BlockKey SOLID_LEAVES = new BlockKey("solid_leaves");
    private static final BlockKey WATER = new BlockKey("water");
    private static final BlockKey HUT = new BlockKey("hut");

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
     * wander decision comes 1200 IDLE ticks after the leisure walk ends (here the twelfth call, 1101 to 1200).
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
        assertEquals(moves + 1, t.bodies.moves.size(), "the twelfth call, within 1200 ticks");
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

    /** MC canUse: a guard does not wander. */
    @Test
    void aGuardDoesNotWander() {
        data.setJob(TestJobs.GUARD.factory().apply(data));
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

    /** A tree is no wander spot: our citizens climb 3 blocks, so its leaves are no room for a body (Hytale world). */
    @Test
    void aTreeIsNoWanderSpotItWandersOnTheGroundBeyond() {
        rolls.ints.add(5); // no leisure: a wander around where it stands
        east(63, DIRT); // east, 11 blocks: a tree
        east(64, TRUNK);
        east(65, TRUNK);
        east(66, LEAVES);
        put(new BlockPos(-1, 61, 10), DIRT); // west: ground 2 blocks down
        rolls.doubles.addAll(List.of(0.0, Math.PI));

        wander.wander();

        assertEquals(List.of(new Vec3(-0.5, 62, 10.5)), t.bodies.moves, "its ground, not the trunk's top");
    }

    /** A pack's solid leaves are no floor either: the treetop is no wander spot. */
    @Test
    void solidLeavesAreNoFloor() {
        rolls.ints.add(5); // no leisure: a wander around where it stands
        east(63, DIRT);
        east(64, TRUNK);
        east(65, TRUNK);
        east(66, SOLID_LEAVES);

        wander.wander();

        assertEquals(List.of(), t.bodies.moves, "not on top of the treetop, at 67");
    }

    /** MC PathJobRandomPos never ends a wander over water; a column with water around its height is no spot. */
    @Test
    void aPondIsNoWanderSpot() {
        rolls.ints.add(5); // no leisure: a wander around where it stands
        east(61, DIRT);
        east(62, WATER);
        east(63, WATER);

        wander.wander();

        assertEquals(List.of(), t.bodies.moves);
    }

    /** Down a slope, a treetop below the citizen's height is no spot either (it would drop onto the leaves). */
    @Test
    void aTreetopBelowIsNoWanderSpot() {
        rolls.ints.add(5); // no leisure: a wander around where it stands
        east(60, LEAVES);

        wander.wander();

        assertEquals(List.of(), t.bodies.moves);
    }

    /** Nor are leaves 4 blocks above, one higher than the highest feet height tried: it could climb into them. */
    @Test
    void leavesJustAboveTheHeightsTriedAreNoWanderSpot() {
        rolls.ints.add(5); // no leisure: a wander around where it stands
        east(68, LEAVES);

        wander.wander();

        assertEquals(List.of(), t.bodies.moves);
    }

    /** A hut block is no floor nor room for a body (MC SurfaceType: NOT_PASSABLE), as for BlockApproach. */
    @Test
    void aHutBlockIsNoWanderSpot() {
        rolls.ints.add(5); // no leisure: a wander around where it stands
        east(63, DIRT);
        east(64, HUT);
        east(65, HUT);

        wander.wander();

        assertEquals(List.of(), t.bodies.moves);
    }

    /** A cell with no room for the head is skipped: here the ceiling's top. */
    @Test
    void aCellWithoutHeadroomIsSkipped() {
        rolls.ints.add(5); // no leisure: a wander around where it stands
        east(63, DIRT);
        east(65, DIRT);

        wander.wander();

        assertEquals(List.of(new Vec3(21.5, 66, 10.5)), t.bodies.moves);
    }

    /** The block at {@code y} of the column the first wander draw (angle 0, 11 blocks east) aims at. */
    private void east(int y, BlockKey key) {
        put(new BlockPos(21, y, 10), key);
    }

    /** Puts {@code key} at {@code at}, the catalog knowing every block these tests use. */
    private void put(BlockPos at, BlockKey key) {
        t.catalog.kinds.put(DIRT, BlockKind.SOLID);
        t.catalog.kinds.put(TRUNK, BlockKind.SOLID);
        t.catalog.kinds.put(LEAVES, BlockKind.NON_SOLID); // as Hytale's: no Material, BlockType's default Empty
        t.catalog.kinds.put(WATER, BlockKind.FLUID);
        t.catalog.kinds.put(HUT, BlockKind.UNBREAKABLE);
        t.catalog.kinds.put(SOLID_LEAVES, BlockKind.SOLID); // a pack's, with "Material": "Solid"
        t.catalog.leaves.add(LEAVES);
        t.catalog.leaves.add(SOLID_LEAVES);
        t.blocks.blocks.put(at, new BlockState(key, 0));
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
