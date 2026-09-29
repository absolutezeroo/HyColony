package dev.hycolony.plugin.npc;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.plugin.WorldRuntime;
import dev.hycolony.plugin.WorldRuntimes;
import java.util.Optional;
import java.util.logging.Level;
import javax.annotation.Nonnull;

/**
 * Binds citizen NPCs loaded from chunks to the core; unbinds on unload. It runs inside Hytale's chunk load and unload:
 * a failure is logged SEVERE and never thrown into them (CLAUDE.md § 4).
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
            WorldRuntime rt = runtimes.of(store.getExternalData().getWorld());
            CitizenTag tag = store.getComponent(ref, HyColonyComponents.citizenTag());
            if (rt != null && rt.enabled() && tag != null) {
                rt.manager().onBodyLoaded(rt.bodies().track(ref), tag.colonyId(), tag.citizenId());
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
