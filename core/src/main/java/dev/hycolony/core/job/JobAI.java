package dev.hycolony.core.job;

import dev.hycolony.core.kernel.port.Msg;
import java.util.Optional;

/** A job's own behaviour, ticked while its citizen is WORKING. Manages its own cadence internally. */
public interface JobAI {
    void tick();

    String stateName();

    boolean canBeInterrupted();

    /** MC AbstractEntityAIBasic.canGoIdle: true when the worker has nothing to do and may wander; false by default. */
    default boolean canGoIdle() {
        return false;
    }

    /** What the job is doing right now, as one translatable line (the citizen window); empty if nothing to say. */
    default Optional<Msg> describe() {
        return Optional.empty();
    }
}
