package dev.hycolony.core.citizen.happiness;

import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;

/** What a dynamic factor reads from the colony and the citizen (MC HappinessFunctionEntry). */
@FunctionalInterface
public interface HappinessFunction {
    double apply(Colony colony, CitizenData citizen);
}
