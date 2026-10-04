package dev.hycolony.core.citizen.death;

import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;

/** MC CitizenDiedModEvent: {@code citizen} of {@code colony} died; it is no longer in the colony. */
public record CitizenDied(Colony colony, CitizenData citizen, DeathCause cause) {}
