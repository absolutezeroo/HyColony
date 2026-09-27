package dev.hycolony.plugin.block;

import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.plugin.IdMap;
import dev.hycolony.plugin.WorldRuntimes;

/** Registers the block systems: huts, colony protection and, when its pack is enabled, the flower pot. */
public final class BlockSystems {
    private BlockSystems() {}

    /**
     * Hut, then protection, then flower pot systems: they handle the same block events in that order, so a use the
     * colony refuses is already cancelled when the flower pot sees it.
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
