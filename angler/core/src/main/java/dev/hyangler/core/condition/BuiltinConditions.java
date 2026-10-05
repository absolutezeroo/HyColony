package dev.hyangler.core.condition;

import dev.hyangler.api.WaterKind;
import dev.hyangler.api.condition.Condition;
import dev.hyangler.api.condition.ConditionSpec;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** HyAngler's own condition types (spec § 6.4), read from their fields; each throws on a missing field. */
final class BuiltinConditions {
    private BuiltinConditions() {}

    /** Registers Environment, Zone, Water, Depth, Time, Weather, Moon, OpenWater and Sky. */
    static void registerAll(ConditionRegistry registry) {
        registry.register("Environment", spec -> {
            Set<String> ids = ids(spec, "Ids");
            return ctx -> ids.contains(ctx.environment());
        });
        registry.register("Zone", spec -> {
            Set<String> ids = ids(spec, "Ids");
            return ctx -> ids.contains(ctx.zone());
        });
        registry.register("Water", BuiltinConditions::water);
        registry.register("Depth", BuiltinConditions::depth);
        registry.register("Time", BuiltinConditions::time);
        registry.register("Weather", BuiltinConditions::weather);
        registry.register("Moon", BuiltinConditions::moon);
        registry.register("OpenWater", spec -> ctx -> ctx.openWater());
        registry.register("Sky", spec -> {
            boolean visible = spec.bool("Visible").orElse(true);
            return ctx -> ctx.skyVisible() == visible;
        });
    }

    private static Set<String> ids(ConditionSpec spec, String key) {
        List<String> ids = spec.strings(key);
        if (ids.isEmpty()) {
            throw new IllegalArgumentException(spec.type() + " needs " + key);
        }
        return Set.copyOf(ids);
    }

    private static Condition water(ConditionSpec spec) {
        String kind = spec.string("Kind").orElseThrow(() -> new IllegalArgumentException("Water needs Kind"));
        WaterKind water = switch (kind.toLowerCase(Locale.ROOT)) {
            case "fresh" -> WaterKind.FRESH;
            case "salt" -> WaterKind.SALT;
            default -> throw new IllegalArgumentException("Water Kind is Fresh or Salt, not " + kind);
        };
        return ctx -> ctx.water() == water;
    }

    private static Condition depth(ConditionSpec spec) {
        int min = spec.integer("Min").orElse(0);
        int max = spec.integer("Max").orElse(Integer.MAX_VALUE);
        if (min < 0 || max < min) {
            throw new IllegalArgumentException("Depth needs 0 <= Min <= Max");
        }
        return ctx -> ctx.depth() >= min && ctx.depth() <= max;
    }

    private static Condition time(ConditionSpec spec) {
        double from = spec.number("From").orElse(-1);
        double to = spec.number("To").orElse(-1);
        if (from < 0 || from > 24 || to < 0 || to > 24) {
            throw new IllegalArgumentException("Time needs From and To, from 0 to 24");
        }
        if (from == to) {
            throw new IllegalArgumentException("Time needs From and To to differ");
        }
        TimeWindow window = new TimeWindow(from, to);
        return ctx -> window.contains(ctx.hour());
    }

    /** The weather is one of Ids, and the rain is Rain; each key given must hold. */
    private static Condition weather(ConditionSpec spec) {
        Set<String> ids = Set.copyOf(spec.strings("Ids"));
        Optional<Boolean> rain = spec.bool("Rain");
        if (ids.isEmpty() && rain.isEmpty()) {
            throw new IllegalArgumentException("Weather needs Ids or Rain");
        }
        return ctx -> (ids.isEmpty() || ids.contains(ctx.weather()))
                && rain.map(r -> r == ctx.raining()).orElse(true);
    }

    private static Condition moon(ConditionSpec spec) {
        Set<Integer> phases = Set.copyOf(spec.integers("Phases"));
        if (phases.isEmpty()) {
            throw new IllegalArgumentException("Moon needs Phases");
        }
        return ctx -> phases.contains(ctx.moonPhase());
    }
}
