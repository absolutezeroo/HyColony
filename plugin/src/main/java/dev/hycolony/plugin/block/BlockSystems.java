package dev.hycolony.plugin.block;

import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.plugin.IdMap;
import dev.hycolony.plugin.WorldRuntimes;

/** Registers the block systems: huts, colony protection and the field block. */
public final class BlockSystems {
    private BlockSystems() {}

    /** Registers the hut, protection and field block systems; the field block only when the id-map has one. */
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
    }
}
