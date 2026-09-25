package dev.hycolony.core.job;

/** A job's own behaviour, ticked while its citizen is WORKING. Manages its own cadence internally. */
public interface JobAI {
    void tick();

    String stateName();

    boolean canBeInterrupted();
}
