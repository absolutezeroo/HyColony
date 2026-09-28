package dev.hycolony.core.crafting.request;

import static dev.hycolony.core.crafting.request.CraftingRig.ESSENCE;
import static dev.hycolony.core.crafting.request.CraftingRig.SEEDS;
import static dev.hycolony.core.crafting.request.CraftingRig.tasksOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.crafting.recipe.BenchRequirement;
import dev.hycolony.core.crafting.recipe.Ingredient;
import dev.hycolony.core.crafting.recipe.Recipe;
import dev.hycolony.core.crafting.recipe.RecipeFixtures;
import dev.hycolony.core.crafting.recipe.RecipeId;
import dev.hycolony.core.crafting.recipe.RecipeSource;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.RequestManager;
import dev.hycolony.core.request.model.Crafting;
import dev.hycolony.core.request.model.Delivery;
import dev.hycolony.core.request.model.RequestState;
import dev.hycolony.core.request.model.RequestToken;
import dev.hycolony.core.request.model.StackList;
import dev.hycolony.core.request.model.StackRequest;
import dev.hycolony.core.request.resolver.PlayerResolver;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CraftingProductionResolverTest {
    private final CraftingRig rig = new CraftingRig();

    private RequestManager m() {
        return rig.m();
    }

    private CraftingProductionResolver production(boolean isPublic) {
        return rig.resolver(CraftingProductionResolver.class, isPublic);
    }

    /** A hand recipe of {@code inputs} making one {@code output}, giving {@code back} back. */
    private static Recipe byHand(String id, List<Ingredient> inputs, ItemKey output, List<ItemAmount> back) {
        return new Recipe(
                inputs,
                new ItemAmount(output, 1),
                back,
                new BenchRequirement(BenchRequirement.FIELDCRAFT, List.of("Basic"), 0),
                Optional.empty(),
                new RecipeSource.Hytale(id),
                false);
    }

    /** A private crafting task for the hut's own hand recipe {@code id}, asked by its private request resolver. */
    private Request privateTask(RecipeId id) {
        return m().get(m().createAndAssign(
                                rig.resolver(CraftingRequestResolver.class, false),
                                new Crafting(SEEDS, 10, 10, id.value(), false),
                                -1))
                .orElseThrow();
    }

    @Test
    void alreadyFulfillableTaskAsksForNothing() {
        CitizenData crafter = rig.benchSeedsWithACrafter();
        rig.stock(ESSENCE, 20);

        Request task = rig.task(rig.ask(rig.other, SEEDS, 10, 10));

        assertEquals(production(true).resolverId(), rig.resolverOf(task));
        assertEquals(List.of(), task.children());
        assertEquals(RequestState.IN_PROGRESS, task.state(), "the crafter has it to make");
        assertEquals(List.of(task.token()), tasksOf(crafter).taskQueue());
        assertEquals(List.of(), tasksOf(crafter).assignedTasks());
    }

    @Test
    void missingIngredientsAreRequestedTimesTheCount() {
        CitizenData crafter = rig.benchSeedsWithACrafter();

        Request task = rig.task(rig.ask(rig.other, SEEDS, 10, 4));

        assertEquals(List.of(new StackRequest(ESSENCE, 20, 8, true)), rig.children(task));
        assertEquals(List.of(task.token()), tasksOf(crafter).assignedTasks(), "scheduled until the essence is there");
        assertEquals(List.of(), tasksOf(crafter).taskQueue());
    }

    @Test
    void resourceTypeIngredientIsRequestedAsAStackList() {
        ItemKey oak = new ItemKey("Wood_Oak_Trunk");
        ItemKey birch = new ItemKey("Wood_Birch_Trunk");
        ItemKey planks = new ItemKey("Wood_Planks");
        rig.h.t.recipes.resourceType("Wood_Trunk", oak, birch);
        rig.h.teach(byHand("Planks", List.of(new Ingredient.OfResourceType("Wood_Trunk", 2)), planks, List.of()));
        rig.h.hire();

        Request task = rig.task(rig.ask(rig.other, planks, 10, 10));

        StackList trunks = (StackList) rig.children(task).getFirst();
        assertEquals(List.of(oak, birch), trunks.accepted());
        assertEquals("Wood_Trunk", trunks.description());
        assertEquals(20, trunks.count());
        assertEquals(20, trunks.minCount());
    }

    @Test
    void givenBackIngredientIsRequestedOnce() {
        ItemKey bucket = new ItemKey("Container_Bucket");
        rig.h.teach(byHand(
                "Seeds_With_Bucket",
                List.of(new Ingredient.OfItem(bucket, 1), new Ingredient.OfItem(ESSENCE, 2)),
                SEEDS,
                List.of(new ItemAmount(bucket, 1))));
        rig.h.hire();

        Request task = rig.task(rig.ask(rig.other, SEEDS, 10, 10));

        assertEquals(
                List.of(new StackRequest(bucket, 1, 1, true), new StackRequest(ESSENCE, 20, 20, true)),
                rig.children(task));
    }

    @Test
    void ingredientsReservedByATaskAreNotCountedForTheNext() {
        rig.benchSeedsWithACrafter();
        rig.stock(ESSENCE, 20);

        Request first = rig.task(rig.ask(rig.other, SEEDS, 10, 10));
        Request second = rig.task(rig.ask(rig.other, SEEDS, 10, 10));

        assertEquals(List.of(), first.children());
        assertEquals(List.of(new StackRequest(ESSENCE, 20, 20, true)), rig.children(second), "MC considerReservation");
    }

    @Test
    void taskGoesToTheLeastLoadedCrafter() {
        CitizenData first = rig.benchSeedsWithACrafter();
        CitizenData second = rig.h.hire();
        tasksOf(first).onTaskBeingScheduled(new RequestToken(UUID.randomUUID()));

        Request task = rig.task(rig.ask(rig.other, SEEDS, 10, 10));
        Request next = rig.task(rig.ask(rig.other, SEEDS, 5, 5));

        assertEquals(List.of(task.token()), tasksOf(second).assignedTasks());
        assertTrue(tasksOf(first).assignedTasks().contains(next.token()), "equal loads: the first crafter");
    }

    @Test
    void productionResolverTakesOnlyTheTasksOfItsOwnHut() {
        rig.benchSeedsWithACrafter();
        String recipe = rig.h.module.recipes().getFirst().value();

        Request fromOther = m().get(m().createAndAssign(rig.other, new Crafting(SEEDS, 1, 1, recipe, true), -1))
                .orElseThrow();

        assertFalse(production(true).canResolve(m(), fromOther));
        assertEquals(PlayerResolver.ID, rig.resolverOf(fromOther));
        assertFalse(production(true).handles(new Crafting(SEEDS, 1, 1, recipe, false)));
        assertTrue(production(false).handles(new Crafting(SEEDS, 1, 1, recipe, false)));
    }

    @Test
    void finishedTaskIsDeliveredToAnotherHut() {
        CitizenData crafter = rig.benchSeedsWithACrafter();
        rig.stock(ESSENCE, 20);
        Request parent = rig.ask(rig.other, SEEDS, 10, 10);
        Request task = rig.task(parent);
        m().addDelivery(task.token(), new ItemAmount(SEEDS, 10));

        tasksOf(crafter).finishRequest(rig.h.colony, true);

        assertEquals(List.of(), tasksOf(crafter).taskQueue());
        assertEquals(List.of(new ItemAmount(SEEDS, 10)), parent.deliveries());
        Delivery delivery = (Delivery) rig.children(task).getFirst();
        assertEquals(rig.h.hut.position(), delivery.start());
        assertEquals(rig.other.requesterId(), delivery.target());
        assertEquals(new ItemAmount(SEEDS, 10), delivery.stack());
        assertEquals(Delivery.DEFAULT_DELIVERY_PRIORITY, delivery.priority());
    }

    @Test
    void finishedTaskForTheSameHutNeedsNoDelivery() {
        CitizenData crafter = rig.benchSeedsWithACrafter();
        rig.stock(ESSENCE, 20);
        Request parent = rig.ask(rig.h.hut, SEEDS, 10, 10);
        Request task = rig.task(parent);
        m().addDelivery(task.token(), new ItemAmount(SEEDS, 10));

        tasksOf(crafter).finishRequest(rig.h.colony, true);

        assertTrue(m().get(task.token()).isEmpty(), "completed and received at once");
        assertEquals(RequestState.COMPLETED, parent.state());
        assertEquals(List.of(), parent.deliveries());
        assertTrue(m().all().stream().noneMatch(r -> r.requestable() instanceof Delivery));
    }

    @Test
    void cancelledTaskLeavesItsCraftersLists() {
        CitizenData crafter = rig.benchSeedsWithACrafter();
        Request parent = rig.ask(rig.other, SEEDS, 10, 10);

        m().updateState(parent.token(), RequestState.CANCELLED);

        assertEquals(List.of(), tasksOf(crafter).assignedTasks());
        assertTrue(m().all().isEmpty());
    }

    @Test
    void privateTaskCraftsAtOnceInTheHut() {
        RecipeId id = rig.h.teach(RecipeFixtures.fieldcraft("Seeds", SEEDS.id()));
        rig.h.hire();
        rig.stock(ESSENCE, 20);

        Request task = privateTask(id);

        assertEquals(production(false).resolverId(), rig.resolverOf(task));
        assertEquals(RequestState.COMPLETED, task.state());
        assertEquals(10, rig.inHut(SEEDS));
        assertEquals(0, rig.inHut(ESSENCE));
    }

    @Test
    void privateTaskWhoseRecipeWasRemovedFails() {
        RecipeId id = rig.h.teach(RecipeFixtures.fieldcraft("Seeds", SEEDS.id()));
        rig.h.hire();
        Request task = privateTask(id);
        RequestToken essence = task.children().getFirst();
        rig.stock(ESSENCE, 20);
        rig.h.module.remove(rig.h.colony, id);

        m().overrule(essence, List.of(new ItemAmount(ESSENCE, 20)));

        assertTrue(m().get(task.token()).isEmpty(), "failed: MC finds no module holding the recipe");
        assertEquals(0, rig.inHut(SEEDS));
    }
}
