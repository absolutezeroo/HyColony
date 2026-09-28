package dev.hycolony.core.crafting.job;

import static dev.hycolony.core.crafting.job.CrafterRig.AXE;
import static dev.hycolony.core.crafting.job.CrafterRig.BUCKET;
import static dev.hycolony.core.crafting.job.CrafterRig.ESSENCE;
import static dev.hycolony.core.crafting.job.CrafterRig.SEEDS;
import static dev.hycolony.core.crafting.job.CrafterRig.seeds;
import static dev.hycolony.core.crafting.job.CrafterRig.until;
import static dev.hycolony.core.crafting.job.CraftingStep.CRAFT;
import static dev.hycolony.core.crafting.job.CraftingStep.GATHERING_REQUIRED_MATERIALS;
import static dev.hycolony.core.crafting.job.CraftingStep.GET_RECIPE;
import static dev.hycolony.core.crafting.job.CraftingStep.IDLE;
import static dev.hycolony.core.crafting.job.CraftingStep.INVENTORY_FULL;
import static dev.hycolony.core.crafting.job.CraftingStep.NEEDS_ITEM;
import static dev.hycolony.core.crafting.job.CraftingStep.QUERY_ITEMS;
import static dev.hycolony.core.crafting.job.CraftingStep.START_WORKING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.Skill;
import dev.hycolony.core.crafting.module.CraftingHut;
import dev.hycolony.core.crafting.recipe.Ingredient;
import dev.hycolony.core.crafting.recipe.Recipe;
import dev.hycolony.core.crafting.task.CraftingTasks;
import dev.hycolony.core.job.JobAI;
import dev.hycolony.core.job.JobXp;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.item.ToolInfo;
import dev.hycolony.core.kernel.item.ToolType;
import dev.hycolony.core.logistics.warehouse.WarehouseBuilding;
import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.model.Delivery;
import dev.hycolony.core.request.model.Pickup;
import dev.hycolony.core.request.model.RequestState;
import dev.hycolony.core.request.model.ToolRequest;
import dev.hycolony.core.request.resolver.RetryingResolver;
import dev.hycolony.core.testing.TestContexts;
import java.util.List;
import java.util.Optional;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;

class CraftingWorkTest {
    /** Rolls 0: every improvement chance succeeds. */
    private static final RandomGenerator LUCKY = () -> 0L;

    private static final String REDUCEABLE_ESSENCE = """
            {"jobs": {"%s": {"allow": [{"bench": "Farmingbench", "categories": ["*"]}]}},
             "reduceable": {"ingredients": ["Ingredient_Life_Essence"], "excludedProducts": []}}""".formatted(CraftingHut.JOB);

    private final CrafterRig rig = new CrafterRig();

    @Test
    void noTaskMeansIdle() {
        assertFalse(rig.work.hasWorkToDo());
        assertEquals(IDLE, rig.work.decide());
        assertTrue(rig.job().createAI(rig.h.colony, rig.body).canGoIdle(), "the citizen wanders meanwhile");
    }

    @Test
    void crafterWithSomethingToDumpDoesNotGoIdle() {
        rig.job().incrementActions();

        assertFalse(rig.job().createAI(rig.h.colony, rig.body).canGoIdle(), "it dumps first");
    }

    @Test
    void missingModuleFailsTheTask() {
        rig.stock(ESSENCE, 20);
        Request parent = rig.ask(10);
        Request task = rig.task(parent);
        rig.h.module.toggle(rig.h.colony, 0); // disabled: no crafting module of the hut holds the recipe any more

        assertEquals(START_WORKING, rig.toRecipe());

        assertTrue(rig.m().get(task.token()).isEmpty(), "failed");
        assertEquals(RetryingResolver.ID, resolverOf(parent), "the hut no longer takes it");
        assertEquals(1, rig.job().actionsDone(), "a failed task is an action: the crafter dumps");
        assertEquals(20, rig.inHut(ESSENCE));
    }

    @Test
    void missingToolRequestsItAndFailsTheTask() {
        CrafterRig axe = new CrafterRig(seeds(List.of(), Optional.of(ToolType.AXE)));
        axe.stock(ESSENCE, 20);
        Request task = axe.task(axe.ask(10));

        assertEquals(START_WORKING, axe.toRecipe());

        assertTrue(axe.m().get(task.token()).isEmpty(), "failed");
        List<Request> tools = axe.m().byRequester(axe.h.hut.requesterId()).stream()
                .filter(r -> r.requestable() instanceof ToolRequest t && t.type() == ToolType.AXE)
                .toList();
        assertEquals(1, tools.size());
        assertEquals(axe.crafter.id(), tools.getFirst().citizenId(), "the crafter waits for it");
        assertTrue(axe.work.needsItem());
    }

