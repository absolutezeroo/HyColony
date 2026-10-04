package dev.hycolony.core.citizen.vitals;

import dev.hycolony.core.citizen.CitizenState;
import dev.hycolony.core.citizen.hurt.HurtMemory;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * A citizen's vital signs: for diagnostics, what its AI did last, its {@link #walks()}, and its {@link #history()}
 * while tracked, kept up to date in place (never allocated per tick) by the features themselves, not through the event
 * bus; for its healing, its {@link #hurtMemory()}. Runtime only, never saved.
 */
public final class CitizenVitals {
    private @Nullable CitizenState aiState;
    private long aiStateSince;
    private @Nullable String jobStep;
    private long jobStepSince;
    private int aiFailures;
    private int jobFailures;
    private long lastActionTick;
    private final WalkVitals walks = new WalkVitals();
    private final HurtMemory hurtMemory = new HurtMemory();
    private @Nullable CitizenHistory history;

    /** The citizen AI's state; empty before its AI was made. */
    public Optional<CitizenState> aiState() {
        return Optional.ofNullable(aiState);
    }

    /** The tick its AI entered its state. */
    public long aiStateSince() {
        return aiStateSince;
    }

    /** What its job's AI is doing ({@code JobAI.stateName()}); empty while it has no job AI. */
    public Optional<String> jobStep() {
        return Optional.ofNullable(jobStep);
    }

    /** The tick its job step last changed, or its job AI started. */
    public long jobStepSince() {
        return jobStepSince;
    }

    /** Exceptions its citizen AI caught. */
    public int aiFailures() {
        return aiFailures;
    }

    /** Exceptions its job AIs caught, all of them since the citizen was loaded. */
    public int jobFailures() {
        return jobFailures;
    }

    /** The tick its job last counted an action (MC {@code actionsDone}): a block placed, a cell hoed, a craft. */
    public long lastActionTick() {
        return lastActionTick;
    }

    /** Its walks: the last one started and ended, and the stuck handler's last action. */
    public WalkVitals walks() {
        return walks;
    }

    /** Its last attacker, which stops its healing. */
    public HurtMemory hurtMemory() {
        return hurtMemory;
    }

    /**
     * Keeps this citizen's {@link #history()} until the returned tracking closes; world thread. The history starts
     * empty unless another tracking still holds it.
     */
    public CitizenHistory.Tracking track() {
        CitizenHistory h = keepsHistory() ? history : null;
        if (h == null) {
            h = new CitizenHistory();
            history = h;
        }
        return h.track();
    }

    /** Its last transitions, walk ends and stuck actions, oldest first; empty while no tracking is open. */
    public List<HistoryEntry> history() {
        return keepsHistory() && history != null ? history.entries() : List.of();
    }

    /** Whether a tracking is open; drops the history once all are closed, so an untracked citizen holds none. */
    boolean keepsHistory() {
        if (history != null && !history.tracked()) {
            history = null;
        }
        return history != null;
    }

    /** Notes {@code entry} in its history; the caller builds it only while {@link #keepsHistory()}. */
    void note(HistoryEntry entry) {
        if (history != null) {
            history.add(entry);
        }
    }

    /** The AI state without an Optional: the watch reads it each tick. */
    @Nullable
    CitizenState rawAiState() {
        return aiState;
    }

    /** The job step without an Optional: the watch reads it each tick. */
    @Nullable
    String rawJobStep() {
        return jobStep;
    }

    void aiState(CitizenState state, long tick) {
        aiState = state;
        aiStateSince = tick;
    }

    void jobStep(@Nullable String step, long tick) {
        jobStep = step;
        jobStepSince = tick;
    }

    void aiFailed() {
        aiFailures++;
    }

    void jobFailed(int more) {
        jobFailures += more;
    }

    void acted(long tick) {
        lastActionTick = tick;
    }
}
