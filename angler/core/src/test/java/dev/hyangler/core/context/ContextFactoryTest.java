package dev.hyangler.core.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hyangler.api.FishingContext;
import dev.hyangler.api.Tackle;
import dev.hyangler.api.WaterKind;
import dev.hyangler.core.port.BlockKind;
import dev.hyangler.core.port.EnvironmentProbe;
import dev.hyangler.core.port.WeatherProbe;
import dev.hyangler.core.port.WorldClock;
import dev.hyangler.core.testing.FakeBlocks;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ContextFactoryTest {
    private static final WorldClock DUSK = clock(19.5, 2);
    private static final WeatherProbe RAIN = new WeatherProbe() {
        @Override
        public String weather(int x, int y, int z) {
            return "Zone1_Rain";
        }

        @Override
        public boolean raining(int x, int y, int z) {
            return true;
        }
    };

    private static WorldClock clock(double hour, int moon) {
        return new WorldClock() {
            @Override
            public double hour() {
                return hour;
            }

            @Override
            public int moonPhase() {
                return moon;
            }
        };
    }

    private static EnvironmentProbe env(String id) {
        return new EnvironmentProbe() {
            @Override
            public String environment(int x, int y, int z) {
                return id;
            }

            @Override
            public String zone(String environment) {
                return environment.isEmpty() ? "" : "Zone" + environment.charAt(8);
            }
        };
    }

    @Test
    void theContextReadsEveryProbeAtTheBobber() {
        FakeBlocks pond = new FakeBlocks().fill(-5, 5, -5, 5, 10, 5, BlockKind.WATER_SOURCE);
        FishingContext c = new ContextFactory(pond, env("Env_Zone1_Forests"), DUSK, RAIN, Set.of("Env_Zone1_Shores"))
                .at(0, 10, 0, Tackle.of(1, 0, 32));
        assertEquals("Env_Zone1_Forests", c.environment());
        assertEquals("Zone1", c.zone());
        assertEquals(WaterKind.FRESH, c.water());
        assertEquals(6, c.depth());
        assertTrue(c.openWater());
        assertEquals(19.5, c.hour());
        assertEquals(2, c.moonPhase());
        assertTrue(c.skyVisible());
        assertEquals("Zone1_Rain", c.weather());
        assertTrue(c.raining());
        assertEquals(1, c.tackle().lure());
    }

    @Test
    void aSaltEnvironmentGivesSaltWater() {
        FishingContext c = new ContextFactory(
                        new FakeBlocks(), env("Env_Zone1_Shores"), DUSK, RAIN, Set.of("Env_Zone1_Shores"))
                .at(0, 10, 0, Tackle.NONE);
        assertEquals(WaterKind.SALT, c.water());
        assertFalse(c.openWater());
    }

    @Test
    void theSaltEnvironmentsAreCopiedOnce() {
        Set<String> salt = new HashSet<>(Set.of("Env_Zone1_Shores"));
        ContextFactory factory = new ContextFactory(new FakeBlocks(), env("Env_Zone1_Shores"), DUSK, RAIN, salt);
        salt.clear();
        assertEquals(WaterKind.SALT, factory.at(0, 10, 0, Tackle.NONE).water());
    }

    @Test
    void anOpenWaterHeldThroughTheCastReplacesTheOneAtTheCatch() {
        FakeBlocks pond = new FakeBlocks().fill(-5, 5, -5, 5, 10, 5, BlockKind.WATER_SOURCE);
        ContextFactory factory = new ContextFactory(pond, env("Env_Zone1_Forests"), DUSK, RAIN, Set.of());
        assertTrue(factory.openWater(0, 10, 0));
        assertFalse(factory.at(0, 10, 0, Tackle.NONE, false).openWater());
    }

    @Test
    void aHiddenSkyIsReadAtTheBobber() {
        FishingContext c =
                new ContextFactory(new FakeBlocks().sky(false), env(""), DUSK, RAIN, Set.of()).at(0, 0, 0, Tackle.NONE);
        assertFalse(c.skyVisible());
    }

    @Test
    void aClockGoneWrongGivesMidnightAndTheFirstMoon() {
        for (double hour : new double[] {-3, Double.NaN}) {
            FishingContext c = new ContextFactory(new FakeBlocks(), env(""), clock(hour, -1), RAIN, Set.of())
                    .at(0, 0, 0, Tackle.NONE);
            assertEquals(0.0, c.hour());
            assertEquals(0, c.moonPhase());
        }
    }

    @Test
    void anHourOfTwentyFourIsMidnight() {
        assertEquals(
                0.0,
                new ContextFactory(new FakeBlocks(), env(""), clock(24.0, 0), RAIN, Set.of())
                        .at(0, 0, 0, Tackle.NONE)
                        .hour());
    }
}
