package dev.hydomum.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DomumConfigTest {
    @Test
    void cutterCraftSecondsIsClampedBetweenZeroAndTenAndDefaultsToHalfASecond() {
        assertEquals(0.0, new DomumConfig(-1).cutterCraftSeconds());
        assertEquals(10.0, new DomumConfig(99).cutterCraftSeconds());
        assertEquals(0.5, DomumConfig.defaults().cutterCraftSeconds());
    }
}
