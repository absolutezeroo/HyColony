package dev.hycolony.core.citizen.food;

import dev.hycolony.core.citizen.CitizenData;
import java.util.function.DoubleSupplier;
import java.util.random.RandomGenerator;

/** Whether a citizen should go and eat. Port of MC CitizenAI.shouldEat (no disease: never sick). */
public final class EatDecision {
    /** MC CitizenConstants.AVERAGE_SATURATION: at or below it a citizen may eat. */
    public static final double AVERAGE_SATURATION = 10;
    /** MC EntityAIEatTask.RESTAURANT_LIMIT: at or below it a citizen goes and eats. */
    public static final double RESTAURANT_LIMIT = 2.5;
    /** MC CitizenDiseaseHandler.SEEK_DOCTOR_HEALTH: below it a hungry citizen eats sooner. */
    static final double SEEK_DOCTOR_HEALTH = 6;
    /** MC: a waiter goes and eats only once in 200 decisions (nextInt(200) > 0 skips). */
    private static final int WAITER_SKIP_BOUND = 200;

    /** What the decision reads: the AI's state and its job's say, the body's health (read last, only if needed). */
    public record Situation(boolean eating, boolean interruptible, boolean waiter, DoubleSupplier health) {}

    private EatDecision() {}

    /**
     * MC shouldEat: not when it just ate or is full, nor when its job cannot be interrupted; yes while eating; a waiter
     * rarely; else at {@link #RESTAURANT_LIMIT}, or below {@link HungerTicks#LOW_SATURATION} when hurt.
     */
    public static boolean shouldEat(CitizenData citizen, Situation s, RandomGenerator random) {
        CitizenHunger hunger = citizen.hunger();
        if (hunger.justAte() || hunger.saturation() >= CitizenData.MAX_SATURATION) {
            return false;
        }
        if (!s.interruptible()) {
            return false;
        }
        if (s.eating()) {
            return true;
        }
        if (s.waiter() && random.nextInt(WAITER_SKIP_BOUND) > 0) {
            return false;
        }
        double saturation = hunger.saturation();
        return saturation <= AVERAGE_SATURATION
                && (saturation <= RESTAURANT_LIMIT
                        || (saturation < HungerTicks.LOW_SATURATION
                                && s.health().getAsDouble() < SEEK_DOCTOR_HEALTH));
    }
}
