package dev.hyangler.api;

import java.util.Objects;

/**
 * Where and when a catch is rolled, seen from the bobber's block (spec § 8.1). It holds no player and no bobber: an
 * NPC builds one as a player's rod does. Strings are Hytale asset ids, empty when unknown.
 *
 * @param environment the block's environment ({@code Env_Zone1_Plains}…)
 * @param zone the environment's zone ({@code Zone1}…)
 * @param water fresh or salt
 * @param depth water blocks below the surface, under the bobber
 * @param openWater the vanilla open-water rule (a 5 x 4 x 5 cube of water below and air above)
 * @param skyVisible whether the bobber sees the sky
 * @param hour the world's hour of day, from 0 (inclusive) to 24 (exclusive)
 * @param weather the weather's asset id at the block
 * @param raining whether rain falls on the bobber
 * @param moonPhase the moon phase index, from 0
 * @param tackle the angler's tackle
 * @since 1.0
 */
public record FishingContext(
        String environment,
        String zone,
        WaterKind water,
        int depth,
        boolean openWater,
        boolean skyVisible,
        double hour,
        String weather,
        boolean raining,
        int moonPhase,
        Tackle tackle) {
    /** Throws {@link IllegalArgumentException} on an hour outside [0, 24), a negative depth or moon phase. */
    public FishingContext {
        Objects.requireNonNull(environment, "environment");
        Objects.requireNonNull(zone, "zone");
        Objects.requireNonNull(water, "water");
        Objects.requireNonNull(weather, "weather");
        Objects.requireNonNull(tackle, "tackle");
        if (!(hour >= 0 && hour < 24) || depth < 0 || moonPhase < 0) {
            throw new IllegalArgumentException("invalid context: hour " + hour + ", depth " + depth);
        }
    }
}
