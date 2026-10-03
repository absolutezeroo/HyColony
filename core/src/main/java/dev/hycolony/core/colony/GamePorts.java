package dev.hycolony.core.colony;

import dev.hycolony.core.citizen.inventory.ArmorCatalog;
import dev.hycolony.core.construction.blueprint.BlueprintSource;
import dev.hycolony.core.construction.blueprint.PlacementRules;
import dev.hycolony.core.construction.blueprint.PlanCatalogs;
import dev.hycolony.core.construction.tape.TapeBlocks;
import dev.hycolony.core.crafting.furnace.CookingSetup;
import dev.hycolony.core.crafting.recipe.CraftingSetup;
import dev.hycolony.core.farming.FarmingAccess;
import dev.hycolony.core.kernel.catalog.BlockCatalog;
import dev.hycolony.core.kernel.catalog.FoodCatalog;
import dev.hycolony.core.kernel.catalog.ItemCatalog;
import dev.hycolony.core.kernel.port.ContainerAccess;
import dev.hycolony.core.kernel.port.PlayerInventory;
import dev.hycolony.core.kernel.port.WorldBlocks;
import dev.hycolony.core.kernel.port.WorldEffects;

/**
 * The game content and world the features work on, one adapter each: items, block types, foods, Structurize's
 * placement rules, blocks, containers, the player's inventory, blueprints, block effects, the crafting setup (recipe
 * catalog and {@code crafting.json} rules), the crops and soil of farming, the cooking stations, the construction
 * tape's blocks and the armour pieces. The colony's own ports (bodies, clock, notices, players) sit in
 * {@link ColonyContext}.
 */
public record GamePorts(
        ItemCatalog catalog,
        BlockCatalog blockCatalog,
        FoodCatalog foods,
        PlacementRules placement,
        WorldBlocks blocks,
        ContainerAccess containers,
        PlayerInventory playerInventory,
        BlueprintSource blueprints,
        WorldEffects effects,
        CraftingSetup crafting,
        FarmingAccess farming,
        CookingSetup cooking,
        TapeBlocks tape,
        ArmorCatalog armors) {

    /** The catalogs a plan's matching and costing read: items, block types, placement rules and recipes. */
    public PlanCatalogs planCatalogs() {
        return new PlanCatalogs(catalog, blockCatalog, placement, crafting.catalog());
    }
}
