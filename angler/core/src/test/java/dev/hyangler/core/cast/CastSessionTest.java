package dev.hyangler.core.cast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hyangler.core.testing.ScriptedRandom;
import org.junit.jupiter.api.Test;

class CastSessionTest {
    private static final CastInputs AIR = new CastInputs(false, false, 5, false, true, true);
    private static final CastInputs WATER = new CastInputs(true, false, 5, false, true, true);
    private static final CastInputs GROUND = new CastInputs(false, true, 5, false, true, true);
    /** Water around the bobber, but not open water. */
    private static final CastInputs POND = new CastInputs(true, false, 5, false, true, false);
    /** A bobbing bobber whose water was taken away: the same inputs as AIR, named for what they mean there. */
    private static final CastInputs DRY = AIR;

    /** Queues one counted tick (rain and sky drawn, neither applies) per tick. */
    private static ScriptedRandom counted(ScriptedRandom r, int ticks) {
        for (int i = 0; i < ticks; i++) {
            r.withDoubles(0.9, 0.9);
        }
        return r;
    }

    /** wait 101 - 100 = 1 tick (lure 1), approach 20, window 20. */
    private static ScriptedRandom quickBite(int countedTicks) {
        return counted(new ScriptedRandom(101, 20, 20), countedTicks);
    }

    private static CastSession session(ScriptedRandom r) {
        return new CastSession(1, 32, 1.0, r);
    }

    private static void tick(CastSession s, CastInputs in, int times) {
        for (int i = 0; i < times; i++) {
            s.tick(in);
        }
    }

    /** A quick bite's session three ticks in: landed, wait drawn, now approaching (one counted tick spent). */
    private static CastSession approaching(int countedTicks) {
        CastSession s = session(quickBite(countedTicks));
        tick(s, WATER, 3);
        assertEquals(CastState.APPROACH, s.state());
        return s;
    }

    @Test
    void aBobberThatLandsInWaterDrawsItsWaitNextTickThenApproachesAndBites() {
        CastSession s = session(quickBite(21));
        assertEquals(CastState.FLYING, s.tick(AIR));
        assertEquals(CastState.FLOATING, s.tick(WATER));
        assertEquals(CastState.FLOATING, s.tick(WATER));
        assertEquals(CastState.APPROACH, s.tick(WATER));
        tick(s, WATER, 19);
        assertEquals(CastState.APPROACH, s.state());
        assertEquals(CastState.BITING, s.tick(WATER));
    }

    @Test
    void reelingDuringTheBiteCatchesAndWearsOne() {
        CastSession s = session(quickBite(21));
        tick(s, WATER, 23);
        assertEquals(CastState.BITING, s.state());
        assertEquals(CastEnd.CAUGHT, s.reel());
        assertEquals(1, s.wear());
        assertEquals(CastState.ENDED, s.state());
    }

    @Test
    void reelingWhileWaitingCatchesNothingAndWearsNothing() {
        CastSession s = session(counted(new ScriptedRandom(400, 20, 20), 1));
        tick(s, WATER, 3);
        assertEquals(CastEnd.ESCAPED, s.reel());
        assertEquals(0, s.wear());
    }

    @Test
    void reelingInFlightCatchesNothing() {
        CastSession s = session(new ScriptedRandom());
        s.tick(AIR);
        assertEquals(CastEnd.ESCAPED, s.reel());
        assertEquals(0, s.wear());
    }

    @Test
    void aBiteNotHookedPassesAndTheWaitIsDrawnAgain() {
        CastSession s = session(counted(new ScriptedRandom(101, 20, 20, 101, 20, 20), 22));
        tick(s, WATER, 23);
        assertEquals(CastState.BITING, s.state());
        tick(s, WATER, 20);
        assertEquals(CastState.FLOATING, s.state());
        assertEquals(CastState.FLOATING, s.tick(WATER));
        assertEquals(CastState.APPROACH, s.tick(WATER));
    }

    @Test
    void aTenfoldBiteTimeStillBites() {
        CastSession s = new CastSession(0, 32, 10.0, counted(new ScriptedRandom(600, 20, 20), 6020));
        tick(s, WATER, 2 + 6000 + 20);
        assertEquals(CastState.BITING, s.state());
    }

    @Test
    void theWaitHoldsWhileTheBobberIsOutOfTheWater() {
        CastSession s = session(quickBite(1));
        tick(s, WATER, 2);
        tick(s, DRY, 50); // no tick counted: an unscripted draw would fail the test
        assertEquals(CastState.FLOATING, s.state());
        assertEquals(CastState.APPROACH, s.tick(WATER));
    }

    @Test
    void aWaitThatNeverEndsIsLostWithinItsBound() {
        CastSession s = new CastSession(0, 32, 1.0, new ScriptedRandom(600, 20, 20));
        tick(s, WATER, 2);
        int bound = CastSession.CYCLE_SLACK * (600 + 20 + 20);
        tick(s, DRY, bound - 1);
        assertEquals(CastState.FLOATING, s.state());
        s.tick(DRY);
        assertEquals(CastEnd.CANCELLED, s.end().orElseThrow());
    }

    @Test
    void aBobberOnTheGroundIsReeledInWithWearTwo() {
        CastSession s = session(new ScriptedRandom());
        assertEquals(CastState.GROUNDED, s.tick(GROUND));
        assertEquals(CastEnd.GROUNDED, s.reel());
        assertEquals(2, s.wear());
    }

