package dev.hyangler.core.cast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
    void theLongestDelaysAreSixHundredThenEightyThenForty() {
        BiteTimes t = BiteTimer.roll(0, 1.0, new ScriptedRandom(600, 80, 40));
        assertEquals(new BiteTimes(600, 80, 40), t);
    }

    @Test
    void aHighLureDrawsOnlyWaitsOfOneTickOrMore() {
        assertEquals(1, BiteTimer.roll(3, 1.0, new ScriptedRandom(301, 20, 20)).waitTicks());
        assertThrows(AssertionError.class, () -> BiteTimer.roll(3, 1.0, new ScriptedRandom(300, 20, 20)));
    }

    @Test
    void aNegativeLureCountsAsNone() {
        assertEquals(
                500, BiteTimer.roll(-3, 1.0, new ScriptedRandom(500, 20, 20)).waitTicks());
    }

    @Test
    void aLureAboveFiveCountsAsFive() {
        assertEquals(
                100, BiteTimer.roll(9, 1.0, new ScriptedRandom(600, 20, 20)).waitTicks());
    }

    @Test
    void theConfigsMultiplierScalesTheWaitToAtLeastOneTick() {
        assertEquals(
                400, BiteTimer.roll(0, 2.0, new ScriptedRandom(200, 20, 20)).waitTicks());
        assertEquals(1, BiteTimer.roll(5, 0.1, new ScriptedRandom(501, 20, 20)).waitTicks());
    }

    @Test
    void aFishTeasesMoreAsTheWaitEnds() {
        assertEquals(0.15, BiteTimer.teaseChance(100), 1e-12);
        assertEquals(0.15 + 10 * 0.01, BiteTimer.teaseChance(50), 1e-12);
        assertEquals(0.15 + 10 * 0.02, BiteTimer.teaseChance(30), 1e-12);
        assertEquals(0.15 + 10 * 0.05, BiteTimer.teaseChance(10), 1e-12);
        assertEquals(0.15 + 19 * 0.05, BiteTimer.teaseChance(1), 1e-12);
    }

    @Test
    void theTeasingStepsChangeExactlyAtSixtyFortyAndTwenty() {
        // vanilla compares strictly (FishingHook.catchingFish: < 20, < 40, < 60)
        assertEquals(0.15, BiteTimer.teaseChance(60), 1e-12);
        assertEquals(0.15 + 1 * 0.01, BiteTimer.teaseChance(59), 1e-12);
        assertEquals(0.15 + 20 * 0.01, BiteTimer.teaseChance(40), 1e-12);
        assertEquals(0.15 + 1 * 0.02, BiteTimer.teaseChance(39), 1e-12);
        assertEquals(0.15 + 20 * 0.02, BiteTimer.teaseChance(20), 1e-12);
        assertEquals(0.15 + 1 * 0.05, BiteTimer.teaseChance(19), 1e-12);
    }

    @Test
    void rainSpeedsTheCountAndAHiddenSkySlowsIt() {
        assertEquals(2, BiteTimer.step(true, true, new ScriptedRandom().withDoubles(0.1, 0.9)));
        assertEquals(1, BiteTimer.step(true, true, new ScriptedRandom().withDoubles(0.3, 0.9)));
        assertEquals(0, BiteTimer.step(false, false, new ScriptedRandom().withDoubles(0.9, 0.2)));
        assertEquals(1, BiteTimer.step(true, false, new ScriptedRandom().withDoubles(0.1, 0.2)));
    }
}