    @Test
    void toolBroughtToTheHutIsPickedUpWhileWaiting() {
        CrafterRig axe = new CrafterRig(seeds(List.of(), Optional.of(ToolType.AXE)));
        axe.t.catalog.tools.put(AXE, new ToolInfo(ToolType.AXE, 1, 1f));
        axe.stock(ESSENCE, 20);
        axe.task(axe.ask(10));
        assertEquals(START_WORKING, axe.toRecipe());

        axe.stock(AXE, 1); // a player puts one in the hut's chest
        assertEquals(IDLE, until(axe.work::waitForRequests, NEEDS_ITEM));

        assertEquals(1, axe.carried(AXE));
        assertFalse(axe.work.needsItem());
    }

    @Test
    void toolInTheHutIsTakenForTheRecipe() {
        CrafterRig axe = new CrafterRig(seeds(List.of(), Optional.of(ToolType.AXE)));
        axe.t.catalog.tools.put(AXE, new ToolInfo(ToolType.AXE, 1, 1f));
        axe.stock(ESSENCE, 20);
        axe.stock(AXE, 1);
        axe.task(axe.ask(10));

        assertEquals(QUERY_ITEMS, axe.toRecipe());

        assertEquals(1, axe.carried(AXE));
        assertEquals(0, axe.inHut(AXE));
    }

    @Test
    void ingredientInTheHutIsFetchedBeforeCrafting() {
        rig.stock(ESSENCE, 20);
        rig.task(rig.ask(10));

        assertEquals(QUERY_ITEMS, rig.toRecipe());
        assertEquals(10, rig.tasks().maxCraftingCount());
        assertEquals(0, rig.tasks().craftCounter());
        assertEquals(GATHERING_REQUIRED_MATERIALS, rig.work.queryItems());
        assertEquals(START_WORKING, until(rig.work::gather, GATHERING_REQUIRED_MATERIALS));

        assertEquals(20, rig.carried(ESSENCE));
        assertEquals(0, rig.inHut(ESSENCE));
        assertEquals(QUERY_ITEMS, rig.work.decide());
        assertEquals(CRAFT, rig.work.queryItems());
    }

    @Test
    void runsAlreadyMadeCountTowardsTheTask() {
        rig.stock(ESSENCE, 20);
        rig.task(rig.ask(10));
        rig.crafter.inventory().insert(new ItemAmount(SEEDS, 6), _ -> 64); // made before a restart, say

        assertEquals(QUERY_ITEMS, rig.toRecipe());

        assertEquals(6, rig.tasks().craftCounter(), "MC doneOpsCount: the seeds it carries");
        assertEquals(10, rig.tasks().maxCraftingCount());
    }

    @Test
    void inventoryFullOfOtherItemsIsDumpedOncePerRecipe() {
        for (String other : List.of("Rock_Stone", "Soil_Dirt", "Wood_Oak_Trunk", "Ingredient_Fibre")) {
            rig.crafter.inventory().insert(new ItemAmount(new ItemKey(other), 1), _ -> 64);
        }
        rig.stock(ESSENCE, 20);
        rig.task(rig.ask(10));

        assertEquals(INVENTORY_FULL, rig.toRecipe(), "4 slots of other items: more than 3");
        assertEquals(QUERY_ITEMS, rig.work.getRecipe(), "once per recipe, even if the dump left them");
    }

    /** MC WorkerUtil.isPartOfRecipe knows the grid's tools and the secondary outputs, not the recipe's required tool. */
    @Test
    void toolTheRecipeRequiresCountsAsAnOtherItem() {
        CrafterRig axe = new CrafterRig(seeds(List.of(), Optional.of(ToolType.AXE)));
        axe.t.catalog.tools.put(AXE, new ToolInfo(ToolType.AXE, 1, 1f));
        for (String other : List.of("Rock_Stone", "Soil_Dirt", "Wood_Oak_Trunk")) {
            axe.crafter.inventory().insert(new ItemAmount(new ItemKey(other), 1), _ -> 64);
        }
        axe.crafter.inventory().insert(new ItemAmount(AXE, 1), _ -> 1);
        axe.stock(ESSENCE, 20);
        axe.task(axe.ask(10));

        assertEquals(INVENTORY_FULL, axe.toRecipe(), "3 slots of other items and the axe: more than 3");
    }

