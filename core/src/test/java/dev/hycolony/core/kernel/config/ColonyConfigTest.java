package dev.hycolony.core.kernel.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ColonyConfigTest {
    @Test
    void autosaveIntervalIsClampedToAtLeastOneMinute() {
        ColonyConfig c = new ColonyConfig(4, 250, 4, 8, 20, true, 0, false, true);
        assertEquals(1, c.autosaveIntervalMinutes());
    }
}
