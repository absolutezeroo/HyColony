package dev.hycolony.core.logistics.courier;

import dev.hycolony.core.kernel.ai.IState;

/** The courier AI's states (MC AIWorkerState, as EntityAIWorkDeliveryman registers them). */
enum CourierState implements IState {
    IDLE,
    START_WORKING,
    PREPARE_DELIVERY,
    DELIVERY,
    PICKUP,
    DUMPING
}
