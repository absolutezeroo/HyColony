package dev.hydomum.plugin.api;

import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hydomum.plugin.cutter.CutterSystem;

/**
 * HyDomum's systems that another mod may order its own against (SystemDependency names a system by its class): a
 * colony's protection runs before the cutter opens. HyDomum registers them first in its setup, unconditionally.
 */
public final class HyDomumSystems {
    private HyDomumSystems() {}

    /** The system that opens the architect's cutter on use. */
    public static Class<? extends EntityEventSystem<EntityStore, UseBlockEvent.Pre>> cutterUse() {
        return CutterSystem.class;
    }
}
