package dev.hycolony.core.construction.builder;

import dev.hycolony.core.kernel.ai.IState;

/** The builder AI's states (MineColonies AIWorkerState subset). */
public enum BuilderState implements IState {
    IDLE,
    START_WORKING,
    LOAD_STRUCTURE,
    GATHERING_REQUIRED_MATERIALS,
    NEEDS_ITEM,
    BUILDING_STEP,
    MINE_BLOCK,
    INVENTORY_FULL,
    COMPLETE_BUILD
}
