package dev.hycolony.core.colony;

import dev.hycolony.core.construction.blueprint.BlueprintSource;
import dev.hycolony.core.crafting.recipe.CraftingSetup;
import dev.hycolony.core.farming.FarmingAccess;
import dev.hycolony.core.kernel.port.ContainerAccess;
import dev.hycolony.core.kernel.port.ItemCatalog;
import dev.hycolony.core.kernel.port.PlayerInventory;
import dev.hycolony.core.kernel.port.WorldBlocks;
import dev.hycolony.core.kernel.port.WorldEffects;

/**
 * The game content and world the features work on, one adapter each: items, blocks, containers, the player's
 * inventory, blueprints, block effects, the crafting setup (recipe catalog and {@code crafting.json} rules) and the
 * crops and soil of farming. The colony's own ports (bodies, clock, notices, players) sit in {@link ColonyContext}.
 */
public record GamePorts(
        ItemCatalog catalog,
        WorldBlocks blocks,
        ContainerAccess containers,
        PlayerInventory playerInventory,
        BlueprintSource blueprints,
        WorldEffects effects,
        CraftingSetup crafting,
        FarmingAccess farming) {}
