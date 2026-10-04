package dev.hyangler.core.context;

import dev.hyangler.api.FishingContext;
import dev.hyangler.api.Tackle;
import dev.hyangler.api.WaterKind;
import dev.hyangler.core.port.BlockProbe;
import dev.hyangler.core.port.EnvironmentProbe;
import dev.hyangler.core.port.WeatherProbe;
import dev.hyangler.core.port.WorldClock;
import java.util.Set;

/**
 * Builds a catch's context at a bobber's block from the world's ports (spec § 8.2): environment and zone, salt water
 * from the salt environments of the id-map, depth, open water, sky, hour, weather, moon.
 */
public record ContextFactory(
        BlockProbe blocks,
        EnvironmentProbe environments,
        WorldClock clock,
        WeatherProbe weather,
        Set<String> saltEnvironments) {

    /** The context at (x, y, z) for this tackle; an hour of 24 is midnight. */
    public FishingContext at(int x, int y, int z, Tackle tackle) {
        String environment = environments.environment(x, y, z);
        WaterKind water = saltEnvironments.contains(environment) ? WaterKind.SALT : WaterKind.FRESH;
        double hour = clock.hour() % 24;
        return new FishingContext(
                environment,
                environments.zone(environment),
                water,
                WaterColumn.depth(blocks, x, y, z),
                OpenWater.test(blocks, x, y, z),
                blocks.skyVisible(x, y, z),
                hour < 0 || Double.isNaN(hour) ? 0 : hour,
                weather.weather(x, y, z),
                weather.raining(x, y, z),
                Math.max(0, clock.moonPhase()),
                tackle);
    }
}
