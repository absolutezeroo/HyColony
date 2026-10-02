package dev.hylens.core.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.hycolony.api.ApiText;
import dev.hycolony.api.debug.SaturationChange;
import org.junit.jupiter.api.Test;

/** The menu's saturation buttons, as MC's operators and suggestions (spec 2026-10-02 lot 2, § 5). */
class SaturationStepTest {
    @Test
    void theMenuShowsTheSaturationWithOneDecimalAsTheWatchPanel() {
        assertEquals(ApiText.of("hylens.menu.saturation", "7.3", "20"), new MenuView.Saturation(7.26, 20.9).text());
    }

    @Test
    void zeroAndMaxSetMcsSuggestionsForEquals() {
        assertEquals(SaturationChange.SET, SaturationStep.ZERO.change());
        assertEquals(0, SaturationStep.ZERO.value(60));
        assertEquals(SaturationChange.SET, SaturationStep.MAX.change());
        assertEquals(60, SaturationStep.MAX.value(60));
    }

    @Test
    void lessAndMoreAreMcsMinusAndPlusByItsSuggestedOne() {
        assertEquals(SaturationChange.DECREASE, SaturationStep.LESS.change());
        assertEquals(1, SaturationStep.LESS.value(60));
        assertEquals(SaturationChange.INCREASE, SaturationStep.MORE.change());
        assertEquals(1, SaturationStep.MORE.value(60));
    }
}
