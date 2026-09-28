package dev.hycolony.core.job;

import com.google.gson.JsonObject;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.kernel.port.BodyId;

/** A citizen's occupation. Concrete jobs (builder, etc.) extend this from the construction task on. */
public abstract class Job {
    /** MC CitizenData.DISABLED: the inactivity timer is not running. */
    private static final int DISABLED = -1;

    private final JobType type;
    private final CitizenData citizen;
    private int actionsDone;
    private boolean working;
    private int inactivityTimer = DISABLED;

    protected Job(JobType type, CitizenData citizen) {
        this.type = type;
        this.citizen = citizen;
    }

    public JobType type() {
        return type;
    }

    public CitizenData citizen() {
        return citizen;
    }

    public abstract JobAI createAI(Colony colony, BodyId body);

    public int actionsDone() {
        return actionsDone;
    }

    public void incrementActions() {
        actionsDone++;
    }

    /** MC incrementActionsDone(n). */
    public void incrementActions(int n) {
        actionsDone += n;
    }

    public void clearActions() {
        actionsDone = 0;
    }

    public boolean isWorking() {
        return working;
    }

    /**
     * MC CitizenData.setWorking: starting work calls {@link #onActivityChange} with true and stops the inactivity
     * timer; stopping starts it. No effect when the state does not change.
     */
    public void setWorking(Colony colony, boolean isWorking) {
        if (isWorking && !working) {
            working = true;
            onActivityChange(colony, true);
            inactivityTimer = DISABLED;
        } else if (!isWorking && working) {
            inactivityTimer = 0;
            working = false;
        }
    }

    /**
     * MC CitizenData.update, every citizen-data update (60 ticks): once not working for {@link #inactivityLimit()}
     * updates, calls {@link #onActivityChange} with false, once.
     */
    public void tickInactivity(Colony colony) {
        if (!working && inactivityTimer != DISABLED && ++inactivityTimer >= inactivityLimit()) {
            onActivityChange(colony, false);
            inactivityTimer = DISABLED;
        }
    }

    /** MC IJob.getInactivityLimit: citizen-data updates without work before going inactive; -1 if not applicable. */
    protected int inactivityLimit() {
        return -1;
    }

    /** MC IJob.triggerActivityChangeAction: the citizen started working, or stayed idle too long. No-op by default. */
    protected void onActivityChange(Colony colony, boolean active) {}

    /** MC IJob.onRemoval: the job is being taken from its citizen. No-op by default. */
    public void onRemoval(Colony colony) {}

    /** Also saves whether the citizen is working (MC CitizenData TAG_ACTIVE); the inactivity timer restarts unset. */
    public JsonObject write() {
        JsonObject o = new JsonObject();
        o.addProperty("type", type.id());
        o.addProperty("actionsDone", actionsDone);
        o.addProperty("working", working);
        return o;
    }

    public void read(JsonObject o) {
        actionsDone = o.has("actionsDone") ? o.get("actionsDone").getAsInt() : 0;
        working = o.has("working") && o.get("working").getAsBoolean();
    }
}
