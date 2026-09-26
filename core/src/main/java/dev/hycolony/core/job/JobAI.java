package dev.hycolony.core.job;

import dev.hycolony.core.kernel.port.Msg;
import java.util.Optional;

/** A job's own behaviour, ticked while its citizen is WORKING. Manages its own cadence internally. */
public interface JobAI {
    void tick();

    String stateName();

    boolean canBeInterrupted();

    /** What the job is doing right now, as one translatable line (the citizen window); empty if nothing to say. */
    default Optional<Msg> describe() {
        return Optional.empty();
    }
}
