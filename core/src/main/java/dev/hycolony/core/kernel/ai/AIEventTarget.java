package dev.hycolony.core.kernel.ai;

import java.util.function.BooleanSupplier;

/** Transition not bound to a state, evaluated in its priority group every tick. */
public class AIEventTarget<S extends IState> extends TickingTransition<S> {
    private final AIBlockingEventType eventType;

    public AIEventTarget(AIBlockingEventType eventType, BooleanSupplier condition, IStateSupplier<S> action, int tickRate) {
        super(null, condition, action, tickRate);
        this.eventType = eventType;
    }

    public AIEventTarget(AIBlockingEventType eventType, IStateSupplier<S> action, int tickRate) {
        this(eventType, () -> true, action, tickRate);
    }

    @Override
    public IStateEventType getEventType() {
        return eventType;
    }
}
