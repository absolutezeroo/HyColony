package dev.hycolony.core.farming.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import dev.hycolony.core.crafting.module.CraftingModule;
import dev.hycolony.core.crafting.recipe.BenchRequirement;
import dev.hycolony.core.crafting.recipe.CraftingRules;
import dev.hycolony.core.crafting.recipe.Ingredient;
import dev.hycolony.core.crafting.recipe.Recipe;
import dev.hycolony.core.crafting.recipe.RecipeId;
import dev.hycolony.core.crafting.recipe.RecipeSource;
import dev.hycolony.core.farming.CropState;
import dev.hycolony.core.job.JobAI;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.Workstation;
import dev.hycolony.core.testing.TestContexts;
import dev.hycolony.core.testing.farming.FakeFarming;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;

/**
 * The SP3b-2 spec's scenario: a wheat field that feeds its own seeds. The farmer tills and plants, harvests the ripe
 * wheat, runs out of seeds, and its own hut crafts new ones from the essence it harvested, which it plants again.
 */
class FarmerScenarioTest extends FarmerTestBase {
    private static final String RULES = """
            {"jobs":{"hycolony:farmer":{"allow":[{"bench":"Farmingbench","categories":["Seeds"]}]}}}
            """;

    /** 2 essence → 1 wheat seed at the Farmingbench, as Hytale's Plant_Seeds_Wheat recipe. */
    private static final Recipe SEEDS_RECIPE = new Recipe(
            List.of(new Ingredient.OfItem(FakeFarming.ESSENCE, 2)),
            new ItemAmount(SEEDS, 1),
            List.of(),
            new BenchRequirement("Farmingbench", List.of("Seeds"), 0),
            Optional.empty(),
            new RecipeSource.Hytale("Plant_Seeds_Wheat_Recipe_Generated_0"),
            false);

    @Override
    TestContexts contexts() {
        TestContexts t = new TestContexts();
        t.craftingRules = CraftingRules.parse(JsonParser.parseString(RULES).getAsJsonObject(), w -> {});
        t.recipes.add(SEEDS_RECIPE);
        t.players.online.put(OWNER, HUT); // keeps the colony active: its requests tick
        return t;
    }

    @Test
    void wheatFieldFeedsItsOwnSeeds() {
        field(true);
        give(HOE, 1);
        putInHut(SEEDS, 8);
        settings().setFertilize(false);
        hut.registeredBlocks().addWorkstation(HUT.offset(1, 0, 0), new Workstation("Farmingbench", 1));
        RecipeId seeds = colony.registries().recipes().checkOrAdd(SEEDS_RECIPE);
        assertTrue(hut.module(CraftingModule.class).orElseThrow().learn(colony, hut, seeds, OWNER));
        JobAI ai = job.createAI(colony, body);

        run(ai, () -> planted() == 8);
        crops().forEach(c -> t.farming.cropState.put(c, CropState.MATURE));
        run(ai, () -> planted() == 0);
        run(ai, () -> planted() == 8);

        assertFalse(colony.isSuspended(), "the colony threw");
        assertTrue(t.containers.count(hut.containers(), FakeFarming.WHEAT) > 0, "the harvest is in the hut");
        assertEquals(
                0, everywhere(FakeFarming.ESSENCE), "the 24 essence harvested made 12 seeds (MC crafts what it can)");
        assertEquals(12 - 8, everywhere(SEEDS), "8 of them planted");
    }

    /** {@code item} in the hut and the farmer's inventory. */
    private int everywhere(dev.hycolony.core.kernel.item.ItemKey item) {
        return t.containers.count(hut.containers(), item) + carried(item);
    }

    private int planted() {
        return (int) crops().stream().filter(t.farming.crops::containsKey).count();
    }

    private static List<BlockPos> crops() {
        return cells().stream().map(c -> c.offset(0, 1, 0)).toList();
    }

    /** Runs the colony and the farmer, a colony day every 2000 ticks, until {@code done}. */
    private void run(JobAI ai, BooleanSupplier done) {
        for (int i = 0; i < 60_000 && !done.getAsBoolean(); i++) {
            t.clock.tick++;
            colony.tick();
            ai.tick();
            if (i % 2_000 == 1_999) {
                colony.setDay(colony.day() + 1);
            }
        }
        assertTrue(
                done.getAsBoolean(),
                () -> "never happened; farmer in " + ai.stateName() + ", requests "
                        + colony.requests().all()
                        + ", planted " + planted() + ", stage "
                        + colony.registries().fields().get(FIELD).orElseThrow().stage() + ", actions "
                        + job.actionsDone() + ", carried " + citizen.inventory().contents() + ", hut "
                        + t.containers.containers.get(HUT) + ", tilled " + t.farming.tilled.size() + ", day "
                        + colony.day());
    }
}
