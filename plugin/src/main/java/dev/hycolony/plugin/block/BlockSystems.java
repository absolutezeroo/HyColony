package dev.hycolony.plugin.block;

import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.plugin.IdMap;
import dev.hycolony.plugin.WorldRuntimes;

/**
 * Registers the block systems: huts, colony protection, the field block and, when an enabled pack maps flower pots, the
 * flower pot.
 */
public final class BlockSystems {
    private BlockSystems() {}

    /**
     * Registers the hut, protection and flower pot systems; the flower pot only when the merged id-map has pots. It runs
     * after the protection through its own dependency, which must already be registered.
     */
    public static void register(ComponentRegistryProxy<EntityStore> registry, WorldRuntimes worlds, IdMap ids) {
        registry.registerSystem(new HutBlockSystems.Place(worlds));
        registry.registerSystem(new HutBlockSystems.Break(worlds));
        registry.registerSystem(new HutBlockSystems.Use(worlds));
        registry.registerSystem(new ProtectionSystems.Place(worlds));
        registry.registerSystem(new ProtectionSystems.Break(worlds));
        registry.registerSystem(new BlockUseProtectionSystem(worlds, ids));
        registry.registerSystem(new ExplosionProtectionSystem(worlds));
        String field = ids.fieldBlockId();
        if (!field.isEmpty()) {
            registry.registerSystem(new FieldBlockSystems.Place(worlds, ids.itemId("block.field")));
            registry.registerSystem(new FieldBlockSystems.Break(worlds, field));
            registry.registerSystem(new FieldBlockSystems.Use(worlds, field));
        }
        if (!ids.flowerPots().isEmpty()) {
            registry.registerSystem(new FlowerPotSystem(ids));
        }
    }
}
