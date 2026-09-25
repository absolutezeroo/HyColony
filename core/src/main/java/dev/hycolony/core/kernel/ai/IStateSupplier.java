package dev.hycolony.core.kernel.ai;

/** Transition action. Returns the next state, or null to not transition. */
@FunctionalInterface
public interface IStateSupplier<S extends IState> {
    S get();
}
