package dev.hycolony.plugin.block;

import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.plugin.IdMap;
import dev.hycolony.plugin.WorldRuntimes;

/** Registers the block systems: huts, colony protection and, when its pack is enabled, the flower pot. */
public final class BlockSystems {
    /** The sub-plugin that brings the flower pots; their system is registered only when it is enabled. */
    public static final String FLOWER_POT_PACK = "Decorations";

    private BlockSystems() {}

    /**
     * Registers the hut, protection and flower pot systems. The flower pot runs after the protection through its own
     * dependency, which must already be registered.
     */
    public static void register(
            ComponentRegistryProxy<EntityStore> registry, WorldRuntimes worlds, IdMap ids, boolean flowerPots) {
        registry.registerSystem(new HutBlockSystems.Place(worlds));
        registry.registerSystem(new HutBlockSystems.Break(worlds));
        registry.registerSystem(new HutBlockSystems.Use(worlds));
        registry.registerSystem(new ProtectionSystems.Place(worlds));
        registry.registerSystem(new ProtectionSystems.Break(worlds));
        registry.registerSystem(new BlockUseProtectionSystem(worlds, ids));
        registry.registerSystem(new ExplosionProtectionSystem(worlds));
        if (flowerPots) {
            registry.registerSystem(new FlowerPotSystem(ids));
        }
    }
}
