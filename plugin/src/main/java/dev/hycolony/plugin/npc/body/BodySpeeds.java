package dev.hycolony.plugin.npc.body;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Each citizen body's walking speed: its job's factor (MC MOVEMENT_SPEED over its base), times 0.85 while it starves
 * (MC MOVEMENT_SLOWDOWN 0, which multiplies the total), applied through {@link CitizenSpeed}'s nearest effect. World
 * thread only.
 */
public final class BodySpeeds {
    /** MC MobEffects.MOVEMENT_SLOWDOWN at amplifier 0: -15 % of the total speed. */
    static final double STARVING_FACTOR = 0.85;

    private final CitizenSpeed speed;
    /** Weak, so a removed entity drops out; a body without an entry walks at its job's factor 1. */
    private final Map<Ref<EntityStore>, Double> jobFactors = new WeakHashMap<>();

    private final Map<Ref<EntityStore>, Boolean> starving = new WeakHashMap<>();

    public BodySpeeds(CitizenSpeed speed) {
        this.speed = speed;
    }

    /** The job's factor for {@code ref} (1 = normal), kept with its starving slowdown. */
    public void setJobFactor(Ref<EntityStore> ref, double factor, Store<EntityStore> store) {
        jobFactors.put(ref, factor);
        apply(ref, store);
    }

    /** Starts or ends the starving slowdown of {@code ref}; nothing to do when it does not change. */
    public void setStarving(Ref<EntityStore> ref, boolean isStarving, Store<EntityStore> store) {
        Boolean before = starving.put(ref, isStarving);
        if (before == null || before != isStarving) {
            apply(ref, store);
        }
    }

    private void apply(Ref<EntityStore> ref, Store<EntityStore> store) {
        double factor = jobFactors.getOrDefault(ref, 1.0);
        if (starving.getOrDefault(ref, false)) {
            factor *= STARVING_FACTOR;
        }
        speed.apply(ref, factor, store);
    }
}
