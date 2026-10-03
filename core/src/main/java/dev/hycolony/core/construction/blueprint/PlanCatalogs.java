package dev.hycolony.core.construction.blueprint;

import dev.hycolony.core.crafting.recipe.RecipeCatalog;
import dev.hycolony.core.kernel.catalog.BlockCatalog;
import dev.hycolony.core.kernel.catalog.ItemCatalog;

/** What judging and costing a plan's cells reads: items, block types, Structurize's placement rules and recipes. */
public record PlanCatalogs(ItemCatalog items, BlockCatalog blocks, PlacementRules placement, RecipeCatalog recipes) {}
