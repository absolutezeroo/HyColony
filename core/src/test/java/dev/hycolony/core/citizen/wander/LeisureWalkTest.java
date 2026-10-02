package dev.hycolony.core.citizen.wander;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.item.BlockKey;
import dev.hycolony.core.kernel.item.BlockKind;
import dev.hycolony.core.kernel.item.BlockState;
import dev.hycolony.core.kernel.port.NavStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

/** MC EntityAICitizenWander.goToLeisureSite and wanderAtLeisureSite, with walkToPos and walkToRandomPosWithin. */
class LeisureWalkTest extends WanderFixture {
    /** MC walkToPos: before any walk, a citizen within 1.5 blocks of the site is already there. */
    @Test
    void aCitizenAlreadyAtTheSiteDoesNotWalk() {
        standAt(new Vec3(1.9, 64, 0.5)); // in the next block: 1 block off
        rolls.ints.add(0);
        wander.wander();

        wander.leisure();
        rolls.ints.addAll(List.of(1, 0, 0, 0)); // stays, strolls to the box's corner
        wander.leisure();

        assertEquals(List.of(new Vec3(-19.5, 64, -19.5)), t.bodies.moves, "only the stroll");
    }

    /** MC walkToPos: 1.5 blocks before any walk, not the 3 of a stopped walk. */
    @Test
    void aCitizenTwoBlocksOffWalksToTheSite() {
        standAt(new Vec3(2.9, 64, 0.5));
        rolls.ints.add(0);
        wander.wander();

        wander.leisure();

        assertEquals(List.of(centre(CENTRE)), t.bodies.moves);
    }

    /** MC BlockPosUtil.dist from blockPosition(): block (1, 1) is √2 off, though the body is 2.1 blocks away. */
    @Test
    void theFirstStepMeasuresFromTheCitizensBlock() {
        standAt(new Vec3(1.99, 64, 1.99));
        rolls.ints.add(0);
        wander.wander();

        wander.leisure();

        assertEquals(List.of(), t.bodies.moves, "already there");
    }

    /** MC walkToPos once stopped: from block 3, 3 blocks off, though the body is 3.49 blocks away. */
    @Test
    void theArrivalMeasuresFromTheCitizensBlock() {
        rolls.ints.add(0);
        wander.wander();
        wander.leisure();
        t.bodies.bodies.get(body).position = new Vec3(3.99, 64, 0.5);
        t.bodies.bodies.get(body).status = NavStatus.ARRIVED;

        wander.leisure();

        assertEquals(List.of(centre(CENTRE)), t.bodies.moves, "arrived: not sent again");
    }

    /** MC PathJobRandomPos distSqr(start, spot) > 10²: a spot exactly 10 blocks off is passed for another. */
    @Test
    void aStrollSpotExactlyTenBlocksOffIsPassed() {
        standAt(new Vec3(0.1, 64, 0.5));
        rolls.ints.add(0);
        wander.wander();
        wander.leisure(); // already there
        rolls.ints.addAll(List.of(1, 0, 30, 20, 40, 40)); // (10, 0), 10.4 off as a body; then (20, 20)

        wander.leisure();

        assertEquals(List.of(new Vec3(20.5, 64, 20.5)), t.bodies.moves);
    }

    /** MC walkToPos: a walk to another target, still under way, is replaced at once. */
    @Test
    void aWalkStillUnderWayElsewhereIsReplaced() {
        t.bodies.frozen = true;
        rolls.ints.add(5);
        wander.wander(); // a wander walk, stuck
        t.clock.tick += CitizenWander.WANDER_TIMEOUT_TICKS;
        wander.wander(); // seen under way
        t.clock.tick += CitizenWander.WANDER_TIMEOUT_TICKS;
        rolls.ints.add(0);
        wander.wander(); // waited long enough: leisure

        wander.leisure();

        assertEquals(centre(CENTRE), t.bodies.moves.getLast());
        assertEquals(2, t.bodies.moves.size());
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
}
