package dev.hycolony.core.logistics.courier;

import static org.junit.jupiter.api.Assertions.assertFalse;

import dev.hycolony.core.kernel.config.ColonyConfig;
import dev.hycolony.core.testing.TestContexts;
import org.junit.jupiter.api.Test;

/** MC ServerConfiguration workersAlwaysWorkInRain (CitizenAI.shouldWorkWhileRaining). */
class CourierRainConfigTest extends CourierAITestBase {
    @Override
    TestContexts contexts() {
        TestContexts c = new TestContexts();
        ColonyConfig d = ColonyConfig.defaults();
        c.config = new ColonyConfig(
                new ColonyConfig.Gameplay(4, 250, true),
                d.claims(),
                d.permissions(),
                d.commands(),
                d.client(),
                d.hycolony());
        return c;
    }

    @Test
    void workersAlwaysWorkInRainKeepsTheCourierAtWork() {
        hire();

        t.world.raining = true;

        assertFalse(ai.canGoIdle());
    }
}
