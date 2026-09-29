package dev.hycolony.core.job;

/**
 * The AI of a worker whose hut (or the hut's module its work needs) is missing: it never works and lets its citizen
 * idle (MC's AI then waits for a building). A new AI is made once the citizen's work building changes.
 */
public final class IdleAI implements JobAI {
    @Override
    public void tick() {}

    @Override
    public String stateName() {
        return "IDLE";
    }

    @Override
    public boolean canBeInterrupted() {
        return true;
    }

    @Override
    public boolean canGoIdle() {
        return true;
    }
}
