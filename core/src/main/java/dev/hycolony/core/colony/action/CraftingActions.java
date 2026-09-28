package dev.hycolony.core.colony.action;

import dev.hycolony.core.colony.ColonyManager;
import dev.hycolony.core.colony.view.ColonyWindows;
import dev.hycolony.core.crafting.module.CraftingModule;
import dev.hycolony.core.crafting.module.CraftingModule.LearnRefusal;
import dev.hycolony.core.crafting.module.RecipeEdits;
import dev.hycolony.core.crafting.recipe.RecipeId;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.port.Msg;
import java.util.Optional;
import java.util.UUID;

/**
 * A crafting hut's Recipes tab buttons (MC AddRemoveRecipeMessage, ToggleRecipeMessage and
 * ChangeRecipePriorityMessage, checked as {@link RecipeEdits}): learn, remove, enable or disable, move. Each needs
 * MANAGE_HUTS (MC AbstractBuildingServerMessage) and a hut with a crafting module, then re-shows the hut's window.
 *
 * <p>They act on the hut's first crafting module: MC's messages name the module, but HyColony huts have one (MC's
 * smelting modules are not ported). Deviation from MC: no success or error sound.
 *
 * <p>Built by its caller, like {@code WandActions}, rather than reached through {@link ColonyManager}, which holds the
 * most methods allowed (PMD TooManyMethods); it keeps no state.
 */
public final class CraftingActions {
    private final ColonyManager manager;
    private final ColonyWindows windows;

    public CraftingActions(ColonyManager manager) {
        this.manager = manager;
        this.windows = manager.windows();
    }

    /** A managed hut and its crafting module. */
    private record Target(ManagedHut hut, CraftingModule module) {}

    /**
     * Learn: teaches the hut the recipe of a Recipes tab line and tells the player (MC MESSAGE_RECIPE_SAVED), or tells
     * why not. Deviation from MC: the refusal names its reason, where MC names the hut and both possible reasons.
     */
    public boolean learn(UUID player, BlockPos hutPos, String recipeId) {
        Target t = target(player, hutPos).orElse(null);
        if (t == null) {
            return false;
        }
        Optional<LearnRefusal> refused =
                RecipeEdits.learn(t.hut().colony(), t.hut().building(), t.module(), new RecipeId(recipeId), player);
        manager.context()
                .notifier()
                .send(
                        player,
                        refused.map(r -> Msg.of("hycolony.crafting.learnRefused", "%" + r.langKey()))
                                .orElseGet(() -> Msg.of("hycolony.crafting.learned")));
        show(t, player);
        return refused.isEmpty();
    }

    /** Remove: forgets a learnt recipe; false for a custom recipe or one not listed. */
    public boolean remove(UUID player, BlockPos hutPos, String recipeId) {
        Target t = target(player, hutPos).orElse(null);
        if (t == null) {
            return false;
        }
        boolean removed = RecipeEdits.remove(t.hut().colony(), t.module(), new RecipeId(recipeId));
        show(t, player);
        return removed;
    }

    /** Enable/Disable of the learnt recipe at {@code index}; false when refused (see {@link RecipeEdits#toggle}). */
    public boolean toggle(UUID player, BlockPos hutPos, int index) {
        Target t = target(player, hutPos).orElse(null);
        if (t == null) {
            return false;
        }
        boolean toggled = RecipeEdits.toggle(t.hut().colony(), t.hut().building(), t.module(), index);
        show(t, player);
        return toggled;
    }

    /** Up/Down of the learnt recipe at {@code index}, to the top or bottom on a {@code fullMove} (MC Shift). */
    public boolean move(UUID player, BlockPos hutPos, int index, boolean up, boolean fullMove) {
        Target t = target(player, hutPos).orElse(null);
        if (t == null) {
            return false;
        }
        boolean moved = RecipeEdits.move(t.hut().colony(), t.module(), index, up, fullMove);
        show(t, player);
        return moved;
    }

    private Optional<Target> target(UUID player, BlockPos hutPos) {
        return ManagedHut.find(manager, player, hutPos)
                .flatMap(h -> h.building().module(CraftingModule.class).map(m -> new Target(h, m)));
    }

    private void show(Target t, UUID player) {
        windows.showBuilding(t.hut().colony(), t.hut().building(), player);
    }
}
