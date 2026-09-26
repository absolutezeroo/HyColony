package dev.hycolony.core.kernel.ai;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/** A transition evaluated every {@code tickRate} machine ticks. Port of MineColonies' TickingTransition. */
public class TickingTransition<S extends IState> {
    /** MC TickRateConstants.MAX_AI_TICKRATE, in ticks (not MAX_TICKRATE, the colony's 500-tick slow tick). */
    public static final int MAX_AI_TICKRATE = 20 * 60 * 10;
    /** MC TickRateConstants.MAX_TICKRATE_VARIANT, in ticks. */
    public static final int MAX_TICKRATE_VARIANT = 50;

    /** Spreads transitions across ticks. Shared by all worlds, hence atomic. */
    private static final AtomicInteger OFFSET_VARIANT = new AtomicInteger();

    private final S state;
    private final BooleanSupplier condition;
    private final IStateSupplier<S> nextState;
    private final int tickRate;
    private int ticksToUpdate;

    protected TickingTransition(S state, BooleanSupplier condition, IStateSupplier<S> nextState, int tickRate) {
        this.state = state;
        this.condition = condition;
        this.nextState = nextState;
        this.tickRate = Math.max(1, Math.min(tickRate, MAX_AI_TICKRATE));
        int variant = OFFSET_VARIANT.getAndUpdate(v -> v + 1 >= MAX_TICKRATE_VARIANT ? 0 : v + 1);
        this.ticksToUpdate = variant % this.tickRate;
    }

    /** Test hook: makes offsets deterministic. */
    static void resetOffsetVariant() {
        OFFSET_VARIANT.set(0);
    }

    public S getState() {
        return state;
    }

    /** Null for plain state transitions. */
    public IStateEventType getEventType() {
        return null;
    }

    public boolean isOneTime() {
        return false;
    }

    public int getTickRate() {
        return tickRate;
    }

    boolean checkCondition() {
        return condition.getAsBoolean();
    }

    S getNextState() {
        return nextState.get();
    }

    int countdownTicksToUpdate(int reduction) {
        return ticksToUpdate -= reduction;
    }

    void setTicksToUpdate(int ticksToUpdate) {
        this.ticksToUpdate = ticksToUpdate;
    }
}
