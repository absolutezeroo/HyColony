package dev.hycolony.core.job;

import dev.hycolony.core.kernel.port.Msg;
import java.util.Optional;

/** A job's own behaviour, ticked while its citizen is WORKING. Manages its own cadence internally. */
public interface JobAI {
    void tick();

    /**
     * What the job is doing, as the name of its machine's state (an enum constant's name): read each tick by the
     * citizen's vital signs, it must not allocate.
     */
    String stateName();

    boolean canBeInterrupted();

    /** MC AbstractEntityAIBasic.canGoIdle: true when the worker has nothing to do and may wander; false by default. */
    default boolean canGoIdle() {
        return false;
    }

    /**
     * How many exceptions its machine caught so far, for diagnostics (MC AbstractEntityAIBasic.onException doubles a
     * pause instead of counting); 0 by default.
     */
    default int failures() {
        return 0;
    }

    /** What the job is doing right now, as one translatable line (the citizen window); empty if nothing to say. */
    default Optional<Msg> describe() {
        return Optional.empty();
    }
}
