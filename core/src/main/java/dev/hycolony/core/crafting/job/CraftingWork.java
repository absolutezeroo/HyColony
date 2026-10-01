package dev.hycolony.core.crafting.job;

import dev.hycolony.core.crafting.job.RecipeCounts.Needed;
import dev.hycolony.core.crafting.module.CraftingModules;
import dev.hycolony.core.crafting.module.RecipeChoice;
import dev.hycolony.core.crafting.module.RecipeChoice.Chosen;
import dev.hycolony.core.crafting.module.RecipeChoice.FulfillQuery;
import dev.hycolony.core.crafting.recipe.Recipe;
import dev.hycolony.core.crafting.recipe.RecipeId;
import dev.hycolony.core.crafting.task.CraftingTasks;
import dev.hycolony.core.kernel.item.ToolType;
import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.model.Crafting;
import dev.hycolony.core.request.model.RequestState;
import java.util.Optional;
import java.util.OptionalInt;
import org.jspecify.annotations.Nullable;

/**
 * A crafter's work (MC AbstractEntityAICrafting): each step returns the {@link CraftingStep} to go to, and a concrete
 * crafter AI maps each state to its step. The crafter takes the head task of its queue, picks the recipe, fetches its
 * ingredients from the hut, hits the bench until each run is made ({@link CraftingRun}), then dumps and finishes the
 * task. Deviation from MC: a component the job's AI owns rather than a base class it extends (ArchitectureTest).
 */
public final class CraftingWork {
    /** MC CitizenConstants.STANDARD_DELAY: the rate of the decision, recipe and ingredient steps. */
    public static final int STANDARD_DELAY = 5;
    /** MC AbstractEntityAICrafting.HIT_DELAY: the ticks between two hits. */
    public static final int HIT_DELAY = 10;
    /** MC Constants.TICKS_SECOND: the rate of the idle, gathering and dump steps. */
    public static final int TICKS_SECOND = 20;
    /**
     * MC getActionsDoneUntilDumping and getActionRewardForCraftingSuccess of AbstractEntityAICrafting: 1, so a crafter
     * dumps after each task. A crafter that overrides both (the farmer: 64) passes its own to CraftingWorkContext.
     */
    public static final int ACTIONS_UNTIL_DUMP = 1;

    private final CraftingWorkContext ctx;
    private final RecipeCounts counts;
    private final CrafterHands hands;
    private final CraftedOutputs outputs;
    private final CraftingRun run;
    /** MC currentRequest: the task whose recipe is under way. */
    private @Nullable Request currentRequest;
    /** MC currentRecipeStorage: the recipe under way. */
    private @Nullable Chosen currentRecipe;
    /** MC dumped: the inventory was emptied once already for this recipe. */
    private boolean dumped;
    /** MC needsCurrently: the ingredient to fetch from the hut. */
    private @Nullable Needed needed;

    public CraftingWork(CraftingWorkContext ctx) {
        this.ctx = ctx;
        this.counts = new RecipeCounts(ctx.stock(), ctx.recipes());
        this.hands = new CrafterHands(ctx);
        this.outputs = new CraftedOutputs(ctx);
        this.run = new CraftingRun(ctx, outputs);
    }

    /** MC hasWorkToDo: the crafter's queue holds a live task. */
    public boolean hasWorkToDo() {
        return ctx.tasks().currentTask(ctx.colony()).isPresent();
    }

    /** MC decide: at the hut, the next crafting step; IDLE without a task; waits there while a dump is due. */
    public CraftingStep decide() {
        if (!hasWorkToDo()) {
            return CraftingStep.IDLE;
        }
        if (!ctx.walkToHut() || ctx.job().actionsDone() >= ctx.stock().actionsUntilDump()) {
            return CraftingStep.START_WORKING;
        }
        return nextCraftingState();
    }

