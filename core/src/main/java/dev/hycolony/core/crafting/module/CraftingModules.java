package dev.hycolony.core.crafting.module;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.module.BuildingModule;
import dev.hycolony.core.crafting.recipe.RecipeId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** A hut's crafting modules (MC AbstractBuilding.getModulesByType(ICraftingBuildingModule.class)). */
public final class CraftingModules {
    private CraftingModules() {}

    /** The hut's crafting modules, in module order. */
    public static List<CraftingModule> of(Building hut) {
        List<CraftingModule> out = new ArrayList<>();
        for (BuildingModule module : hut.modules().values()) {
            if (module instanceof CraftingModule crafting) {
                out.add(crafting);
            }
        }
        return out;
    }

    /** MC AbstractBuilding.getCraftingModuleForRecipe: the first crafting module of the hut holding the recipe. */
    public static Optional<CraftingModule> holding(Building hut, RecipeId id) {
        for (CraftingModule module : of(hut)) {
            if (module.holdsRecipe(id)) {
                return Optional.of(module);
            }
        }
        return Optional.empty();
    }
}
