package dev.hycolony.core.crafting.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.crafting.module.CraftingHut;
import dev.hycolony.core.crafting.recipe.BenchRequirement;
import dev.hycolony.core.crafting.recipe.Ingredient;
import dev.hycolony.core.crafting.recipe.Recipe;
import dev.hycolony.core.crafting.recipe.RecipeFixtures;
import dev.hycolony.core.crafting.recipe.RecipeSource;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RecipeExecutionTest {
    private static final ItemKey SEEDS = new ItemKey("Plant_Seeds_Wheat");
    private static final ItemKey ESSENCE = RecipeFixtures.ESSENCE;
    private static final ItemKey BUCKET = new ItemKey("Container_Bucket");
    private static final ItemKey STONE = new ItemKey("Rock_Stone");
    /** 2 essence make 1 seed. */
    private static final Recipe SEEDS_BY_HAND = RecipeFixtures.fieldcraft("Seeds", SEEDS.id());

    private final CraftingHut h = new CraftingHut();

    private void stock(ItemKey item, int count) {
        h.t.containers
                .containers
                .computeIfAbsent(h.hut.position(), _ -> new LinkedHashMap<>())
                .merge(item, count, Integer::sum);
    }

    private int inHut(ItemKey item) {
        return h.t.containers.count(h.hut.containers(), item);
    }

    private boolean craftInHut(Recipe recipe) {
        return RecipeExecution.craftInHut(h.colony, h.hut, recipe);
    }

    /** 2 essence make 1 seed and give a bucket back. */
    private static Recipe seedsGivingABucket() {
        return new Recipe(
                List.of(new Ingredient.OfItem(ESSENCE, 2)),
                new ItemAmount(SEEDS, 1),
                List.of(new ItemAmount(BUCKET, 1)),
                new BenchRequirement(BenchRequirement.FIELDCRAFT, List.of("Basic"), 0),
                Optional.empty(),
                new RecipeSource.Hytale("Seeds_And_Bucket"),
                false);
    }

    @Test
    void craftInHutTakesFromTheRacksThenTheWorkersInventory() {
        CitizenData worker = h.hire();
        stock(ESSENCE, 1);
        worker.inventory().insert(new ItemAmount(ESSENCE, 3), _ -> 64);

        assertTrue(craftInHut(SEEDS_BY_HAND));

        assertEquals(0, inHut(ESSENCE));
        assertEquals(2, worker.inventory().count(ESSENCE));
        assertEquals(1, inHut(SEEDS), "the output goes to the racks first");
    }

    @Test
    void craftInHutChangesNothingWhenAnIngredientIsMissing() {
        CitizenData worker = h.hire();
        stock(ESSENCE, 1);

        assertFalse(craftInHut(SEEDS_BY_HAND));

        assertEquals(1, inHut(ESSENCE));
        assertEquals(0, inHut(SEEDS));
        assertEquals(0, worker.inventory().count(SEEDS));
    }

    /** MC AbstractBuilding.getHandlers is empty for a hut without workers. */
    @Test
    void craftInHutNeedsAWorker() {
        stock(ESSENCE, 2);

        assertFalse(craftInHut(SEEDS_BY_HAND));

        assertEquals(2, inHut(ESSENCE));
    }

    /** MC checkForFreeSpace: two outputs for one input need one free slot. */
    @Test
    void craftInHutNeedsAFreeSlotForEachExtraOutput() {
        CitizenData worker = h.hire();
        stock(ESSENCE, 2);
        h.t.containers.slots.put(h.hut.position(), 1);
        for (int slot = 0; slot < worker.inventory().size(); slot++) {
            worker.inventory().set(slot, Optional.of(new ItemAmount(STONE, 1)));
        }

        assertFalse(craftInHut(seedsGivingABucket()));
        assertEquals(2, inHut(ESSENCE));

        worker.inventory().set(0, Optional.empty());
        assertTrue(craftInHut(seedsGivingABucket()));
        assertEquals(1, inHut(SEEDS) + worker.inventory().count(SEEDS));
        assertEquals(1, inHut(BUCKET) + worker.inventory().count(BUCKET));
    }
}
