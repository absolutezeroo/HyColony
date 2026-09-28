package dev.hycolony.core.crafting.job;

import dev.hycolony.core.kernel.ai.IState;

/**
 * The states of a crafter's AI (MC AIWorkerState, the ones AbstractEntityAICrafting and AbstractEntityAIBasic use): a
 * {@link CraftingWork} step returns the next one, and a concrete crafter AI registers a target per state.
 */
public enum CraftingStep implements IState {
    IDLE,
    START_WORKING,
    GET_RECIPE,
    QUERY_ITEMS,
    GATHERING_REQUIRED_MATERIALS,
    CRAFT,
    INVENTORY_FULL,
    NEEDS_ITEM;

    /** MC AIWorkerState.isOkayToEat: the worker may be interrupted in any step but the dump. */
    public boolean isOkayToEat() {
        return this != INVENTORY_FULL;
    }
}
