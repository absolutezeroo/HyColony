package dev.hyangler.core.testing;

import dev.hyangler.api.FishingContext;
import dev.hyangler.api.Tackle;
import dev.hyangler.api.WaterKind;

/** Test contexts: a plains river at noon, clear sky, and one field changed at a time. */
public final class Contexts {
    private Contexts() {}

    /** Env_Zone1_Plains, fresh water 3 deep, open water, sky visible, 12 h, sunny, moon 0, no tackle. */
    public static FishingContext base() {
        return new FishingContext(
                "Env_Zone1_Plains",
                "Zone1",
                WaterKind.FRESH,
                3,
                true,
                true,
                12.0,
                "Zone1_Sunny",
                false,
                0,
                Tackle.NONE);
    }

    /** The base context in another environment, zone and water. */
    public static FishingContext at(String environment, String zone, WaterKind water) {
        FishingContext b = base();
        return new FishingContext(
                environment,
                zone,
                water,
                b.depth(),
                b.openWater(),
                b.skyVisible(),
                b.hour(),
                b.weather(),
                b.raining(),
                b.moonPhase(),
                b.tackle());
    }

    /** The base context at another hour. */
    public static FishingContext hour(double hour) {
        FishingContext b = base();
        return new FishingContext(
                b.environment(),
                b.zone(),
                b.water(),
                b.depth(),
                b.openWater(),
                b.skyVisible(),
                hour,
                b.weather(),
                b.raining(),
                b.moonPhase(),
                b.tackle());
    }

    /** The base context in another weather. */
    public static FishingContext weather(String weather, boolean raining) {
        FishingContext b = base();
        return new FishingContext(
                b.environment(),
                b.zone(),
                b.water(),
                b.depth(),
                b.openWater(),
                b.skyVisible(),
                b.hour(),
                weather,
                raining,
                b.moonPhase(),
                b.tackle());
    }

    /** The base context over other water. */
    public static FishingContext water(int depth, boolean openWater, boolean skyVisible) {
        FishingContext b = base();
        return new FishingContext(
                b.environment(),
                b.zone(),
                b.water(),
                depth,
                openWater,
                skyVisible,
                b.hour(),
                b.weather(),
                b.raining(),
                b.moonPhase(),
                b.tackle());
    }

    /** The base context under another moon. */
    public static FishingContext moon(int phase) {
        FishingContext b = base();
        return new FishingContext(
                b.environment(),
                b.zone(),
                b.water(),
                b.depth(),
                b.openWater(),
                b.skyVisible(),
                b.hour(),
                b.weather(),
                b.raining(),
                phase,
                b.tackle());
    }

    /** The base context with other tackle. */
    public static FishingContext tackle(Tackle tackle) {
        FishingContext b = base();
        return new FishingContext(
                b.environment(),
                b.zone(),
                b.water(),
                b.depth(),
                b.openWater(),
                b.skyVisible(),
                b.hour(),
                b.weather(),
                b.raining(),
                b.moonPhase(),
                tackle);
    }
}