    @Test
    void ingredientNowhereForgetsTheRecipe() {
        rig.stock(ESSENCE, 20);
        Request task = rig.task(rig.ask(10));
        assertEquals(QUERY_ITEMS, rig.toRecipe());

        rig.t.containers.containers.clear(); // a player takes the essence

        assertEquals(GET_RECIPE, rig.work.queryItems());
        assertEquals(START_WORKING, rig.work.getRecipe(), "no recipe can be made now: the task fails");
        assertTrue(rig.m().get(task.token()).isEmpty());
    }

    @Test
    void craftingTakesTheSkillBasedNumberOfHits() {
        rig.crafter.skills().set(Skill.Dexterity, 1, 0); // the hut's primary skill: its crafting speed
        rig.stock(ESSENCE, 2);
        rig.task(rig.ask(1));
        JobAI ai = rig.job().createAI(rig.h.colony, rig.body);
        long firstHit = -1;
        long lastHit = -1;
        for (int i = 0; i < 2_000 && rig.carried(SEEDS) == 0; i++) {
            int before = rig.t.effects.hits.size();
            rig.t.clock.tick++;
            ai.tick();
            if (rig.t.effects.hits.size() > before) {
                firstHit = firstHit < 0 ? rig.t.clock.tick : firstHit;
                lastHit = rig.t.clock.tick;
            }
        }

        assertEquals(1, rig.carried(SEEDS));
        assertEquals(CraftingProgress.requiredHits(1), rig.t.effects.hits.size());
        assertEquals(30, rig.t.effects.hits.size());
        assertEquals(29L * CraftingWork.HIT_DELAY, lastHit - firstHit, "one hit every HIT_DELAY ticks");
        assertTrue(rig.t.effects.hits.stream().allMatch(rig.bench::equals), "at the recipe's bench");
    }

    @Test
    void lastRunImprovesTheRecipeThenDumps() {
        TestContexts t = new TestContexts();
        t.random = () -> LUCKY;
        CrafterRig lucky = new CrafterRig(t, REDUCEABLE_ESSENCE, seeds(List.of(), Optional.empty()));
        lucky.stock(ESSENCE, 2);
        Request task = lucky.task(lucky.ask(1));
        assertEquals(CRAFT, lucky.toCraft());

        assertEquals(INVENTORY_FULL, until(lucky.work::craft, CRAFT));

        assertEquals(1, lucky.carried(SEEDS));
        assertEquals(1, lucky.job().actionsDone());
        Recipe listed = lucky.h
                .colony
                .recipes()
                .get(lucky.h.module.recipes().getFirst())
                .orElseThrow();
        assertEquals(List.of(new Ingredient.OfItem(ESSENCE, 1)), listed.inputs(), "improved in place");
        assertEquals(List.of(0, 0, 0), counters(lucky));

        assertEquals(IDLE, until(lucky.work::dump, INVENTORY_FULL));

        assertEquals(1, lucky.inHut(SEEDS));
        assertEquals(0, lucky.job().actionsDone());
        assertEquals(RequestState.FOLLOWUP_IN_PROGRESS, task.state(), "finished, its seed on its way to the asker");
        assertEquals(List.of(new ItemAmount(SEEDS, 1)), task.deliveries());
    }

    @Test
    void finishedTaskEarnsHalfItsRunsTwiceAsExperience() {
        CitizenData expected = new CitizenData(99);
        JobXp.award(expected, Skill.Dexterity, Skill.Knowledge, 0.5, new JobXp.Levels(1, 0));
        JobXp.award(expected, Skill.Dexterity, Skill.Knowledge, 0.5, new JobXp.Levels(1, 0));
        rig.stock(ESSENCE, 2);
        rig.task(rig.ask(1));
        assertEquals(CRAFT, rig.toCraft());

        assertEquals(INVENTORY_FULL, until(rig.work::craft, CRAFT));
        assertEquals(IDLE, until(rig.work::dump, INVENTORY_FULL));

        // MC awards count / 2 when it finalizes the task, then again when it finishes it after the dump.
        assertEquals(
                expected.skills().experience(Skill.Dexterity),
                rig.crafter.skills().experience(Skill.Dexterity));
    }

    @Test
    void dumpAsksNoPickupWhileATaskIsUnderWay() {
        rig.stock(ESSENCE, 4);
        rig.task(rig.ask(2));
        assertEquals(CRAFT, rig.toCraft());
        rig.job().incrementActions(); // a dump due mid-task

        assertEquals(IDLE, until(rig.work::dump, INVENTORY_FULL));

        assertEquals(List.of(), pickups());
        assertEquals(4, rig.inHut(ESSENCE), "what it carried went back to the hut");
    }

    @Test
    void dumpWithoutTaskAsksForAPickup() {
        rig.crafter.inventory().insert(new ItemAmount(SEEDS, 3), _ -> 64);
        rig.job().incrementActions();

        assertEquals(IDLE, until(rig.work::dump, INVENTORY_FULL));

        assertEquals(1, pickups().size());
    }

