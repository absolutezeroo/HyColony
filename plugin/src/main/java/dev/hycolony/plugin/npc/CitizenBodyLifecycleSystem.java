package dev.hycolony.plugin.npc;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.plugin.WorldRuntime;
import dev.hycolony.plugin.WorldRuntimes;
import java.util.Optional;
import java.util.logging.Level;
import javax.annotation.Nonnull;

/**
 * Binds citizen NPCs loaded from chunks to the core, once the store is free; unbinds on unload. It runs inside Hytale's
 * chunk load and unload: a failure is logged SEVERE and never thrown into them (CLAUDE.md § 4).
 */
public final class CitizenBodyLifecycleSystem extends RefSystem<EntityStore> {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    private final WorldRuntimes runtimes;

    public CitizenBodyLifecycleSystem(WorldRuntimes runtimes) {
        this.runtimes = runtimes;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return HyColonyComponents.citizenTag();
    }

    @Override
    public void onEntityAdded(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull AddReason reason,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> buffer) {
        if (reason != AddReason.LOAD) {
            return; // freshly spawned bodies are bound by HytaleCitizenBodies.spawn
        }
        try {
            World world = store.getExternalData().getWorld();
            WorldRuntime rt = runtimes.of(world);
            CitizenTag tag = store.getComponent(ref, HyColonyComponents.citizenTag());
            if (rt != null && rt.enabled() && tag != null) {
                BodyId body = rt.bodies().track(ref);
                // The store is processing this load: binding wakes the citizen, which writes to the store, and a
                // write here throws (Store.assertWriteProcessing). The core binds it once the store is free, from the
                // world's task queue, before the next colony tick for a chunk load. A body re-added within a tick (an
                // NPC role reload, a dev case) may meet a colony tick first: its respawn check, every 5 minutes, then
                // gives the citizen a new body and this one is despawned as a duplicate.
                int colonyId = tag.colonyId();
                int citizenId = tag.citizenId();
                world.execute(() -> bind(world, rt, body, colonyId, citizenId));
            }
        } catch (RuntimeException e) {
            LOG.at(Level.SEVERE).withCause(e).log("HyColony: binding a loaded citizen body failed");
        }
    }

    /**
     * Binds {@code body} to its citizen, unless it was unloaded or untracked meanwhile, or HyColony was disabled or
     * removed from {@code world}.
     */
    private void bind(World world, WorldRuntime rt, BodyId body, int colonyId, int citizenId) {
        try {
            if (rt.equals(runtimes.of(world))
                    && rt.enabled()
                    && rt.bodies().entity(body).isPresent()) {
                rt.manager().onBodyLoaded(body, colonyId, citizenId);
            }
        } catch (RuntimeException e) {
            LOG.at(Level.SEVERE).withCause(e).log("HyColony: binding a loaded citizen body failed");
        }
    }

    @Override
    public void onEntityRemove(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull RemoveReason reason,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> buffer) {
        try {
            WorldRuntime rt = runtimes.of(store.getExternalData().getWorld());
            if (rt == null) {
                return;
            }
            Optional<BodyId> body = rt.bodies().untrack(ref); // always, or its entry would stay behind
            CitizenTag tag = store.getComponent(ref, HyColonyComponents.citizenTag());
            if (tag != null) {
                body.ifPresent(id -> rt.manager().onBodyUnloaded(id, tag.colonyId()));
            }
        } catch (RuntimeException e) {
            LOG.at(Level.SEVERE).withCause(e).log("HyColony: unbinding a citizen body failed");
        }
    }
}
