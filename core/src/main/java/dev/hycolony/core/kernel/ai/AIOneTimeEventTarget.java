package dev.hycolony.core.kernel.ai;

import java.util.function.BooleanSupplier;

/**
 * MC AIOneTimeEventTarget: an EVENT transition removed after it returns a state, unless it overrides
 * {@link #shouldRemove} to last until done (MC CommandCitizenTriggerWalkTo).
 */
public class AIOneTimeEventTarget<S extends IState> extends AIEventTarget<S> {
    public AIOneTimeEventTarget(BooleanSupplier condition, IStateSupplier<S> action) {
        super(AIBlockingEventType.EVENT, condition, action, 1);
    }

    public AIOneTimeEventTarget(IStateSupplier<S> action) {
        this(() -> true, action);
    }

    @Override
    public boolean shouldRemove() {
        return true;
    }
}