    @Test
    void secondaryOutputsGoToTheNearestWarehouse() {
        CrafterRig bucket = new CrafterRig(seeds(List.of(new ItemAmount(BUCKET, 1)), Optional.empty()));
        Building near = warehouse(bucket, new BlockPos(20, 64, 0), 1);
        Building far = warehouse(bucket, new BlockPos(60, 64, 0), 1);
        warehouse(bucket, new BlockPos(12, 64, 0), 0); // nearer, but not built yet
        bucket.stock(ESSENCE, 2);
        Request task = bucket.task(bucket.ask(1));
        assertEquals(CRAFT, bucket.toCraft());
        assertEquals(INVENTORY_FULL, until(bucket.work::craft, CRAFT));
        assertEquals(List.of(new ItemAmount(SEEDS, 1)), task.deliveries(), "only the output is the task's");
        bucket.tasks().secondaryOutputs().merge(BUCKET, 69, Integer::sum); // 70 in all: more than a stack

        assertEquals(IDLE, until(bucket.work::dump, INVENTORY_FULL));

        List<Delivery> sent = bucket.m().byRequester(near.requesterId()).stream()
                .map(Request::requestable)
                .filter(Delivery.class::isInstance)
                .map(Delivery.class::cast)
                .toList();
        assertEquals(
                List.of(new ItemAmount(BUCKET, 64), new ItemAmount(BUCKET, 6)),
                sent.stream().map(Delivery::stack).toList());
        for (Delivery d : sent) {
            assertEquals(bucket.h.hut.position(), d.start());
            assertEquals(near.requesterId(), d.target());
            assertEquals(Pickup.MAX_BUILDING_PRIORITY, d.priority());
        }
        assertEquals(List.of(), bucket.m().byRequester(far.requesterId()));
        assertTrue(bucket.tasks().secondaryOutputs().isEmpty());
    }

    @Test
    void cancelledRequestMidCraftIsAbandoned() {
        rig.stock(ESSENCE, 20);
        Request parent = rig.ask(10);
        Request task = rig.task(parent);
        assertEquals(CRAFT, rig.toCraft());
        for (int i = 0; i < 4; i++) {
            assertEquals(CRAFT, rig.work.craft()); // the walk to the bench, then 3 hits
        }
        int hits = rig.t.effects.hits.size();

        rig.m().updateState(parent.token(), RequestState.CANCELLED); // the asker no longer wants the seeds
        assertTrue(rig.m().get(task.token()).isEmpty());

        assertEquals(START_WORKING, rig.work.craft());
        assertEquals(hits, rig.t.effects.hits.size(), "no hit for a task that is gone");
        assertEquals(List.of(0, 0, 0), counters(rig));
        assertEquals(1, rig.job().actionsDone(), "it dumps what it holds");
        assertEquals(IDLE, rig.work.decide());
        assertEquals(IDLE, until(rig.work::dump, INVENTORY_FULL));
        assertEquals(20, rig.inHut(ESSENCE), "the essence went back to the hut");
        assertEquals(0, rig.carried(SEEDS));
    }

    @Test
    void newTaskAfterAnAbandonedOneStartsAfresh() {
        rig.stock(ESSENCE, 20);
        Request first = rig.ask(10);
        assertEquals(CRAFT, rig.toCraft());
        rig.m().updateState(first.token(), RequestState.CANCELLED);
        assertEquals(START_WORKING, rig.work.craft());
        assertEquals(IDLE, until(rig.work::dump, INVENTORY_FULL));

        rig.task(rig.ask(3));

        assertEquals(QUERY_ITEMS, rig.toRecipe());
        assertEquals(3, rig.tasks().maxCraftingCount());
    }

    private static List<Integer> counters(CrafterRig r) {
        CraftingTasks tasks = r.tasks();
        return List.of(tasks.maxCraftingCount(), tasks.craftCounter(), tasks.progress());
    }

    private String resolverOf(Request r) {
        return rig.m().resolverOf(r.token()).map(res -> res.resolverId()).orElseThrow();
    }

    private List<Request> pickups() {
        return rig.m().byRequester(rig.h.hut.requesterId()).stream()
                .filter(r -> r.requestable() instanceof Pickup)
                .toList();
    }

    private static Building warehouse(CrafterRig r, BlockPos pos, int level) {
        Building w = Building.create(WarehouseBuilding.TYPE, pos, 0);
        w.setLevel(level);
        w.setBuilt(level > 0);
        r.h.colony.buildings().add(w);
        return w;
    }
}
