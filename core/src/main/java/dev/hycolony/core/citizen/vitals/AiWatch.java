package dev.hycolony.core.citizen.vitals;

import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.CitizenState;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.job.JobAI;
import dev.hycolony.core.kernel.event.EventBus;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Watches one citizen's AI after each of its ticks: a new AI state, a new job step or a new job failure goes to its
 * {@link CitizenVitals}, and is posted as a {@link CitizenDebugEvents} only while someone listens. Comparing the job
 * step's name each tick allocates nothing: job AIs name their step by its enum constant.
 */
public final class AiWatch {
    private final Colony colony;
    private final CitizenData citizen;
    /** The failures the current job AI had when last watched. */
    private int seenFailures;

    public AiWatch(Colony colony, CitizenData citizen) {
        this.colony = colony;
        this.citizen = citizen;
    }

    /** After an AI tick: notes {@code state}, and {@code job}'s step and failures when it has a job AI. */
    public void afterTick(CitizenState state, @Nullable JobAI job) {
        CitizenVitals v = citizen.vitals();
        CitizenState before = v.rawAiState();
        if (state != before) {
            v.aiState(state, colony.context().clock().currentTick());
            EventBus bus = colony.context().bus();
            if (before != null && bus.hasListeners(CitizenDebugEvents.AiStateChanged.class)) {
                bus.post(new CitizenDebugEvents.AiStateChanged(colony, citizen, before, state));
            }
        }
        watchJob(v, job);
    }

    /**
     * A new job AI started: its failures count from 0, the old AI's step ends (posted as a change to ""), and the new
     * step is timed from now even under the name its predecessor had (the next tick notes it).
     */
    public void jobStarted() {
        seenFailures = 0;
        CitizenVitals v = citizen.vitals();
        String before = v.rawJobStep();
        v.jobStep(null, colony.context().clock().currentTick());
        EventBus bus = colony.context().bus();
        if (before != null && bus.hasListeners(CitizenDebugEvents.JobStepChanged.class)) {
            bus.post(new CitizenDebugEvents.JobStepChanged(colony, citizen, before, ""));
        }
    }

    /** The citizen AI caught an exception: counts it in its vital signs. */
    public void failed() {
        citizen.vitals().aiFailed();
    }

    private void watchJob(CitizenVitals v, @Nullable JobAI job) {
        String step = job == null ? null : job.stateName();
        String before = v.rawJobStep();
        if (!Objects.equals(step, before)) {
            v.jobStep(step, colony.context().clock().currentTick());
            EventBus bus = colony.context().bus();
            if (bus.hasListeners(CitizenDebugEvents.JobStepChanged.class)) {
                bus.post(new CitizenDebugEvents.JobStepChanged(
                        colony, citizen, before == null ? "" : before, step == null ? "" : step));
            }
        }
        if (job != null && job.failures() > seenFailures) {
            v.jobFailed(job.failures() - seenFailures);
            seenFailures = job.failures();
        }
    }
}
