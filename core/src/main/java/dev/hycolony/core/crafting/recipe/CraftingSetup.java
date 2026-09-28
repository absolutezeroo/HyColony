package dev.hycolony.core.crafting.recipe;

import java.util.Objects;

/** What the crafting system is given at start-up: the Hytale recipe catalog and the {@code crafting.json} rules. */
public record CraftingSetup(RecipeCatalog catalog, CraftingRules rules) {
    public CraftingSetup {
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(rules, "rules");
    }
}
