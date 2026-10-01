package dev.hycolony.plugin;

import com.hypixel.hytale.server.core.universe.world.World;
import dev.hycolony.core.colony.GamePorts;
import dev.hycolony.core.crafting.recipe.CraftingSetup;
import dev.hycolony.plugin.adapter.HytaleContainerAccess;
import dev.hycolony.plugin.adapter.HytaleItemCatalog;
import dev.hycolony.plugin.adapter.HytalePlayerInventory;
import dev.hycolony.plugin.adapter.HytaleWorldBlocks;
import dev.hycolony.plugin.adapter.HytaleWorldEffects;
import dev.hycolony.plugin.crafting.HytaleRecipeCatalog;
import dev.hycolony.plugin.farming.HytaleFarming;
import dev.hycolony.plugin.prefab.HytaleBlueprintSource;

/** The construction adapters of one world, for its {@link WorldRuntime}. */
final class WorldPorts {
    private WorldPorts() {}

    /** The construction adapters of {@code world}. */
    static GamePorts create(World world, RuntimeSetup setup, HytaleItemCatalog catalog, HytaleWorldBlocks worldBlocks) {
        IdMap ids = setup.ids();
        return new GamePorts(
                catalog,
                worldBlocks,
                new HytaleContainerAccess(world, catalog.stacks()),
                new HytalePlayerInventory(world, catalog.stacks()),
                new HytaleBlueprintSource(ids, setup.styles(), catalog),
                new HytaleWorldEffects(world, ids.fireworks(), ids.farming().tillSoundEvent(), ids.sleepParticle()),
                // Read here, before openStorage loads the colonies: a load drops every learnt recipe it does not know.
                new CraftingSetup(HytaleRecipeCatalog.load(), setup.craftingRules()),
                new HytaleFarming(world, worldBlocks, ids.farming(), ids.fieldBlockId()));
    }
}
