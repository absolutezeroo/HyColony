package dev.hycolony.core.kernel.port.body;

import dev.hycolony.core.kernel.port.BodyId;

/**
 * A citizen body's health and what hunger does to it (MC EntityCitizen's health, heal and MOVEMENT_SLOWDOWN). Never
 * throws; an unknown body or one not alive in a loaded world answers 0 or false, and a call on it does nothing.
 */
public interface BodyHealth {
    /** The body's health, on MC's scale (a citizen has 20). */
    double health(BodyId body);

    /** The body's maximum health (MC getMaxHealth, 20 for a citizen). */
    double maxHealth(BodyId body);

    /** Heals the body by {@code amount}, up to its maximum (MC LivingEntity.heal). */
    void heal(BodyId body, double amount);

    /** Whether something hurt the body less than 100 ticks ago (MC getLastHurtByMob() != null). */
    boolean recentlyHurt(BodyId body);

    /** Slows the body down while it starves (MC MOVEMENT_SLOWDOWN 0: -15 % on top of its job's speed), or ends it. */
    void setStarving(BodyId body, boolean starving);
}
