package dev.hycolony.plugin;

import com.hypixel.hytale.server.core.universe.world.World;
import dev.hycolony.core.colony.GamePorts;
import dev.hycolony.core.crafting.furnace.CookingSetup;
import dev.hycolony.core.crafting.recipe.CraftingSetup;
import dev.hycolony.plugin.adapter.HytaleBlocks;
import dev.hycolony.plugin.adapter.HytaleContainerAccess;
import dev.hycolony.plugin.adapter.HytaleItemCatalog;
import dev.hycolony.plugin.adapter.HytalePlayerInventory;
import dev.hycolony.plugin.adapter.HytaleWorldBlocks;
import dev.hycolony.plugin.adapter.HytaleWorldEffects;
import dev.hycolony.plugin.block.HutBlockSystems;
import dev.hycolony.plugin.block.HytaleTapeBlocks;
import dev.hycolony.plugin.crafting.HytaleJobTags;
import dev.hycolony.plugin.crafting.HytaleRecipeCatalog;
import dev.hycolony.plugin.farming.HytaleFarming;
import dev.hycolony.plugin.food.HytaleCookingCatalog;
import dev.hycolony.plugin.food.HytaleCookingStations;
import dev.hycolony.plugin.food.HytaleFoods;
import dev.hycolony.plugin.item.HytaleArmorCatalog;
import dev.hycolony.plugin.item.HytaleBlockCatalog;
import dev.hycolony.plugin.item.HytaleBlockTraits;
import dev.hycolony.plugin.prefab.HytaleBlueprintSource;
import dev.hycolony.plugin.prefab.PackedBlueprints;
import java.util.Set;

/** The construction adapters of one world, for its {@link WorldRuntime}. */
final class WorldPorts {
    private WorldPorts() {}

    /** The construction adapters of {@code world}; the hut blocks are never broken nor built over. */
    static GamePorts create(World world, RuntimeSetup setup, HytaleItemCatalog catalog, HytaleBlocks blocks) {
        IdMap ids = setup.ids();
        Set<String> hutBlockIds = HutBlockSystems.byBlockId(setup).keySet();
        HytaleWorldBlocks worldBlocks = new HytaleWorldBlocks(world, hutBlockIds, blocks);
        HytaleBlockCatalog blockCatalog = new HytaleBlockCatalog(hutBlockIds);
        HytaleFoods foods = new HytaleFoods(ids.food());
        HytaleBlockTraits placement = new HytaleBlockTraits(ids.construction());
        return new GamePorts(
                catalog,
                blockCatalog,
                foods,
                placement,
                worldBlocks,
                new HytaleContainerAccess(world, catalog.stacks()),
                new HytalePlayerInventory(world, catalog.stacks()),
                new PackedBlueprints(
                        new HytaleBlueprintSource(ids, setup.styles(), blockCatalog, placement), setup.packs()),
                new HytaleWorldEffects(
                        world,
                        ids.fireworks(),
                        ids.farming().tillSoundEvent(),
                        ids.sleepParticle(),
                        ids.food().particle()),
                // Read here, before openStorage loads the colonies: a load drops every learnt recipe it does not know.
                // The job tags are assets, loaded only now (crafting.json was read at setup).
                new CraftingSetup(
                        HytaleRecipeCatalog.load(), setup.craftingRules().withTags(HytaleJobTags.load())),
                new HytaleFarming(world, worldBlocks, ids.farming(), ids.fieldBlockId()),
                new CookingSetup(
                        new HytaleCookingCatalog(ids.food(), foods),
                        new HytaleCookingStations(world, catalog.stacks())),
                new HytaleTapeBlocks(ids),
                new HytaleArmorCatalog());
    }
}
