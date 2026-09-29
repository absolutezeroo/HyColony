package dev.hycolony.core.job.work;

/**
 * The ticks a worker waits before its AI's next step (MC AbstractEntityAIBasic.setDelay and waitingForSomething):
 * while some remain, {@link WorkerMachine} keeps the AI in its state.
 */
public final class WorkDelay {
    private int ticks;

    /** MC setDelay: the AI does nothing for {@code ticks}. */
    public void set(int ticks) {
        this.ticks = ticks;
    }

    /** The ticks still to wait; 0 once the delay ran out. */
    public int remaining() {
        return ticks;
    }

    /** MC waitingForSomething: true while a delay runs, which then shrinks by {@code elapsed} ticks (never below 0). */
    public boolean waiting(int elapsed) {
        if (ticks <= 0) {
            return false;
        }
        ticks = Math.max(0, ticks - elapsed);
        return true;
    }
}
