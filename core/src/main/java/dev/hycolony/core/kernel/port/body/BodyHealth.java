package dev.hycolony.core.kernel.port.body;

import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.port.BodyId;
import java.util.Optional;

/**
 * A citizen body's health, what hunger does to it and what threatens it (MC EntityCitizen's health, heal,
 * MOVEMENT_SLOWDOWN and the monsters it avoids). Never
 * throws; an unknown body or one not alive in a loaded world answers 0 or false, and a call on it does nothing.
 */
public interface BodyHealth {
    /** The body's health, on Hytale's scale (a citizen has 100, {@code CitizenData.MAX_HEALTH}). */
    double health(BodyId body);

    /** The body's maximum health (MC getMaxHealth, 100 for a citizen on Hytale's scale). */
    double maxHealth(BodyId body);

    /**
     * Hurts the body by {@code amount} as its being stuck in place would (MC STUCK_DAMAGE), through the world's damage
     * rules: armour, the citizen's hit filter, death at 0.
     */
    void damage(BodyId body, double amount);

    /** Heals the body by {@code amount}, up to its maximum (MC LivingEntity.heal). */
    void heal(BodyId body, double amount);

    /**
     * The position of the nearest living hostile creature in the body's bounding box inflated by {@code range} blocks
     * horizontally and 3 vertically (MC EntityAICitizenAvoidEntity.getClosestToAvoid, a Monster); empty without one.
     */
    Optional<Vec3> nearestThreat(BodyId body, double range);

    /** Slows the body down while it starves (MC MOVEMENT_SLOWDOWN 0: -15 % on top of its job's speed), or ends it. */
    void setStarving(BodyId body, boolean starving);
}
