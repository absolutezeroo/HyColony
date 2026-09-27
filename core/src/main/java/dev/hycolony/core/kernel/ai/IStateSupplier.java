package dev.hycolony.core.kernel.ai;

import org.jspecify.annotations.Nullable;

/** Transition action. Returns the next state, or null to not transition. */
@FunctionalInterface
public interface IStateSupplier<S extends IState> {
    @Nullable
    S get();
}
