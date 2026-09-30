package dev.hycolony.core.job;

import dev.hycolony.core.kernel.port.Msg;
import dev.hycolony.core.request.model.RequestToken;
import java.util.List;
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

    /**
     * Whether it waits legitimately (for a task, or items it asked for), its step then lasting as long as it must: the
     * stale-step invariant skips it; false by default.
     */
    default boolean waiting() {
        return false;
    }

    /** Whether its current step works on the head of its {@link #queue()}; false by default. */
    default boolean servesQueueHead() {
        return false;
    }

    /** Its own task queue, head first, read-only; empty for a job without one. */
    default List<RequestToken> queue() {
        return List.of();
    }

    /** What the job is doing right now, as one translatable line (the citizen window); empty if nothing to say. */
    default Optional<Msg> describe() {
        return Optional.empty();
    }
}
