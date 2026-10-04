package dev.hycolony.core.citizen.death;

import java.util.Optional;

/**
 * What killed a citizen (MC DamageSource): Hytale's damage cause id ("Fall", "Physical"..., "Unknown" for one the
 * plugin does not know) and its killer's name when an entity killed it, as a message parameter (a player's name, or
 * {@code %} and a translation key).
 */
public record DeathCause(String cause, Optional<String> killer) {}
