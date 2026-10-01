package dev.hycolony.api.read;

import dev.hycolony.api.CitizenRef;
import dev.hycolony.api.Experimental;
import java.util.List;

/**
 * How a citizen fares, as it was when read (MineColonies' saturation and happiness). Experimental until a second
 * client settles what it needs.
 *
 * <ul>
 *   <li>{@code saturation}: from 0 (starving) to {@code maxSaturation} (full, 60 as MineColonies).
 *   <li>{@code happiness}: from 0 to 10.
 *   <li>{@code factors}: each happiness modifier the citizen has, in its order, with its factor (1 is neutral).
 * </ul>
 *
 * @since 1.1
 */
@Experimental
public record CitizenWellbeing(
        CitizenRef citizen, double saturation, double maxSaturation, double happiness, List<HappinessFactor> factors) {
    /** Keeps its own copy of the factors. */
    public CitizenWellbeing {
        factors = List.copyOf(factors);
    }

    /**
     * One happiness modifier: its MineColonies id ({@code homelessness}, {@code food}...; the list may grow) and its
     * last factor.
     *
     * @since 1.1
     */
    @Experimental
    public record HappinessFactor(String id, double factor) {}
}
