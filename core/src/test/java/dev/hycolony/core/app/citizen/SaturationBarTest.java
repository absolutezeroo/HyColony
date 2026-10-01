package dev.hycolony.core.app.citizen;

import static dev.hycolony.core.app.citizen.SaturationBar.Icon.EMPTY;
import static dev.hycolony.core.app.citizen.SaturationBar.Icon.FULL;
import static dev.hycolony.core.app.citizen.SaturationBar.Icon.HALF;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/** MC CitizenWindowUtils.createSaturationBar: one icon per 6 saturation, full, half or empty. */
class SaturationBarTest {
    @Test
    void hungryIsTenEmptyIcons() {
        assertEquals(Collections.nCopies(10, EMPTY), SaturationBar.of(0));
    }

    @Test
    void fedIsTenFullIcons() {
        assertEquals(Collections.nCopies(10, FULL), SaturationBar.of(60));
    }

    @Test
    void aRemainderOfASixthIsAHalfIcon() {
        assertEquals(List.of(FULL, FULL, FULL, FULL, FULL, HALF, EMPTY, EMPTY, EMPTY, EMPTY), SaturationBar.of(33));
        assertEquals(List.of(HALF, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY), SaturationBar.of(3));
    }
}
