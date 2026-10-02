package dev.hycolony.core.citizen.wander;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.testing.TestJobs;
import java.util.List;
import org.junit.jupiter.api.Test;

/** MC EntityAICitizenWander.decide and getRandomLeisureSite, and the territory bound asked for. */
class CitizenWanderTest extends WanderFixture {
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