    /**
     * MC getNextCraftingState: a dump first if other items fill the inventory (once per recipe), then the ingredients
     * of the recipe under way, else a recipe for the head task.
     */
    private CraftingStep nextCraftingState() {
        Chosen recipe = currentRecipe;
        if (recipe != null && !dumped && counts.tooManyOtherItems(recipe.recipe())) {
            dumped = true;
            return CraftingStep.INVENTORY_FULL;
        }
        return currentRequest != null && recipe != null ? CraftingStep.QUERY_ITEMS : CraftingStep.GET_RECIPE;
    }

    /**
     * MC getRecipe: the first recipe of the hut's module holding the task's recipe that makes its item and can be made
     * once; its tool; then the runs to make (MC maxCraftingCount, craftCounter). The task fails (FAILED, one action)
     * if there is none, the tool is missing (it is asked for) or an ingredient falls short.
     */
    public CraftingStep getRecipe() {
        Request task = ctx.tasks().currentTask(ctx.colony()).orElse(null);
        if (task == null) {
            return CraftingStep.START_WORKING;
        }
        if (!(task.requestable() instanceof Crafting crafting)) {
            return failTask(); // only crafting tasks are queued: a broken save, not a game state
        }
        Chosen chosen = CraftingModules.holding(ctx.hut(), new RecipeId(crafting.recipeId()))
                .flatMap(module -> RecipeChoice.firstFulfillable(
                        ctx.colony(), ctx.hut(), module, crafting.stack()::equals, FulfillQuery.of(1)))
                .orElse(null);
        if (chosen == null) {
            return failTask();
        }
        if (!dumped && counts.tooManyOtherItems(chosen.recipe())) {
            dumped = true;
            currentRecipe = null;
            return CraftingStep.INVENTORY_FULL;
        }
        Optional<ToolType> tool = chosen.recipe().requiredTool();
        if (tool.isPresent() && ctx.tools().missing(tool.get(), ctx.stock(), ctx::walkToHut)) {
            return failTask();
        }
        currentRequest = task;
        return counts.forTask(chosen.recipe(), crafting)
                .map(c -> start(chosen, c))
                .orElseGet(this::failTask);
    }

    /** The recipe is under way: its counts are the crafter's, its ingredients come next. */
    private CraftingStep start(Chosen chosen, RecipeCounts.Counts c) {
        currentRecipe = chosen;
        ctx.tasks().setMaxCraftingCount(c.maxCraftingCount());
        ctx.tasks().setCraftCounter(c.craftCounter());
        return CraftingStep.QUERY_ITEMS;
    }

    /**
     * MC's failed task: FAILED, which hands its parent back to the request system, and an action done, so the crafter
     * dumps before the next one. The recipe is forgotten (MC keeps it when no module holds the task's recipe).
     */
    private CraftingStep failTask() {
        currentRecipe = null;
        ctx.tasks().finishRequest(ctx.colony(), false);
        ctx.job().incrementActions(ctx.stock().actionsUntilDump()); // MC getActionRewardForCraftingSuccess
        return CraftingStep.START_WORKING;
    }

    /** MC queryItems: checks the ingredients of the recipe under way ({@link #checkForItems}). */
    public CraftingStep queryItems() {
        Chosen recipe = currentRecipe;
        return recipe == null ? CraftingStep.START_WORKING : checkForItems(recipe.recipe());
    }

    /**
     * MC checkForItems: CRAFT once the inventory holds every ingredient for the runs; an ingredient the hut still has
     * is fetched first; one that is nowhere makes the crafter forget the recipe and look again.
     */
    private CraftingStep checkForItems(Recipe recipe) {
        CraftingTasks tasks = ctx.tasks();
        Needed missing = counts.shortfall(recipe, tasks.maxCraftingCount(), tasks.craftCounter())
                .orElse(null);
        if (missing == null) {
            return CraftingStep.CRAFT;
        }
        if (counts.inHut(missing.ingredient()) > 0) {
            needed = missing;
            return CraftingStep.GATHERING_REQUIRED_MATERIALS;
        }
        currentRecipe = null;
        currentRequest = null;
        return CraftingStep.GET_RECIPE;
    }

