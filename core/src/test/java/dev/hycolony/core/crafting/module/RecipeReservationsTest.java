package dev.hycolony.core.crafting.module;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.BuildingType;
import dev.hycolony.core.crafting.recipe.Ingredient;
import dev.hycolony.core.crafting.recipe.RecipeFixtures;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.logistics.pickup.HutKeep;
import dev.hycolony.core.request.model.StackRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RecipeReservationsTest {
    private static final ItemKey SEEDS = new ItemKey("Plant_Seeds_Wheat");
    private static final ItemKey ESSENCE = RecipeFixtures.ESSENCE;

    private final CraftingHut h = new CraftingHut();
    private final Building other =
            Building.create(new BuildingType("test:other", "hut.other", 1, List.of()), new BlockPos(13, 64, 4), 0);

    RecipeReservationsTest() {
        h.colony.buildings().add(other);
        h.bench("Farmingbench", 1);
        h.teach(RecipeFixtures.at("Farmingbench", "Seeds", SEEDS.id()));
        h.hire();
    }

    private void stock(ItemKey item, int count) {
        h.t.containers
                .containers
                .computeIfAbsent(h.hut.position(), _ -> new LinkedHashMap<>())
                .merge(item, count, Integer::sum);
    }

    /** Another hut asks for seeds: the hut's crafter gets a task of {@code runs} runs. */
    private void ask(int runs) {
        h.colony.requests().createAndAssign(other, new StackRequest(SEEDS, runs, runs, true), -1);
    }

    private Map<Ingredient, Integer> reserved() {
        return RecipeReservations.reserved(h.colony, h.hut, h.module);
    }

    @Test
    void reservationsAddUpTheRunsOfEveryQueuedAndScheduledTask() {
        stock(ESSENCE, 20);

        ask(10);
        ask(5);

        assertEquals(Map.of(new Ingredient.OfItem(ESSENCE, 1), 30), reserved());
    }

    @Test
    void taskOfARecipeTheModuleNoLongerUsesReservesNothing() {
        ask(10);

        h.module.toggle(h.colony, 0);

        assertEquals(Map.of(), reserved());
    }

    @Test
    void ingredientsOfQueuedTasksAreKeptFromCouriers() {
        stock(ESSENCE, 20);
        ask(10);

        HutKeep keep = HutKeep.of(h.colony, h.hut, false);

        assertEquals(0, keep.removable(new ItemAmount(ESSENCE, 20)));
        assertEquals(5, keep.removable(new ItemAmount(ESSENCE, 5)), "only what the tasks need");
        assertEquals(0, keep.removable(new ItemAmount(SEEDS, 10)), "MC: the output too");
        assertEquals(3, keep.removable(new ItemAmount(new ItemKey("Rock_Stone"), 3)));
    }

    @Test
    void aWorkerDumpingItsInventoryKeepsNothingForTheTasks() {
        stock(ESSENCE, 20);
        ask(10);

        assertEquals(20, HutKeep.of(h.colony, h.hut, true).removable(new ItemAmount(ESSENCE, 20)));
    }
}
