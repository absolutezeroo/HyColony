package dev.hycolony.core.crafting.restaurant;

import dev.hycolony.core.kernel.ai.IState;

/** The states of the waiter's AI (MC AIWorkerState, those AbstractEntityAIUsesFurnace and EntityAIWorkCook use). */
public enum CookState implements IState {
    IDLE,
    START_WORKING,
    FILL_UP_FURNACES,
    RETRIEVING_END_PRODUCT_FROM_FURNACE,
    RETRIEVING_USED_FUEL_FROM_FURNACE,
    GATHERING_REQUIRED_MATERIALS,
    COOK_SERVE_FOOD_TO_CITIZEN,
    COOK_SERVE_FOOD_TO_PLAYER,
    INVENTORY_FULL;

    /** MC AIWorkerState.isOkayToEat: every waiter state may be interrupted but the dump. */
    public boolean isOkayToEat() {
        return this != INVENTORY_FULL;
    }
}