    /**
     * MC getNeededItem: at the hut, takes the missing ingredient into the inventory, then START_WORKING. Deviation
     * from MC: the hut's containers are one stock ({@link dev.hycolony.core.job.work.WorkerStock#take}), where MC walks
     * to each chest holding the item.
     */
    public CraftingStep gather() {
        Needed need = needed;
        if (need == null) {
            return CraftingStep.START_WORKING;
        }
        if (!ctx.walkToHut()) {
            return CraftingStep.GATHERING_REQUIRED_MATERIALS;
        }
        counts.fetch(need);
        needed = null;
        return CraftingStep.START_WORKING;
    }

    /**
     * MC craft, every {@link #HIT_DELAY} ticks: at the bench, one hit more; once the hits of a run are given and the
     * ingredients still there, the run is made ({@link CraftingRun#make}). A task gone meanwhile is abandoned
     * ({@link #abandon}).
     */
    public CraftingStep craft() {
        Chosen recipe = currentRecipe;
        Request current = currentRequest;
        Optional<Request> head = ctx.tasks().currentTask(ctx.colony());
        if (recipe == null) {
            return CraftingStep.START_WORKING;
        }
        if (head.isEmpty()) {
            return current == null ? CraftingStep.START_WORKING : abandon();
        }
        if (current == null) {
            return CraftingStep.GET_RECIPE;
        }
        if (!hands.walkToWork(recipe.recipe())) {
            return CraftingStep.CRAFT;
        }
        return hit(recipe, current, head.get());
    }

    /**
     * MC craft at the bench: one hit more (the tool in hand, if the recipe needs one); the task still live, the run is
     * made once the hits are given and the ingredients still there.
     */
    private CraftingStep hit(Chosen recipe, Request current, Request head) {
        int progress = ctx.tasks().progress() + 1;
        ctx.tasks().setProgress(progress);
        int required = CraftingProgress.requiredHits(
                ctx.citizen().skills().level(ctx.skills().speed()));
        OptionalInt toolSlot =
                recipe.recipe().requiredTool().map(ctx.stock()::toolInInventory).orElse(OptionalInt.empty());
        hands.hit(recipe.recipe(), toolSlot, required <= 0 ? 1f : Math.min(1f, (float) progress / required));
        if (!isLive(head, current)) {
            return abandon();
        }
        if (progress < required) {
            return CraftingStep.CRAFT;
        }
        if (checkForItems(recipe.recipe()) != CraftingStep.CRAFT) {
            return CraftingStep.START_WORKING;
        }
        return afterRun(run.make(recipe, current, toolSlot));
    }

    /** MC craft's check: the head task is still the one under way, neither cancelled nor failed. */
    private static boolean isLive(Request head, Request current) {
        return head.token().equals(current.token())
                && head.state() != RequestState.CANCELLED
                && head.state() != RequestState.FAILED;
    }

    /**
     * MC craft's cancelled or failed task: the recipe is dropped and an action done, so the crafter dumps what it
     * holds. Deviation from MC: also when the task left the queue, and the counters are reset; MC then goes on with
     * the next task using the dropped task's recipe and counters, or stops without dumping when none is left.
     */
    private CraftingStep abandon() {
        currentRequest = null;
        currentRecipe = null;
        ctx.job().incrementActions(ctx.stock().actionsUntilDump()); // MC getActionRewardForCraftingSuccess
        resetValues();
        return CraftingStep.START_WORKING;
    }

    /** What the crafter does after a run (MC executeCraftingAction's ends, then finalizeCraftingTask). */
    private CraftingStep afterRun(CraftingRun.Outcome outcome) {
        return switch (outcome) {
            case NEXT_RUN -> {
                ctx.tasks().setProgress(0);
                yield CraftingStep.GET_RECIPE;
            }
            case DONE -> finalizeCraftingTask();
            case FAILED -> {
                currentRequest = null;
                ctx.tasks().finishRequest(ctx.colony(), false);
                ctx.job().incrementActionsAndDecSaturation(); // MC AbstractEntityAICrafting: the tool broke
                resetValues();
                yield CraftingStep.START_WORKING;
            }
        };
    }

