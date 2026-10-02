package dev.hycolony.core.crafting.job;

import dev.hycolony.core.crafting.module.CraftingModules;
import dev.hycolony.core.crafting.module.RecipeChoice.Chosen;
import dev.hycolony.core.crafting.module.RecipeImprovement;
import dev.hycolony.core.crafting.module.RecipeImprovement.Crafted;
import dev.hycolony.core.crafting.task.CraftingTasks;
import dev.hycolony.core.kernel.item.Inventory;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.request.Request;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * One run of the recipe under way (MC AbstractEntityAICrafting.executeCraftingAction): the ingredients in the
 * crafter's inventory become the outputs ({@link RecipeExecution#craftOnce}), which go where they belong
 * ({@link CraftedOutputs#route}); the tool wears; the last run of the batch may improve the recipe. MC's building
 * statistics are not ported.
 */
final class CraftingRun {
    /** How the run ended: another run to make, the batch done, or the task failed (run unmade, tool broken). */
    enum Outcome {
        NEXT_RUN,
        DONE,
        FAILED
    }

    private final CraftingWorkContext ctx;
    private final CraftedOutputs outputs;

    CraftingRun(CraftingWorkContext ctx, CraftedOutputs outputs) {
        this.ctx = ctx;
        this.outputs = outputs;
    }

    /**
     * MC executeCraftingAction for {@code task}, with the tool in {@code toolSlot} if the recipe needs one: FAILED if
     * the run cannot be made (MC's success reward, which makes the crafter dump) or the tool broke before the batch's
     * last run (one action and hunger); DONE after the batch's last run, the success reward earned and the recipe
     * maybe improved; else NEXT_RUN.
     */
    Outcome make(Chosen recipe, Request task, OptionalInt toolSlot) {
        Optional<List<ItemAmount>> added =
                RecipeExecution.craftOnce(recipe.recipe(), ctx.stock().inventory(), ctx.recipes(), ctx.items());
        if (added.isEmpty()) {
            ctx.job().incrementActions(ctx.stock().actionsUntilDump()); // MC getActionRewardForCraftingSuccess
            return Outcome.FAILED;
        }
        outputs.route(recipe.recipe(), added.get(), task);
        CraftingTasks tasks = ctx.tasks();
        tasks.setCraftCounter(tasks.craftCounter() + 1);
        boolean broke = toolSlot.isPresent() && wear(toolSlot.getAsInt());
        if (tasks.craftCounter() >= tasks.maxCraftingCount()) {
            ctx.job().incrementActions(ctx.stock().actionsUntilDump()); // MC getActionRewardForCraftingSuccess
            improve(recipe);
            return Outcome.DONE;
        }
        if (broke) {
            ctx.job().incrementActionsAndDecSaturation(); // MC incrementActionsDoneAndDecSaturation
            return Outcome.FAILED;
        }
        return Outcome.NEXT_RUN;
    }

    /**
     * MC CitizenItemUtils.damageItemInHand(1): wears the tool in {@code slot}; true when that broke it. Deviation from
     * MC: no research yet, so no TOOL_DURABILITY chance to spare the tool.
     */
    private boolean wear(int slot) {
        Inventory inventory = ctx.stock().inventory();
        return inventory
                .slot(slot)
                .map(tool -> inventory.damage(slot, 1, ctx.items().durability(tool.item())))
                .orElse(false);
    }

    /** MC improveRecipe, by the crafting module holding the recipe, for the runs made. */
    private void improve(Chosen recipe) {
        CraftingModules.holding(ctx.hut(), recipe.id())
                .ifPresent(module -> RecipeImprovement.improve(
                        ctx.colony(),
                        ctx.hut(),
                        module,
                        new Crafted(
                                recipe.id(),
                                ctx.tasks().craftCounter(),
                                ctx.citizen(),
                                ctx.skills().improvement())));
    }
}
