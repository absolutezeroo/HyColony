package dev.hyvanilla.plugin.api;

import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hyvanilla.plugin.block.FlowerPotSystem;

/**
 * HyVanilla's systems that another mod may order its own against (SystemDependency names a system by its class): a
 * colony's protection runs before a flower pot is used. HyVanilla registers them in its setup, unconditionally.
 */
public final class HyVanillaSystems {
    private HyVanillaSystems() {}

    /** The system that plants in, or takes back from, a flower pot on use. */
    public static Class<? extends EntityEventSystem<EntityStore, UseBlockEvent.Pre>> flowerPotUse() {
        return FlowerPotSystem.class;
    }
}
