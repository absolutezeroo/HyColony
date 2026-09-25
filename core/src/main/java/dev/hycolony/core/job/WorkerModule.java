package dev.hycolony.core.job;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.BuildingEventsModule;
import dev.hycolony.core.building.PersistentModule;
import dev.hycolony.core.building.TickingModule;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.Skill;
import dev.hycolony.core.colony.Colony;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Assigns citizens to a job at a building. Port of MineColonies' WorkerBuildingModule. */
public final class WorkerModule implements PersistentModule, TickingModule, BuildingEventsModule {
    private final JobType jobType;
    private final Skill primary;
    private final Skill secondary;
    private final int maxWorkers;
    private final boolean assignableAtLevel0;
    private final List<Integer> workers = new ArrayList<>();
    private HiringMode hiringMode = HiringMode.DEFAULT;

    public WorkerModule(JobType jobType, Skill primary, Skill secondary, int maxWorkers, boolean assignableAtLevel0) {
        this.jobType = jobType;
        this.primary = primary;
        this.secondary = secondary;
        this.maxWorkers = maxWorkers;
        this.assignableAtLevel0 = assignableAtLevel0;
    }

    public List<Integer> workers() { return Collections.unmodifiableList(workers); }
    public HiringMode hiringMode() { return hiringMode; }
    public void setHiringMode(HiringMode hiringMode) { this.hiringMode = hiringMode; }
    public Skill primary() { return primary; }
    public Skill secondary() { return secondary; }
    public JobType job() { return jobType; }

    public boolean canAssignCitizens(Building b) {
        return assignableAtLevel0 || (b.level() > 0 && b.isBuilt());
    }

    /** Fails (returns false) when full, {@link #canAssignCitizens} is false, or the citizen is already employed. */
    public boolean hire(Colony c, Building b, CitizenData citizen) {
        if (workers.size() >= maxWorkers || !canAssignCitizens(b)
                || citizen.job().isPresent() || citizen.workBuilding() != null) {
            return false;
        }
        citizen.setJob(jobType.factory().apply(citizen));
        citizen.setWorkBuilding(b.position());
        workers.add(citizen.id());
        c.markDirty();
        return true;
    }

    public void fire(Colony c, Building b, int citizenId) {
        if (!workers.remove(Integer.valueOf(citizenId))) {
            return;
        }
        c.citizens().get(citizenId).ifPresent(citizen -> {
            citizen.setJob(null);
            citizen.setWorkBuilding(null);
        });
        c.markDirty();
    }

    /** Auto-hiring needs the colony (its citizens and settings): see the two-arg overload. */
    @Override
    public void onColonyTick(Building building) {}

    @Override
    public void onColonyTick(Colony colony, Building building) {
        if (workers.size() >= maxWorkers || !canAssignCitizens(building)) {
            return;
        }
        boolean autoHire = hiringMode == HiringMode.AUTO
                || (hiringMode == HiringMode.DEFAULT && colony.settings().autoHiring());
        if (!autoHire) {
            return;
        }
        for (CitizenData citizen : colony.citizens().all()) {
            if (!citizen.isChild() && citizen.workBuilding() == null) {
                hire(colony, building, citizen);
                return;
            }
        }
    }

    @Override
    public void write(JsonObject out) {
        JsonArray arr = new JsonArray();
        workers.forEach(arr::add);
        out.add("workers", arr);
        out.addProperty("hiringMode", hiringMode.name());
    }

    @Override
    public void read(JsonObject in) {
        workers.clear();
        if (in.has("workers")) {
            in.getAsJsonArray("workers").forEach(e -> workers.add(e.getAsInt()));
        }
        if (in.has("hiringMode")) {
            hiringMode = HiringMode.valueOf(in.get("hiringMode").getAsString());
        }
    }
}
