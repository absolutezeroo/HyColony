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
 * Ports the construction system needs, the crafting setup (recipe catalog and {@code crafting.json} rules) and the
 * farming port, kept here because {@code colony} and {@code ColonyContext} have no room left.
 */
public record ConstructionPorts(
        ItemCatalog catalog,
        WorldBlocks blocks,
        ContainerAccess containers,
        PlayerInventory playerInventory,
        BlueprintSource blueprints,
        WorldEffects effects,
        CraftingSetup crafting,
        FarmingAccess farming) {}
