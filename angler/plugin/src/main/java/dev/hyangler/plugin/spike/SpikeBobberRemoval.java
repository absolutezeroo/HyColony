package dev.hyangler.plugin.spike;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.logging.Level;
import javax.annotation.Nonnull;

/**
 * Throwaway (fishing-hytale.md § 7.5): whenever a spike bobber goes, however it goes (its catch ended, its minute
 * up, Hytale's despawn of a bobber that never reached water, its chunk unloaded, its thrower gone), cuts its line and
 * gives its angler's camera back: the one place every exit passes.
 */
public final class SpikeBobberRemoval extends RefSystem<EntityStore> {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    private boolean failed;

    @Override
    public Query<EntityStore> getQuery() {
        return SpikeBobberSystem.bobberType();
    }

    @Override
    public void onEntityAdded(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull AddReason reason,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> buffer) {}

    /** Cuts the removed bobber's line and releases its angler's camera; never throws. */
    @Override
    public void onEntityRemove(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull RemoveReason reason,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> buffer) {
        try {
            SpikeBobber bobber = buffer.getComponent(ref, SpikeBobberSystem.bobberType());
            if (bobber == null) {
                return;
            }
            SpikeLine.cut(bobber.carriers, buffer);
            Ref<EntityStore> angler = bobber.angler;
            if (angler != null) {
                SpikeCamera.release(angler, buffer);
            }
        } catch (RuntimeException e) { // out of a RefSystem, an exception would stop the world's thread
            LOG.at(failed ? Level.FINE : Level.SEVERE).withCause(e).log("HyAngler spike: bobber removal failed");
            failed = true;
        }
    }
}
