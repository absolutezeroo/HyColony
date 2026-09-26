package dev.hycolony.core.kernel.ai;

import java.util.function.BooleanSupplier;

public class AITarget<S extends IState> extends TickingTransition<S> {
    public AITarget(S state, BooleanSupplier condition, IStateSupplier<S> action, int tickRate) {
        super(state, condition, action, tickRate);
    }

    public AITarget(S state, IStateSupplier<S> action, int tickRate) {
        this(state, () -> true, action, tickRate);
    }

    public AITarget(S state, S next, int tickRate) {
        this(state, () -> true, () -> next, tickRate);
    }

    /** Runs {@code task} every {@code tickRate} ticks while in {@code state}, never changing the state. */
    public static <S extends IState> AITarget<S> every(S state, Runnable task, int tickRate) {
        return new AITarget<S>(
                state,
                (IStateSupplier<S>) () -> {
                    task.run();
                    return null;
                },
                tickRate);
    }
}
