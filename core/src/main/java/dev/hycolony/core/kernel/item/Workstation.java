package dev.hycolony.core.kernel.item;

import java.util.Objects;

/**
 * A crafting bench a hut owns through its plan, and the tier its plan gave it (Hytale {@code BenchBlock.TierLevel},
 * from 1). Deviation from MC: MC benches have no tier; Hytale recipes ask for a bench tier.
 */
public record Workstation(String benchId, int tier) {
    public Workstation {
        Objects.requireNonNull(benchId, "benchId");
        if (tier < 1) {
            throw new IllegalArgumentException("tier must be >= 1: " + tier);
        }
    }
}
