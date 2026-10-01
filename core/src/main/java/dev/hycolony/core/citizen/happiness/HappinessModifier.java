package dev.hycolony.core.citizen.happiness;

import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;

/**
 * One factor of a citizen's happiness and its weight (MC IHappinessModifier). Its factor is 1 when neutral, below 1
 * when it makes the citizen unhappy, above 1 when it pleases it.
 */
public sealed interface HappinessModifier permits StaticModifier, TimeBasedModifier, ExpirationModifier {
    /** MC getId: its id, also its translation key's tail ({@link HappinessIds}). */
    String id();

    /** MC getWeight. */
    double weight();

    /** MC getFactor(data): computes the factor now and keeps it as {@link #lastFactor}. */
    double factor(Colony colony, CitizenData citizen);

    /** MC getFactor(null): the factor as last computed, what the windows show. */
    double lastFactor();

    /** MC ITimeBasedHappinessModifier.dayEnd, at nightfall; nothing for a static one. */
    default void dayEnd(Colony colony, CitizenData citizen) {}

    /** MC ITimeBasedHappinessModifier.reset; nothing for a static one. */
    default void reset() {}

    /** MC getDays: the days counted (time-based) or left (expiring); 0 for a static one. */
    default int days() {
        return 0;
    }
}
