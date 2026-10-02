package dev.hylens.core.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** The menu's saturation buttons, as MC's suggestions (spec 2026-10-02 lot 2, § 5). */
class SaturationStepTest {
    @Test
    void zeroAndMaxAreMcsSuggestionsForEquals() {
        assertEquals(0, SaturationStep.ZERO.from(42, 60));
        assertEquals(60, SaturationStep.MAX.from(42, 60));
    }

    @Test
    void lessAndMoreStepByOneAsMcsSuggestion() {
        assertEquals(41, SaturationStep.LESS.from(42, 60));
        assertEquals(43, SaturationStep.MORE.from(42, 60));
    }

    @Test
    void theStepStaysBetweenZeroAndTheMaximum() {
        assertEquals(0, SaturationStep.LESS.from(0.5, 60));
        assertEquals(60, SaturationStep.MORE.from(59.5, 60));
    }
}
