package dev.hycolony.core.crafting.request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.BuildingResolver;
import dev.hycolony.core.building.BuildingType;
import dev.hycolony.core.crafting.module.CraftingHut;
import dev.hycolony.core.crafting.recipe.BenchRequirement;
import dev.hycolony.core.crafting.recipe.Ingredient;
import dev.hycolony.core.crafting.recipe.Recipe;
import dev.hycolony.core.crafting.recipe.RecipeFixtures;
import dev.hycolony.core.crafting.recipe.RecipeId;
import dev.hycolony.core.crafting.recipe.RecipeSource;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.RequestManager;
import dev.hycolony.core.request.Resolver;
import dev.hycolony.core.request.model.Crafting;
import dev.hycolony.core.request.model.RequestToken;
import dev.hycolony.core.request.model.Requestable;
import dev.hycolony.core.request.model.StackRequest;
import dev.hycolony.core.request.resolver.RetryingResolver;
import dev.hycolony.core.testing.FakeResolver;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CraftingRequestResolverTest {
    private static final ItemKey SEEDS = new ItemKey("Plant_Seeds_Wheat");

    private final CraftingHut h = new CraftingHut();
    /** Another hut, 5 blocks from the crafting hut (3 east, 4 south). */
    private final Building other =
            Building.create(new BuildingType("test:other", "hut.other", 1, List.of()), new BlockPos(13, 64, 4), 0);

    CraftingRequestResolverTest() {
        h.colony.buildings().add(other);
    }

    private RequestManager m() {
        return h.colony.requests();
    }

    private CraftingRequestResolver resolver(boolean isPublic) {
        return h.hut.resolvers().stream()
                .filter(r -> r instanceof CraftingRequestResolver c && c.isPublic() == isPublic)
                .map(CraftingRequestResolver.class::cast)
                .findFirst()
                .orElseThrow();
    }

    private Request ask(Building from, ItemKey item, int count) {
        return m().get(m().createAndAssign(from, new StackRequest(item, count, count, true), -1))
                .orElseThrow();
    }

    private String resolverOf(Request r) {
        return m().resolverOf(r.token()).map(Resolver::resolverId).orElseThrow();
    }

    private List<Crafting> tasks(Request r) {
        List<Crafting> out = new ArrayList<>();
        for (RequestToken child : r.children()) {
            out.add((Crafting) m().get(child).orElseThrow().requestable());
        }
        return out;
    }

    /** A hand recipe of the given inputs, making one {@code output}. */
    private static Recipe byHand(String id, List<Ingredient> inputs, ItemKey output) {
        return new Recipe(
                inputs,
                new ItemAmount(output, 1),
                List.of(),
                new BenchRequirement(BenchRequirement.FIELDCRAFT, List.of("Basic"), 0),
                Optional.empty(),
                new RecipeSource.Hytale(id),
                false);
    }

    /** The Farmingbench seed recipe, taught to a hut with its bench and a worker. */
    private RecipeId benchSeedsWithAWorker() {
        h.bench("Farmingbench", 1);
        RecipeId id = h.teach(RecipeFixtures.at("Farmingbench", "Seeds", SEEDS.id()));
        h.hire();
        return id;
    }

    @Test
    void craftingHutOffersPublicThenPrivateResolvers() {
        List<String> ids = h.hut.resolvers().stream().map(Resolver::resolverId).toList();

        assertEquals(3, ids.size());
        assertTrue(h.hut.resolvers().getFirst() instanceof BuildingResolver);
        assertEquals(resolver(true).resolverId(), ids.get(1), "MC: the farmer's crafting module comes first");
        assertEquals(resolver(false).resolverId(), ids.get(2));
        assertEquals(CraftingRequestResolver.PRIORITY, resolver(true).priority());
    }

    @Test
    void publicResolverServesAnotherHut() {
        RecipeId id = benchSeedsWithAWorker();

        Request request = ask(other, SEEDS, 10);

        assertEquals(resolver(true).resolverId(), resolverOf(request));
        Crafting task = tasks(request).getFirst();
        assertEquals(new Crafting(SEEDS, 10, 10, id.value(), true), task);
        assertEquals(id.value(), task.recipeId());
        assertTrue(task.isPublic());
    }

    @Test
    void smallRequestIsOneBatch() {
        benchSeedsWithAWorker();

        assertEquals(1, tasks(ask(other, SEEDS, 10)).size());
    }

    /** Review focus 5: never one giant task. */
    @Test
    void largeRequestIsSplitIntoBatchesThatFitTheInventory() {
        benchSeedsWithAWorker();

        List<Crafting> tasks = tasks(ask(other, SEEDS, 1000));

        assertEquals(List.of(500, 500), tasks.stream().map(Crafting::count).toList());
    }

    @Test
    void privateResolverServesOnlyItsOwnHut() {
        h.teach(RecipeFixtures.fieldcraft("Seeds", SEEDS.id()));
        h.hire();

        assertTrue(resolver(false).canResolve(m(), ask(h.hut, SEEDS, 10)));
        assertFalse(resolver(false).canResolve(m(), ask(other, SEEDS, 10)));
        assertTrue(resolver(true).canResolve(m(), ask(other, SEEDS, 10)));
    }

    @Test
    void privateResolverServesItsHutsOwnResolvers() {
        h.teach(RecipeFixtures.fieldcraft("Seeds", SEEDS.id()));
        h.hire();
        Request parent = ask(other, SEEDS, 10);

        RequestToken child = m().createChild(resolver(true), parent.token(), new StackRequest(SEEDS, 2, 2, true));

        assertTrue(resolver(false).canResolve(m(), m().get(child).orElseThrow()), "MC: the requester's location");
    }

    @Test
    void privateResolverTakesOnlyRecipesWithoutBench() {
        benchSeedsWithAWorker();
        Request own = ask(h.hut, SEEDS, 10);

        assertFalse(resolver(false).canResolve(m(), own), "MC: intermediate AIR only");
        assertTrue(resolver(true).canResolve(m(), own));
    }

    @Test
    void hutWithoutWorkerCannotResolve() {
        h.bench("Farmingbench", 1);
        h.teach(RecipeFixtures.at("Farmingbench", "Seeds", SEEDS.id()));

        Request request = ask(other, SEEDS, 10);

        assertFalse(resolver(true).canResolve(m(), request));
        assertEquals(RetryingResolver.ID, resolverOf(request));
    }

    @Test
    void levelZeroHutCannotResolve() {
        benchSeedsWithAWorker();
        h.hut.setLevel(0);

        assertFalse(resolver(true).canResolve(m(), ask(other, SEEDS, 10)));
    }

    @Test
    void itemNoRecipeMakesIsLeftToOthers() {
        benchSeedsWithAWorker();

        Request request = ask(other, new ItemKey("Rock_Stone"), 10);

        assertFalse(resolver(true).canResolve(m(), request));
        assertEquals(Optional.empty(), resolver(true).attemptResolve(m(), request));
    }

    /** Review focus 4: X made from X is refused at once, without looping. */
    @Test
    void recipeNeedingItsOwnOutputIsACycle() {
        h.teach(byHand("Seeds_From_Seeds", List.of(new Ingredient.OfItem(SEEDS, 2)), SEEDS));
        h.hire();

        Request request = ask(other, SEEDS, 10);

        assertFalse(resolver(true).canResolve(m(), request));
        assertEquals(RetryingResolver.ID, resolverOf(request));
        assertEquals(List.of(), request.children());
    }

    /** X made from Y and Y from X: the request for Y that crafting X asks is refused. */
    @Test
    void twoRecipesMakingEachOtherAreACycle() {
        ItemKey x = new ItemKey("X");
        ItemKey y = new ItemKey("Y");
        h.teach(byHand("X_From_Y", List.of(new Ingredient.OfItem(y, 2)), x));
        h.teach(byHand("Y_From_X", List.of(new Ingredient.OfItem(x, 2)), y));
        h.hire();
        m().registerBuiltIn(new IngredientAsker());

        Request request = ask(other, x, 10);

        assertEquals(resolver(true).resolverId(), resolverOf(request));
        Request task = m().get(request.children().getFirst()).orElseThrow();
        Request forY = m().get(task.children().getFirst()).orElseThrow();
        assertEquals(new StackRequest(y, 20, 20, true), forY.requestable());
        assertEquals(RetryingResolver.ID, resolverOf(forY), "crafting Y would ask for 40 X under a request for 10");
        assertEquals(3, m().all().size());
    }

    @Test
    void suitabilityIsTheDistance() {
        benchSeedsWithAWorker();

        assertEquals(5.0, resolver(true).suitability(m(), ask(other, SEEDS, 10)));
        assertEquals(0.0, resolver(true).suitability(m(), ask(h.hut, SEEDS, 10)));
    }

    /** Asks for the ingredients of each crafting task, as the production resolver will (MC createRequestsForRecipe). */
    private final class IngredientAsker extends FakeResolver {
        IngredientAsker() {
            super("test:ingredients", 100);
        }

        @Override
        public boolean handles(Requestable requestable) {
            return requestable instanceof Crafting;
        }

        @Override
        public boolean canResolve(RequestManager manager, Request r) {
            return true;
        }

        @Override
        public Optional<List<Requestable>> attemptResolve(RequestManager manager, Request r) {
            Crafting task = (Crafting) r.requestable();
            Recipe recipe =
                    h.colony.recipes().get(new RecipeId(task.recipeId())).orElseThrow();
            List<Requestable> asked = new ArrayList<>();
            for (Ingredient in : recipe.cleanedInput()) {
                Ingredient.OfItem item = (Ingredient.OfItem) in;
                asked.add(
                        new StackRequest(item.item(), in.amount() * task.count(), in.amount() * task.minCount(), true));
            }
            return Optional.of(asked);
        }

        @Override
        public void resolve(RequestManager manager, Request r) {}
    }
}
