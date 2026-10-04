package dev.hyangler.plugin.spike;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Throwaway (plan task 2): marks the spike's bobber; freeze pins it in water (else Hytale's physics alone moves it),
 * floating once pinned, at the height it entered the water.
 */
final class SpikeBobber implements Component<EntityStore> {
    boolean freeze;
    boolean floating;
    double surfaceY;
    int ticks;

    @Override
    public Component<EntityStore> clone() {
        SpikeBobber copy = new SpikeBobber();
        copy.freeze = freeze;
        copy.floating = floating;
        copy.surfaceY = surfaceY;
        copy.ticks = ticks;
        return copy;
    }
}
