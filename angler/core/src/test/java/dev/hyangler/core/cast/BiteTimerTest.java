package dev.hyangler.core.cast;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.hyangler.api.BiteTimes;
import dev.hyangler.core.testing.ScriptedRandom;
import org.junit.jupiter.api.Test;

class BiteTimerTest {

    @Test
    void lureShortensTheWaitByAHundredTicksALevel() {
        BiteTimes t = BiteTimer.roll(2, 1.0, new ScriptedRandom(350, 50, 30));
        assertEquals(150, t.waitTicks());
        assertEquals(50, t.approachTicks());
        assertEquals(30, t.windowTicks());
    }

    @Test
    void theWaitNeverFallsBelowOneTick() {
        assertEquals(1, BiteTimer.roll(3, 1.0, new ScriptedRandom(150, 20, 20)).waitTicks());
    }

    @Test
    void theConfigsMultiplierScalesTheWait() {
        assertEquals(
                400, BiteTimer.roll(0, 2.0, new ScriptedRandom(200, 20, 20)).waitTicks());
    }

    @Test
    void rainSpeedsTheCountAndAHiddenSkySlowsIt() {
        assertEquals(2, BiteTimer.step(true, true, new ScriptedRandom().withDoubles(0.1, 0.9)));
        assertEquals(1, BiteTimer.step(true, true, new ScriptedRandom().withDoubles(0.3, 0.9)));
        assertEquals(0, BiteTimer.step(false, false, new ScriptedRandom().withDoubles(0.9, 0.2)));
        assertEquals(1, BiteTimer.step(true, false, new ScriptedRandom().withDoubles(0.1, 0.2)));
    }
}
