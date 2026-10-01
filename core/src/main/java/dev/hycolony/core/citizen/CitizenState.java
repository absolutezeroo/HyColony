package dev.hycolony.core.citizen;

import dev.hycolony.core.kernel.ai.IState;

/** The subset of MineColonies' CitizenAIState ported so far: IDLE (wandering included, as in MC), WORK and SLEEP. */
public enum CitizenState implements IState {
    IDLE,
    WORKING,
    SLEEP
}