    @Test
    void aBiteReeledInOnTheGroundCatchesAndWearsTwo() {
        CastSession s = session(quickBite(21));
        tick(s, WATER, 23);
        s.tick(GROUND); // the water is gone and the bobber lies on the ground: the bite holds
        assertEquals(CastEnd.CAUGHT, s.reel());
        assertEquals(2, s.wear());
    }

    @Test
    void aBobberLeftOnTheGroundIsLostAfterVanillasMinute() {
        assertEquals(1200, CastSession.MAX_GROUNDED_TICKS); // vanilla FishingHook.tick: life >= 1200
        CastSession s = session(new ScriptedRandom());
        tick(s, GROUND, CastSession.MAX_GROUNDED_TICKS - 1);
        assertEquals(CastState.GROUNDED, s.state());
        s.tick(GROUND);
        assertEquals(CastEnd.CANCELLED, s.end().orElseThrow());
        assertEquals(0, s.wear());
    }

    @Test
    void leavingTheGroundRestartsItsCount() {
        CastSession s = session(new ScriptedRandom());
        tick(s, GROUND, 1000);
        s.tick(AIR);
        tick(s, GROUND, CastSession.MAX_GROUNDED_TICKS - 1);
        assertEquals(CastState.GROUNDED, s.state());
    }

    @Test
    void aBobberBouncingBetweenGroundAndAirIsLostWithinItsFlightBound() {
        CastSession s = session(new ScriptedRandom());
        for (int i = 0; i < CastSession.MAX_FLYING_TICKS && s.end().isEmpty(); i++) {
            s.tick(GROUND); // counted against the flight bound: it was flying
            s.tick(AIR); // not counted: it was on the ground
        }
        assertEquals(CastEnd.CANCELLED, s.end().orElseThrow());
    }

    @Test
    void aBobberThatRollsFromTheGroundIntoWaterFishes() {
        CastSession s = session(new ScriptedRandom());
        s.tick(GROUND);
        assertEquals(CastState.FLOATING, s.tick(WATER));
    }

    @Test
    void openWaterHoldsOnlyIfItHeldAtEveryTickOfTheApproach() {
        CastSession s = approaching(3);
        assertTrue(s.openWater());
        s.tick(POND);
        assertFalse(s.openWater());
        s.tick(WATER);
        assertFalse(s.openWater());
    }

    @Test
    void openWaterComesBackWhenTheWaitIsDrawnAgain() {
        CastSession s = session(counted(new ScriptedRandom(101, 20, 20, 101, 20, 20), 22));
        tick(s, WATER, 3);
        s.tick(POND);
        tick(s, WATER, 19 + 20);
        assertEquals(CastState.FLOATING, s.state());
        s.tick(WATER);
        assertTrue(s.openWater());
    }

    @Test
    void tenTicksOutOfTheWaterLoseOpenWater() {
        CastSession nine = approaching(2);
        tick(nine, DRY, 9);
        nine.tick(WATER);
        assertTrue(nine.openWater());
        CastSession ten = approaching(2);
        tick(ten, DRY, 10);
        ten.tick(WATER);
        assertFalse(ten.openWater());
    }

    @Test
    void aTickInTheWaterOnlyTakesOneOffTheTicksOutOfIt() {
        CastSession s = approaching(3);
        tick(s, DRY, 9);
        s.tick(WATER); // 9 out of the water, then 8
        tick(s, DRY, 2); // 9, then 10
        assertTrue(s.openWater());
        s.tick(WATER);
        assertFalse(s.openWater());
    }

    @Test
    void openWaterIsJudgedOnTheStateTheTickStartsIn() {
        CastSession s = approaching(21);
        tick(s, WATER, 19);
        assertEquals(CastState.BITING, s.tick(POND));
        assertFalse(s.openWater());
        tick(s, WATER, 20);
        assertEquals(CastState.FLOATING, s.state());
        assertFalse(s.openWater()); // still the bite's: it comes back true on the next tick
    }

    @Test
    void theCountdownTellsTheTicksLeftOfTheApproach() {
        CastSession s = approaching(2);
        assertEquals(20, s.countdown());
        s.tick(WATER);
        assertEquals(19, s.countdown());
    }

    @Test
    void theLineBreaksPastItsLength() {
        CastSession s = session(new ScriptedRandom());
        s.tick(new CastInputs(false, false, 33, false, true, true));
        assertEquals(CastEnd.BROKEN, s.end().orElseThrow());
    }

    @Test
    void aBobberFlyingTooLongIsCancelled() {
        CastSession s = session(new ScriptedRandom());
        tick(s, AIR, CastSession.MAX_FLYING_TICKS - 1);
        assertEquals(CastState.FLYING, s.state());
        s.tick(AIR);
        assertEquals(CastEnd.CANCELLED, s.end().orElseThrow());
    }

    @Test
    void anEndedCastStaysEnded() {
        CastSession s = session(new ScriptedRandom());
        s.cancel();
        assertEquals(CastState.ENDED, s.tick(WATER));
        assertEquals(CastEnd.CANCELLED, s.reel());
        assertTrue(s.end().isPresent());
    }
}
