package dev.hycolony.core.crafting.job;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.BuildingType;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.crafting.module.CraftingHut;
import dev.hycolony.core.crafting.recipe.BenchRequirement;
import dev.hycolony.core.crafting.recipe.Ingredient;
import dev.hycolony.core.crafting.recipe.Recipe;
import dev.hycolony.core.crafting.recipe.RecipeFixtures;
import dev.hycolony.core.crafting.recipe.RecipeId;
import dev.hycolony.core.crafting.recipe.RecipeSource;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.item.ToolType;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.RequestManager;
import dev.hycolony.core.request.model.StackRequest;
import dev.hycolony.core.testing.TestContexts;
import dev.hycolony.core.testing.TestCrafters;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * A level-1 test crafter hut ({@link CraftingHut}) with a Farmingbench, one recipe learnt and a crafter hired, standing
 * at the hut, whose {@link CraftingWork} the tests drive step by step; another hut 5 blocks away asks for items.
 */
final class CrafterRig {
    static final ItemKey SEEDS = new ItemKey("Plant_Seeds_Wheat");
    static final ItemKey ESSENCE = RecipeFixtures.ESSENCE;
    static final ItemKey BUCKET = new ItemKey("Container_Bucket");
    static final ItemKey AXE = new ItemKey("Tool_Hatchet_Crude");

    final CraftingHut h;
    final TestContexts t;
    final Building other =
            Building.create(new BuildingType("test:other", "hut.other", 1, List.of()), new BlockPos(13, 64, 4), 0);
    final BlockPos bench;
    final RecipeId recipe;
    final CitizenData crafter;
    final BodyId body;
    final CraftingWork work;

    /** The Farmingbench seed recipe (2 essence each), under {@link CraftingHut#RULES}. */
    CrafterRig() {
        this(seeds(List.of(), Optional.empty()));
    }

    /** {@code learnt} taught to the hut, under {@link CraftingHut#RULES}. */
    CrafterRig(Recipe learnt) {
        this(new TestContexts(), CraftingHut.RULES, learnt);
    }

    /** {@code learnt} taught to the hut, in a colony made from {@code t}, under {@code rules} as crafting.json. */
    CrafterRig(TestContexts t, String rules, Recipe learnt) {
        this.t = t;
        h = new CraftingHut(t, rules, TestCrafters.HUT);
        t.bodies.instant = true;
        h.colony.buildings().add(other);
        bench = h.bench("Farmingbench", 1);
        recipe = h.teach(learnt);
        crafter = h.hire();
        body = t.bodies.existing(1, crafter.id(), Vec3.center(h.hut.position()));
        work = new CraftingWork(CraftingWorkContext.of(h.colony, job(), body).orElseThrow());
    }

    /** The Farmingbench recipe making 1 seed of 2 essence, giving {@code back} too, with {@code tool}. */
    static Recipe seeds(List<ItemAmount> back, Optional<ToolType> tool) {
        return new Recipe(
                List.of(new Ingredient.OfItem(ESSENCE, 2)),
                new ItemAmount(SEEDS, 1),
                back,
                new BenchRequirement("Farmingbench", List.of("Seeds"), 1),
                tool,
                new RecipeSource.Hytale(SEEDS.id()),
                false);
    }

    TestCrafters.TestCrafterJob job() {
        return (TestCrafters.TestCrafterJob) crafter.job().orElseThrow();
    }

    CraftingTasks tasks() {
        return job().craftingTasks();
    }

    RequestManager m() {
        return h.colony.requests();
    }

    /** Puts {@code count} of {@code item} in the hut block's container. */
    void stock(ItemKey item, int count) {
        t.containers
                .containers
                .computeIfAbsent(h.hut.position(), _ -> new LinkedHashMap<>())
                .merge(item, count, Integer::sum);
    }

    int inHut(ItemKey item) {
        return t.containers.count(h.hut.containers(), item);
    }

    int carried(ItemKey item) {
        return crafter.inventory().count(item);
    }

    /** The other hut asks for {@code count} seeds; returns its request. */
    Request ask(int count) {
        return m().get(m().createAndAssign(other, new StackRequest(SEEDS, count, count, true), -1))
                .orElseThrow();
    }

    /** The crafting task the crafting request resolver asked for {@code parent}. */
    Request task(Request parent) {
        return m().get(parent.children().getFirst()).orElseThrow();
    }

    /**
     * Calls {@code step} until it leaves {@code stay} (a walk, a wait, the hits of a run), at most 100 times; returns
     * the step it gave last.
     */
    static CraftingStep until(Supplier<CraftingStep> step, CraftingStep stay) {
        CraftingStep next = step.get();
        for (int i = 0; i < 100 && next == stay; i++) {
            next = step.get();
        }
        return next;
    }

    /** From the decision to the recipe chosen: the crafter walks to the hut, then decides, then takes the recipe. */
    CraftingStep toRecipe() {
        CraftingStep decided = until(work::decide, CraftingStep.START_WORKING);
        return decided == CraftingStep.GET_RECIPE ? work.getRecipe() : decided;
    }

    /** From the decision to the first hit: the recipe, its ingredients fetched from the hut, the walk to the bench. */
    CraftingStep toCraft() {
        CraftingStep step = toRecipe();
        for (int i = 0; i < 10 && step != CraftingStep.CRAFT; i++) {
            step = switch (step) {
                case QUERY_ITEMS -> work.queryItems();
                case GATHERING_REQUIRED_MATERIALS -> until(work::gather, CraftingStep.GATHERING_REQUIRED_MATERIALS);
                case START_WORKING -> work.decide();
                case GET_RECIPE -> work.getRecipe();
                default -> throw new AssertionError("unexpected step " + step);
            };
        }
        return step;
    }
}
