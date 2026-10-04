package dev.hycolony.core.job;

import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.CitizenState;
import dev.hycolony.core.citizen.vitals.AiWatch;
import dev.hycolony.core.citizen.vitals.WorkExit;
import dev.hycolony.core.citizen.wander.CitizenWander;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.kernel.port.Msg;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * A citizen's work in its AI (MC CitizenAI.registerWorkAI and calculateNextState's work part): when it should work,
 * and its job AI's tick while it works. Its job AI ({@link CurrentJobAI}) is kept while it sleeps, eats or idles.
 */
public final class CitizenWork {
    /** MC CitizenAI: decideAiTask runs every 10 ticks (an EVENT target); the work's stops are checked as often. */
    public static final int DECIDE_INTERVAL_TICKS = 10;

    private final Colony colony;
    private final CitizenData data;
    private final BodyId body;
    private final AiWatch watch;
    private final CitizenWander wander;
    private final CurrentJobAI jobAI;
    private final WorkStops stops;
    private int workTicks;

    public CitizenWork(Colony colony, CitizenData data, BodyId body, AiWatch watch, CitizenWander wander) {
        this.colony = colony;
        this.data = data;
        this.body = body;
        this.watch = watch;
        this.wander = wander;
        this.jobAI = new CurrentJobAI(colony, data, body, watch::jobStarted);
        this.stops = new WorkStops(colony, data);
    }

    /** MC CitizenAI.decideAiTask from IDLE: to work when it should; null to stay. */
    public @Nullable CitizenState idle() {
        return shouldWork() ? CitizenState.WORKING : null;
    }

    /**
     * One tick of WORKING (MC's WORKING target): its job AI ticks; IDLE once it has no job, or its job's stops say so
     * (checked every {@link #DECIDE_INTERVAL_TICKS}), the reason noted.
     */
    public @Nullable CitizenState work() {
        Job job = data.job().orElse(null);
        if (job == null) {
            jobAI.dropIfJobLeft();
            wander.restartWait();
            watch.leftWork(WorkExit.JOB_LOST);
            return CitizenState.IDLE;
        }
        JobAI ai = jobAI.forJob(job);
        jobAI.working(ai);
        // MC re-decides every DECIDE_INTERVAL_TICKS, which also keeps the order lookup off the per-tick path.
        Optional<WorkExit> exit = ++workTicks % DECIDE_INTERVAL_TICKS == 0 ? stops.exit(ai) : Optional.empty();
        if (exit.isPresent()) {
            wander.restartWait();
            watch.leftWork(exit.get());
            return CitizenState.IDLE;
        }
        ai.tick();
        return null;
    }

    /**
     * MC calculateNextState: work only when the rain does not stop it ({@link WorkStops#rainStopsWork}, first in MC),
     * the job AI cannot go idle and the citizen is not on a break ({@link WorkStops#onBreak}).
     */
    public boolean shouldWork() {
        Job job = data.job().orElse(null);
        if (job == null || !colony.context().bodies().isAlive(body) || stops.rainStopsWork()) {
            return false;
        }
        JobAI ai = jobAI.forJob(job);
        return !ai.canGoIdle() && !stops.onBreak(ai);
    }

    /** Whether its job AI, if any, may be interrupted (MC canAIBeInterrupted); true without one. */
    public boolean interruptible() {
        return jobAI.ai().map(JobAI::canBeInterrupted).orElse(true);
    }

    /** Its job AI is reset at its next work tick (a command moved it, or it left WORKING). */
    public void resetOnNextWork() {
        jobAI.resetOnNextWork();
    }

    /** It was just fired: its job AI and job speed go now. */
    public void jobLost() {
        jobAI.dropIfJobLeft();
    }

    /** Its job's AI, while it has a job. */
    public Optional<JobAI> ai() {
        return jobAI.ai();
    }

    /** The actions its current job counted (MC actionsDone); 0 without a job AI's job. */
    public int actionsDone() {
        return jobAI.job().map(Job::actionsDone).orElse(0);
    }

    /** Its job AI's own line. */
    public Optional<Msg> activity() {
        return jobAI.ai().flatMap(JobAI::describe);
    }

    /** The part its ticks are timed as: its job AI's job type id, else {@code "citizen"}; allocates nothing. */
    public String timingPart() {
        Job job = jobAI.job().orElse(null);
        return job == null ? "citizen" : job.type().id();
    }
}
