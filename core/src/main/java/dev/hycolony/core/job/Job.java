package dev.hycolony.core.job;

import com.google.gson.JsonObject;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.kernel.port.BodyId;

/** A citizen's occupation. Concrete jobs (builder, etc.) extend this from the construction task on. */
public abstract class Job {
    private final JobType type;
    private final CitizenData citizen;
    private int actionsDone;

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

    public void clearActions() {
        actionsDone = 0;
    }

    public JsonObject write() {
        JsonObject o = new JsonObject();
        o.addProperty("type", type.id());
        o.addProperty("actionsDone", actionsDone);
        return o;
    }

    public void read(JsonObject o) {
        actionsDone = o.has("actionsDone") ? o.get("actionsDone").getAsInt() : 0;
    }
}
