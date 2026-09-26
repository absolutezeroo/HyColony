package dev.hycolony.plugin.npc;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.plugin.WorldRuntime;
import dev.hycolony.plugin.WorldRuntimes;
import javax.annotation.Nonnull;

/** Binds citizen NPCs loaded from chunks to the core; unbinds on unload. */
public final class CitizenBodyLifecycleSystem extends RefSystem<EntityStore> {
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
        WorldRuntime rt = runtimes.of(store.getExternalData().getWorld());
        if (rt == null || !rt.enabled()) {
            return;
        }
        CitizenTag tag = store.getComponent(ref, HyColonyComponents.citizenTag());
        rt.manager().onBodyLoaded(rt.bodies().track(ref), tag.colonyId(), tag.citizenId());
    }

    @Override
    public void onEntityRemove(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull RemoveReason reason,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> buffer) {
        WorldRuntime rt = runtimes.of(store.getExternalData().getWorld());
        if (rt == null) {
            return;
        }
        CitizenTag tag = store.getComponent(ref, HyColonyComponents.citizenTag());
        rt.bodies().untrack(ref).ifPresent(id -> rt.manager().onBodyUnloaded(id, tag.colonyId()));
    }
}