    /**
     * MC finalizeCraftingTask: the recipe is done and the counters reset; the crafter earns half the task's runs as
     * experience (MC tests the dump due and the counters it just reset), then dumps, and finishes the task after it.
     */
    private CraftingStep finalizeCraftingTask() {
        currentRecipe = null;
        resetValues();
        Request current = currentRequest;
        if (inventoryNeedsDump() && current != null) {
            ctx.award(runsOf(current) / 2.0);
        }
        return CraftingStep.INVENTORY_FULL;
    }

    private static int runsOf(Request task) {
        return task.requestable() instanceof Crafting crafting ? crafting.count() : 0;
    }

    /** MC inventoryNeedsDump's own test (the AI adds the state ones): an action done, or a full inventory. */
    public boolean inventoryNeedsDump() {
        return ctx.stock().dumpDue(ctx.job().actionsDone());
    }

    /**
     * MC dumpInventory then afterDump: at the hut, stores what the crafter carries but what its hut keeps in a worker's
     * inventory (MC keepX: a crafted tool leaves unless the hut keeps its type), asking a courier only when
     * {@link #isAfterDumpPickupAllowed}; its actions start again from 0.
     */
    public CraftingStep dump() {
        if (!ctx.walkToWorkPos(ctx.hut().position())) {
            return CraftingStep.INVENTORY_FULL;
        }
        ctx.stock().dumpKeepingHutRules(isAfterDumpPickupAllowed());
        ctx.job().clearActions();
        return afterDump();
    }

    /**
     * MC afterDump: a task whose runs are all made is finished (RESOLVED, its items then go to the requester), for
     * half its runs as experience; the secondary outputs go to the nearest warehouse; then IDLE. Deviation from MC: the
     * task is finished only while it is still the head of the queue, which {@code finishRequest} finishes; MC finishes
     * the head whenever its current request is in progress, even when that head is another task.
     */
    public CraftingStep afterDump() {
        CraftingTasks tasks = ctx.tasks();
        Request current = currentRequest;
        if (tasks.maxCraftingCount() == 0 && tasks.progress() == 0 && tasks.craftCounter() == 0 && current != null) {
            boolean head = tasks.currentTask(ctx.colony())
                    .filter(h -> h.token().equals(current.token()))
                    .isPresent();
            if (head && current.state() == RequestState.IN_PROGRESS) {
                tasks.finishRequest(ctx.colony(), true);
                ctx.award(runsOf(current) / 2.0);
            }
            currentRequest = null;
            resetValues();
        }
        outputs.sendSecondaryOutputs();
        return CraftingStep.IDLE;
    }

    /** MC isAfterDumpPickupAllowed: no courier while a task is under way (its items wait for their delivery). */
    public boolean isAfterDumpPickupAllowed() {
        return currentRequest == null;
    }

    /** MC resetValues: the counters back to 0, empty hands, and the next recipe may dump once again. */
    public void resetValues() {
        ctx.tasks().setMaxCraftingCount(0);
        ctx.tasks().setProgress(0);
        ctx.tasks().setCraftCounter(0);
        hands.clear();
        dumped = false;
    }

    /** MC checkIfNeedsItem's own test (the AI adds the state ones): a request of this crafter is pending. */
    public boolean needsItem() {
        return ctx.requests().pending();
    }

    /**
     * MC waitForRequests: at the hut, takes what its completed requests brought; IDLE once none is left (MC
     * afterRequestPickUp).
     */
    public CraftingStep waitForRequests() {
        if (!ctx.requests().pending()) {
            return CraftingStep.IDLE;
        }
        if (!ctx.walkToWorkPos(ctx.hut().position())) {
            return CraftingStep.NEEDS_ITEM;
        }
        return ctx.requests().receiveAtHut() ? CraftingStep.NEEDS_ITEM : CraftingStep.IDLE;
    }
}
