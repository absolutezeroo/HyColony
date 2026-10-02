package dev.hycolony.core.crafting.job;

import dev.hycolony.core.crafting.recipe.BenchRequirement;
import dev.hycolony.core.crafting.recipe.Ingredient;
import dev.hycolony.core.crafting.recipe.Recipe;
import dev.hycolony.core.crafting.recipe.RecipeMatching;
import dev.hycolony.core.job.work.WorkerHands;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.item.Workstation;
import dev.hycolony.core.kernel.port.BodyAnimation;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * What a crafter visibly does while it crafts (MC AbstractEntityAICrafting.craft's held items and
 * hitBlockWithToolInHand): it stands at the recipe's bench, holds its tool or one of the ingredients, and hits the
 * bench. Deviation from MC: the crafter works at the recipe's bench, the first the hut registered for it, or at the hut
 * block for a Fieldcraft recipe, where MC walks to one of the schematic's work spots and hits the hut block; and it
 * holds one item, the body having one hand for it, where MC fills both hands.
 */
final class CrafterHands {
    private final CraftingWorkContext ctx;
    private final WorkerHands hands;

    CrafterHands(CraftingWorkContext ctx) {
        this.ctx = ctx;
        this.hands = new WorkerHands(ctx.colony().context().bodies(), ctx.body(), ctx.citizen());
    }

    /** MC walkToTaggedWorkPos: true once at the recipe's bench, or at the hut (walkToBuilding) without one. */
    boolean walkToWork(Recipe recipe) {
        BlockPos at = workBlock(recipe);
        return at.equals(ctx.hut().position()) ? ctx.walkToHut() : ctx.walkToWorkPos(at);
    }

    /**
     * MC craft's hit: the tool in {@code toolSlot} in hand (MC also puts an ingredient in the other hand), else one of
     * the ingredients at random (MC also puts the output in the other hand); then the crafter faces the bench, swings
     * and hits it, {@code done} of the run made (0 to 1).
     */
    void hit(Recipe recipe, OptionalInt toolSlot, float done) {
        BlockPos at = workBlock(recipe);
        Optional<ItemKey> tool = toolSlot.isPresent()
                ? ctx.stock().inventory().slot(toolSlot.getAsInt()).map(ItemAmount::item)
                : Optional.empty();
        hands.hold(tool.or(() -> anIngredient(recipe)));
        hands.face(at);
        hands.swing(BodyAnimation.BUILD);
        ctx.colony().context().ports().effects().blockHit(at, done);
    }

    /** MC resetValues: empty hands. */
    void clear() {
        hands.hold(Optional.empty());
    }

    /** The first bench the hut registered with the recipe's bench id and at least its tier; else the hut block. */
    BlockPos workBlock(Recipe recipe) {
        BenchRequirement needed = recipe.bench();
        if (!needed.isFieldcraft()) {
            for (Map.Entry<BlockPos, Workstation> e :
                    ctx.hut().registeredBlocks().workstations().entrySet()) {
                if (e.getValue().benchId().equals(needed.benchId())
                        && e.getValue().tier() >= needed.requiredTier()) {
                    return e.getKey();
                }
            }
        }
        return ctx.hut().position();
    }

    /** MC {@code getCleanedInput().get(random.nextInt(size))}: an item one of the ingredients accepts. */
    private Optional<ItemKey> anIngredient(Recipe recipe) {
        List<Ingredient> inputs = recipe.cleanedInput();
        if (inputs.isEmpty()) {
            return Optional.empty();
        }
        Ingredient in = inputs.get(ctx.colony().context().random().nextInt(inputs.size()));
        return RecipeMatching.items(in, ctx.recipes()).stream().findFirst();
    }
}
