package dev.hycolony.core.job;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.module.AssignedCitizenModule;
import dev.hycolony.core.building.module.BuildingEventsModule;
import dev.hycolony.core.building.module.BuildingModule;
import dev.hycolony.core.building.module.PersistentModule;
import dev.hycolony.core.building.module.TickingModule;
import dev.hycolony.core.citizen.CitizenAI;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.Skill;
import dev.hycolony.core.citizen.inventory.EquipmentReturn;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.kernel.persist.SavedJson;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Assigns citizens to a job at a building. Port of MineColonies' WorkerBuildingModule.
 *
 * <p>Deviation from MC: WorkerBuildingModule.onUpgradeComplete is not ported. Its calculateMaxCitizens has no
 * counterpart (no housing capacity yet, see TownHallStats) and its model reset only concerns an assigned citizen
 * without a job.
 */
public final class WorkerModule
        implements PersistentModule, TickingModule, BuildingEventsModule, AssignedCitizenModule {
    private final JobType jobType;
    private final Skill primary;
    private final Skill secondary;
    private final int maxWorkers;
    private final boolean assignableAtLevel0;
    private final List<Integer> workers = new ArrayList<>();
    private HiringMode hiringMode = HiringMode.DEFAULT;
    /** MC canWorkingDuringRain; see {@link #workingInRain}. */
    private boolean workingInRain;

    public WorkerModule(JobType jobType, Skill primary, Skill secondary, int maxWorkers, boolean assignableAtLevel0) {
        this.jobType = jobType;
        this.primary = primary;
        this.secondary = secondary;
        this.maxWorkers = maxWorkers;
        this.assignableAtLevel0 = assignableAtLevel0;
    }

    /** The first worker of {@code b}'s worker module; empty for a hut without workers or module. */
    public static Optional<CitizenData> firstWorker(Colony colony, Building b) {
        return b.module(WorkerModule.class)
                .flatMap(w -> w.workers.stream().findFirst())
                .flatMap(colony.citizens()::get);
    }

    public List<Integer> workers() {
        return Collections.unmodifiableList(workers);
    }

    /** MC getMaxInhabitants: the places this module offers. */
    public int maxWorkers() {
        return maxWorkers;
    }

    public HiringMode hiringMode() {
        return hiringMode;
    }

    public void setHiringMode(HiringMode hiringMode) {
        this.hiringMode = hiringMode;
    }

    public Skill primary() {
        return primary;
    }

    public Skill secondary() {
        return secondary;
    }

    public JobType job() {
        return jobType;
    }

    /**
     * MC's {@code canWorkingDuringRain} flag, true for the dining hall's waiter: its workers work in the rain at any
     * level. Set once, while the hut type builds the module; returns this module.
     */
    public WorkerModule workingInRain() {
        workingInRain = true;
        return this;
    }

    /** MC canWorkDuringTheRain: the flag of {@link #workingInRain}, or a max-level hut. */
    public boolean canWorkDuringTheRain(Building b) {
        return workingInRain || b.level() >= b.type().maxLevel();
    }

    public boolean canAssignCitizens(Building b) {
        return HiringMode.canAssignCitizens(b, assignableAtLevel0);
    }

    /**
     * MC assignCitizen: gives the citizen this job at {@code b}, marks the colony dirty and tells the hut's
     * {@link HiringListener} modules (MC onAssignment); fails (returns false) when full, {@link #canAssignCitizens} is
     * false, or the citizen is already employed.
     */
    public boolean hire(Colony c, Building b, CitizenData citizen) {
        if (workers.size() >= maxWorkers
                || !canAssignCitizens(b)
                || citizen.job().isPresent()
                || citizen.workBuilding() != null) {
            return false;
        }
        citizen.setJob(jobType.factory().apply(citizen));
        citizen.setWorkBuilding(b.position());
        workers.add(citizen.id());
        c.markDirty();
        for (BuildingModule module : b.modules().values()) {
            if (module instanceof HiringListener listener) {
                listener.onWorkerHired(c, b);
            }
        }
        return true;
    }

    /** Load healing: drops worker ids that fail {@code keep}. Returns whether any was dropped. */
    public boolean retainWorkers(java.util.function.IntPredicate keep) {
        return workers.removeIf(id -> !keep.test(id));
    }

    /** MC removeCitizen: {@link #fire}s {@code citizenId}; false when it does not work here. */
    @Override
    public boolean removeCitizen(Colony c, Building b, int citizenId) {
        boolean works = workers.contains(citizenId);
        fire(c, b, citizenId);
        return works;
    }

    public void fire(Colony c, Building b, int citizenId) {
        if (!workers.remove(Integer.valueOf(citizenId))) {
            return;
        }
        c.requests().cancelAllFrom(b.requesterId(), citizenId); // MC: a leaving worker's requests go with it
        c.citizens().get(citizenId).ifPresent(citizen -> free(c, citizen));
        c.markDirty();
    }

    /**
     * {@code citizen} loses its job (MC AbstractJob.onRemoval, then the job and workplace cleared): the job lets go
     * of its tasks, its armour goes back to its inventory and its body's hands go empty, the held slots staying as MC
     * ({@link EquipmentReturn}); its AI drops its job AI and walking speed at once, as MC removes the courier's speed
     * modifier on unassignment (DeliverymanAssignmentModule).
     */
    public static void free(Colony c, CitizenData citizen) {
        citizen.job().ifPresent(job -> job.onRemoval(c));
        EquipmentReturn.onJobRemoved(c, citizen);
        citizen.setJob(null);
        citizen.setWorkBuilding(null);
        c.citizens().ai(citizen.id()).ifPresent(CitizenAI::jobLost);
    }

    /** MC AbstractAssignedCitizenModule.onDestroyed: fires every worker of the removed hut. */
    @Override
    public void onRemoved(Colony colony, Building building) {
        // Snapshot: fire() mutates workers, which this would otherwise iterate live.
        for (int citizenId : List.copyOf(workers)) {
            fire(colony, building, citizenId);
        }
    }

    /** MC WorkerBuildingModule.onColonyTick: auto-hires the first idle adult while there is room. */
    @Override
    public void onColonyTick(Colony colony, Building building) {
        if (workers.size() >= maxWorkers || !hiringMode.canAutoHire(colony, building, assignableAtLevel0)) {
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

    /** Tolerant (CLAUDE.md § 5): a non-number worker is skipped, an unknown or missing mode reads as DEFAULT. */
    @Override
    public void read(JsonObject in) {
        workers.clear();
        workers.addAll(SavedJson.ints(in.get("workers")));
        hiringMode = SavedJson.enumOf(HiringMode.class, in.get("hiringMode")).orElse(HiringMode.DEFAULT);
    }
}
