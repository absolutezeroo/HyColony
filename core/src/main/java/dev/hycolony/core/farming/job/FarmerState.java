package dev.hycolony.core.farming.job;

import dev.hycolony.core.crafting.job.CraftingStep;
import dev.hycolony.core.kernel.ai.IState;

/**
 * The states of the farmer's AI (MC AIWorkerState, those EntityAIWorkFarmer and its crafter base use). The farming
 * ones come from {@link FarmWork}, the crafting ones from {@code CraftingWork} through {@link #of(CraftingStep)}.
 */
public enum FarmerState implements IState {
    IDLE,
    START_WORKING,
    PREPARING,
    FARMER_HOE,
    FARMER_PLANT,
    FARMER_HARVEST,
    GET_RECIPE,
    QUERY_ITEMS,
    GATHERING_REQUIRED_MATERIALS,
    CRAFT,
    INVENTORY_FULL,
    NEEDS_ITEM;

    /** MC AIWorkerState.isOkayToEat: every farmer state may be interrupted but the dump. */
    public boolean isOkayToEat() {
        return this != INVENTORY_FULL;
    }

    /** The farmer state of a crafting step (same name). */
    public static FarmerState of(CraftingStep step) {
        return valueOf(step.name());
    }
}
