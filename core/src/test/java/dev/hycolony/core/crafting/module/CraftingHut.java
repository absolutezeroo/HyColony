package dev.hycolony.core.crafting.module;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.BuildingType;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.permission.Permissions;
import dev.hycolony.core.colony.territory.TerritoryIndex;
import dev.hycolony.core.crafting.recipe.CraftingRules;
import dev.hycolony.core.crafting.recipe.Recipe;
import dev.hycolony.core.crafting.recipe.RecipeId;
import dev.hycolony.core.job.WorkerModule;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.Workstation;
import dev.hycolony.core.testing.TestContexts;
import dev.hycolony.core.testing.TestCrafters;
import java.util.UUID;

/**
 * A level-1 test crafter hut ({@link TestCrafters}), in a colony whose {@code crafting.json} lets its job learn every
 * Farmingbench and Fieldcraft recipe.
 */
public final class CraftingHut {
    public static final String JOB = TestCrafters.JOB.id();
    public static final String RULES = """
            {"jobs": {"%s": {"allow": [
                {"bench": "Farmingbench", "categories": ["*"]},
                {"bench": "Fieldcraft", "categories": ["*"]}]}}}""".formatted(JOB);

    public final TestContexts t = new TestContexts();
    public final UUID owner = UUID.randomUUID();
    public final Colony colony;
    public final Building hut;
    public final CraftingModule module;
    private int benches;

    public CraftingHut() {
        this(RULES, true);
    }

    /** With {@code rules} as {@code crafting.json}; {@code many}: MC canLearnManyRecipes. */
    public CraftingHut(String rules, boolean many) {
        this(rules, TestCrafters.hut(many, 1));
    }

    /** With {@code rules} as {@code crafting.json}, a hut of {@code type} (a {@link TestCrafters#hut} variant). */
    public CraftingHut(String rules, BuildingType type) {
        t.craftingRules = CraftingRules.parse(JsonParser.parseString(rules).getAsJsonObject(), w -> {});
        colony = new Colony(
                t.context(),
                new TerritoryIndex(),
                new Colony.Founding(1, "T", new BlockPos(0, 64, 0), Permissions.createDefault(owner, "Owner")));
        hut = Building.create(type, new BlockPos(10, 64, 0), 0);
        hut.setLevel(1);
        hut.setBuilt(true);
        colony.buildings().add(hut);
        module = hut.module(CraftingModule.class).orElseThrow();
    }

    /** Registers the bench as placed by the builder from the hut's plan. */
    public BlockPos bench(String benchId, int tier) {
        BlockPos pos = new BlockPos(11, 64, benches++);
        hut.registeredBlocks().addWorkstation(pos, new Workstation(benchId, tier));
        return pos;
    }

    /** The recipe's id in the colony registry, as the recipes tab finds it. */
    public RecipeId register(Recipe recipe) {
        return colony.recipes().checkOrAdd(recipe);
    }

    /** Registers the recipe and has the colony owner teach it to the hut; fails the test if refused. */
    public RecipeId teach(Recipe recipe) {
        RecipeId id = register(recipe);
        assertTrue(module.learn(colony, hut, id, owner), "refused: " + module.canLearn(colony, hut, id, owner));
        return id;
    }

    /** Hires a new citizen at the hut. */
    public CitizenData hire() {
        CitizenData citizen = new CitizenData(colony.citizens().all().size() + 1);
        colony.citizens().restore(citizen);
        assertTrue(hut.module(WorkerModule.class).orElseThrow().hire(colony, hut, citizen));
        return citizen;
    }
}
