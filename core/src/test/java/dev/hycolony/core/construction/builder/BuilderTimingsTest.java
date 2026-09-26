package dev.hycolony.core.construction.builder;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BuilderTimingsTest {
    @Test
    void placeDelayFormula() {
        // 15 * 10 / (p / 2 + 10), integer arithmetic as in MC.
        assertEquals(15, BuilderTimings.placeDelay(1)); // 150 / 10
        assertEquals(7, BuilderTimings.placeDelay(20)); // 150 / 20
        assertEquals(2, BuilderTimings.placeDelay(99)); // 150 / 59
    }

    @Test
    void breakDelayFormulaAndMinimum() {
        // (int) ((int) (500 * 0.85^(s / 2.0) * h / speed) * 0.5)
        assertEquals(250, BuilderTimings.breakDelay(0, 1f, 1f));
        assertEquals(55, BuilderTimings.breakDelay(10, 2f, 4f)); // 500 * 0.4437 * 2 / 4 = 110.9 -> 110 -> 55
        assertEquals(1, BuilderTimings.breakDelay(99, 0.1f, 100f));
        assertEquals(1, BuilderTimings.breakDelay(0, 0f, 1f));
    }
}
