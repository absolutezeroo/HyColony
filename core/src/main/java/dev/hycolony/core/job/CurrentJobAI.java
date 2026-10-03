package dev.hycolony.core.job;

import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.port.BodyId;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The job AI one citizen works with (MC IJob.getWorkerAI): made for its job and work hut, kept while it sleeps, eats
 * or idles, reset each time it starts working again (MC CitizenAI's WORK target calls resetAI), dropped with its job.
 */
public final class CurrentJobAI {
    private final Colony colony;
    private final CitizenData data;
    private final BodyId body;
    /** Told once a new AI is made (its vital signs time the new job step). */
    private final Runnable started;

    private @Nullable JobAI ai;
    /** The job and work building {@link #ai} was made for. */
    private @Nullable Job job;

    private @Nullable BlockPos workBuilding;
    /** Whether {@link #ai} was reset since the citizen last started working. */
    private boolean reset;

    public CurrentJobAI(Colony colony, CitizenData data, BodyId body, Runnable started) {
        this.colony = colony;
        this.data = data;
        this.body = body;
        this.started = started;
    }

    /** Its AI, while it holds one; null else. */
    public @Nullable JobAI ai() {
        return ai;
    }

    /** The job its AI was made for; null without an AI. */
    public @Nullable Job job() {
        return job;
    }

    /**
     * Its AI for {@code job}, made anew when it was hired for another job or hut (even between two ticks), at normal
     * speed: a courier hired for another job loses its Agility bonus (MC).
     */
    public JobAI forJob(Job job) {
        JobAI current = ai;
        if (current != null && job.equals(this.job) && Objects.equals(data.workBuilding(), workBuilding)) {
            return current;
        }
        colony.context().bodies().setMovementSpeed(body, 1);
        this.job = job;
        workBuilding = data.workBuilding();
        JobAI made = job.createAI(colony, body);
        ai = made;
        reset = false;
        started.run(); // once made: a failing createAI leaves the old AI counted as it was
        return made;
    }

    /** MC CitizenAI's WORK target: resets {@code current} on the first work tick since {@link #resetOnNextWork}. */
    public void working(JobAI current) {
        if (!reset) {
            current.resetAI();
            reset = true;
        }
    }

    /** Its AI is reset the next time it works: it left WORKING, or a command moved it. */
    public void resetOnNextWork() {
        reset = false;
    }

    /**
     * Drops its AI once its job is not the one the AI was made for: forgets it and its walking speed, as MC removes the
     * courier's speed modifier on unassignment (DeliverymanAssignmentModule). Nothing while it holds none.
     */
    public void dropIfJobLeft() {
        Job held = job;
        if (ai != null && (held == null || !data.job().filter(held::equals).isPresent())) {
            ai = null;
            job = null;
            colony.context().bodies().setMovementSpeed(body, 1);
        }
    }
}
