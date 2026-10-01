package dev.hycolony.core.citizen.food;

import dev.hycolony.core.kernel.Vec3;

/**
 * A citizen's hunger: its saturation, whether it just ate, its last meals, and the work and walking that will cost
 * it saturation at the next idle decrease. Port of MC CitizenData's saturation and justAte, CitizenFoodHandler, and
 * EntityCitizen's cachedActionSaturationDecrease and walking count (those two are runtime only, as on MC's entity).
 */
public final class CitizenHunger {
    /** MC CitizenConstants.BIG_SATURATION_FACTOR: saturation one work action costs. */
    static final double BIG_SATURATION_FACTOR = 0.2;
    /** MC CitizenConstants.SATURATION_DECREASE_FACTOR: saturation a continuous action costs. */
    static final double SATURATION_DECREASE_FACTOR = 0.02;
    /** MC Constants.ACTIONS_EACH_BLOCKS_WALKED: walking distance per continuous action. */
    static final double ACTIONS_EACH_BLOCKS_WALKED = 25;
    /** MC Entity.move: the walking distance grows by the horizontal distance times 0.6. */
    static final double WALK_DIST_FACTOR = 0.6;
    /**
     * A walk sample farther than this (blocks) is a teleport, not a walk (MC walkDist only grows by moving).
     * Deviation from MC: positions are sampled every 60 ticks, where MC counts each step.
     */
    static final double TELEPORT_BLOCKS = 32;

    private final double max;
    private double saturation;
    private boolean justAte;
    private final FoodHistory history = new FoodHistory();
    private double pendingDecrease;
    private double walked;
    private double walkedAtLastAction;
    private long interactionCooldownUntil;

    /** Starts full, at {@code max} (MC MAX_SATURATION). */
    public CitizenHunger(double max) {
        this.max = max;
        this.saturation = max;
    }

    public double saturation() {
        return saturation;
    }

    public void setSaturation(double saturation) {
        this.saturation = saturation;
    }

    /** MC CitizenData.increaseSaturation: adds {@code amount}'s size, up to the maximum. */
    public void increase(double amount) {
        saturation = Math.min(max, saturation + Math.abs(amount));
    }

    /**
     * MC CitizenData.decreaseSaturation: takes {@code amount}'s size times the config's {@code foodModifier}, down to
     * 0, and ends the "just ate" state. MC skips it for an inactive colony, whose ticks never run here.
     */
    public void decrease(double amount, double foodModifier) {
        saturation = Math.max(0, saturation - Math.abs(amount * foodModifier));
        justAte = false;
    }

    /** MC CitizenData.justAte: it ate its fill and won't eat again until its saturation drops. */
    public boolean justAte() {
        return justAte;
    }

    public void setJustAte(boolean justAte) {
        this.justAte = justAte;
    }

    /** Its last meals (MC CitizenFoodHandler). */
    public FoodHistory history() {
        return history;
    }

    /** The tick until which a player's food is refused (MC EntityCitizen.interactionCooldown); runtime only. */
    long interactionCooldownUntil() {
        return interactionCooldownUntil;
    }

    void setInteractionCooldownUntil(long tick) {
        this.interactionCooldownUntil = tick;
    }

    /** MC EntityCitizen.decreaseSaturationForAction: one work action, paid at the next idle decrease. */
    public void forAction() {
        pendingDecrease += BIG_SATURATION_FACTOR;
    }

    /** MC EntityCitizen.decreaseSaturationForContinuousAction: a little continuous work, paid likewise. */
    public void forContinuousAction() {
        pendingDecrease += SATURATION_DECREASE_FACTOR;
    }

    /** The saturation its work and walking will cost at the next idle decrease (MC cachedActionSaturationDecrease). */
    public double pending() {
        return pendingDecrease;
    }

    /** Pays the pending decrease (MC cachedActionSaturationDecrease): returns it and starts afresh. */
    double takePending() {
        double pending = pendingDecrease;
        pendingDecrease = 0;
        return pending;
    }

    /**
     * MC EntityCitizen.decreaseWalkingSaturation: the citizen moved from {@code from} to {@code to} since the last
     * sample; the horizontal distance counts as walking unless it is a teleport ({@link #TELEPORT_BLOCKS}).
     */
    public void walked(Vec3 from, Vec3 to) {
        double dx = to.x() - from.x();
        double dz = to.z() - from.z();
        double blocks = Math.sqrt(dx * dx + dz * dz);
        if (blocks <= TELEPORT_BLOCKS) {
            walked(blocks);
        }
    }

    /**
     * {@code blocks} walked horizontally add to its walking distance (× 0.6, MC walkDist); every
     * {@link #ACTIONS_EACH_BLOCKS_WALKED} of it costs a continuous action.
     */
    private void walked(double blocks) {
        walked += blocks * WALK_DIST_FACTOR;
        if (walked - walkedAtLastAction > ACTIONS_EACH_BLOCKS_WALKED) {
            walkedAtLastAction = walked;
            forContinuousAction();
        }
    }
}
