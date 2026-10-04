package dev.hyangler.core.cast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hyangler.core.testing.ScriptedRandom;
import org.junit.jupiter.api.Test;

class CastSessionTest {
    private static final CastInputs AIR = new CastInputs(false, false, 5, false, true);
    private static final CastInputs WATER = new CastInputs(true, false, 5, false, true);

    /** wait 101 - 100 = 1 tick (lure 1), approach 20, window 20; each tick in water draws two doubles. */
    private static ScriptedRandom quickBite(int ticksInWater) {
        ScriptedRandom r = new ScriptedRandom(101, 20, 20);
        for (int i = 0; i < ticksInWater; i++) {
            r.withDoubles(0.9, 0.9);
        }
        return r;
    }

    private static CastSession session(ScriptedRandom r) {
        return new CastSession(1, 32, 1.0, r);
    }

    @Test
    void aBobberThatLandsInWaterWaitsApproachesThenBites() {
        CastSession s = session(quickBite(22));
        assertEquals(CastState.FLYING, s.tick(AIR));
        assertEquals(CastState.FLOATING, s.tick(WATER));
        assertEquals(CastState.APPROACH, s.tick(WATER));
        for (int i = 0; i < 19; i++) {
            s.tick(WATER);
        }
        assertEquals(CastState.BITING, s.tick(WATER));
    }

    @Test
    void reelingDuringTheBiteCatches() {
        CastSession s = session(quickBite(22));
        s.tick(WATER);
        for (int i = 0; i < 21; i++) {
            s.tick(WATER);
        }
        assertEquals(CastState.BITING, s.state());
        assertEquals(CastEnd.CAUGHT, s.reel());
        assertEquals(1, CastEnd.CAUGHT.wear());
        assertEquals(CastState.ENDED, s.state());
    }

    @Test
    void reelingWhileWaitingCatchesNothingAndWearsNothing() {
        CastSession s = session(new ScriptedRandom(400, 20, 20).withDoubles(0.9, 0.9));
        s.tick(WATER);
        s.tick(WATER);
        assertEquals(CastEnd.ESCAPED, s.reel());
        assertEquals(0, CastEnd.ESCAPED.wear());
    }

    @Test
    void aBiteNotHookedPassesAndTheWaitStartsAgain() {
        ScriptedRandom r = new ScriptedRandom(101, 20, 20, 300, 20, 20);
        for (int i = 0; i < 43; i++) {
            r.withDoubles(0.9, 0.9);
        }
        CastSession s = session(r);
        s.tick(WATER);
        for (int i = 0; i < 21; i++) {
            s.tick(WATER);
        }
        assertEquals(CastState.BITING, s.state());
        for (int i = 0; i < 20; i++) {
            s.tick(WATER);
        }
        assertEquals(CastState.FLOATING, s.state());
    }

    @Test
    void aBobberOnTheGroundIsReeledInWithWearTwo() {
        CastSession s = session(new ScriptedRandom());
        assertEquals(CastState.GROUNDED, s.tick(new CastInputs(false, true, 5, false, true)));
        assertEquals(CastEnd.GROUNDED, s.reel());
        assertEquals(2, CastEnd.GROUNDED.wear());
    }

    @Test
    void theLineBreaksPastItsLength() {
        CastSession s = session(new ScriptedRandom());
        s.tick(new CastInputs(false, false, 33, false, true));
        assertEquals(CastEnd.BROKEN, s.end().orElseThrow());
    }

    @Test
    void aBobberFlyingTooLongIsCancelled() {
        CastSession s = session(new ScriptedRandom());
        for (int i = 0; i < CastSession.MAX_FLYING_TICKS; i++) {
            s.tick(AIR);
        }
        assertEquals(CastEnd.CANCELLED, s.end().orElseThrow());
    }

    @Test
    void noCastOutlivesItsSafetyBound() {
        ScriptedRandom r = new ScriptedRandom(600, 20, 20);
        for (int i = 0; i < CastSession.MAX_SESSION_TICKS; i++) {
            r.withDoubles(0.9, 0.2); // a hidden sky: the count never moves
        }
        CastSession s = new CastSession(0, 32, 1.0, r);
        CastInputs dark = new CastInputs(true, false, 5, false, false);
        for (int i = 0; i < CastSession.MAX_SESSION_TICKS && s.end().isEmpty(); i++) {
            s.tick(dark);
        }
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
