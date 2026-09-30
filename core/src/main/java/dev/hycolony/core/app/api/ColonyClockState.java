package dev.hycolony.core.app.api;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;

/**
 * One world's colony clock as a debugging tool drives it (spec 2026-09-30, § 5): paused for an owner, stepped a few
 * core ticks at a time, resumed. Every call comes on the world's thread but {@link #release}, which a plugin's shutdown
 * makes from its own thread: it only leaves a note, read at the world's next tick. An owner released may never pause
 * again, whatever order its pause and its release reach the world in: owners are keys of one plugin instance. No MC
 * source (MC has no pause). Never saved.
 */
public final class ColonyClockState {
    /** The core ticks one step may run at once: as many as the tick system catches up after a stall. */
    public static final int MAX_STEP = 10;

    private @Nullable String owner;
    private int steps;
    /** The owners that stopped: one key per plugin instance, so it only grows with plugins stopped. */
    private final Set<String> released = ConcurrentHashMap.newKeySet();

    /**
     * Pauses the world's colonies for {@code by}; false while another owner holds the pause, or once {@code by}
     * stopped.
     */
    public boolean pause(String by) {
        applyReleases();
        if (released.contains(by) || (owner != null && !owner.equals(by))) {
            return false;
        }
        owner = by;
        return true;
    }

    /** Resumes them, whoever paused them; the steps not run yet are dropped. */
    public void resume() {
        owner = null;
        steps = 0;
    }

    /**
     * While paused, lets {@code n} more core ticks run at the next server tick, {@link #MAX_STEP} pending at most;
     * false while running.
     */
    public boolean step(int n) {
        applyReleases();
        if (owner == null) {
            return false;
        }
        steps = Math.min(MAX_STEP, steps + Math.max(0, n));
        return true;
    }

    /** Whether the world's colonies are paused. */
    public boolean paused() {
        applyReleases();
        return owner != null;
    }

    /** Who paused them; empty while running. */
    public Optional<String> owner() {
        applyReleases();
        return Optional.ofNullable(owner);
    }

    /** {@code by} stopped: its pause ends at the world's next tick, and it may not pause again. Any thread. */
    public void release(String by) {
        released.add(by);
    }

    /**
     * Each server tick: of the {@code due} core ticks the time elapsed calls for, how many run. All of them while
     * running; while paused, the steps asked for, then none.
     */
    public int allow(int due) {
        applyReleases();
        if (owner == null) {
            return due;
        }
        int n = steps;
        steps = 0;
        return n;
    }

    /** Lifts the pause of an owner that stopped. */
    private void applyReleases() {
        if (owner != null && released.contains(owner)) {
            resume();
        }
    }
}
