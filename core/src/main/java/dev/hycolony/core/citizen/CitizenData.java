package dev.hycolony.core.citizen;

import com.google.gson.JsonObject;
import dev.hycolony.core.citizen.food.CitizenHunger;
import dev.hycolony.core.citizen.happiness.CitizenHappiness;
import dev.hycolony.core.citizen.inventory.CitizenEquipment;
import dev.hycolony.core.citizen.vitals.CitizenVitals;
import dev.hycolony.core.job.Job;
import dev.hycolony.core.job.JobStatus;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.item.Inventory;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Persistent citizen state. The in-world body is disposable and rebuilt from this. */
public final class CitizenData {
    public static final double MAX_SATURATION = 60;
    /**
     * MC InventoryCitizen.DEFAULT_INV_SIZE. Deviation from MC: always 27; MC's research effect CITIZEN_INV_SLOTS adds
     * 9, 18 or 27 slots, and HyColony has no research yet.
     */
    public static final int INVENTORY_SLOTS = 27;
    /** MC CitizenData.update: a leisure break lasts 3 minutes, in ticks. */
    public static final int LEISURE_TICKS = 20 * 60 * 3;
    /** MC MAX_HEALTH of a citizen: 20 points, ten hearts; a Hytale body's share of its maximum is scaled to it. */
    public static final int MC_MAX_HEALTH = 20;
    /**
     * A citizen body's maximum health (its role's MaxHealth). Deviation from MC (Hytale world): MC's 20 health points →
     * Hytale's 100 (Server/Entity/Stats/Health.json); MC's amounts in points are scaled by maximum / {@link
     * #MC_MAX_HEALTH}.
     */
    public static final int MAX_HEALTH = 100;

    private final int id;
    private String name = "";
    private Gender gender = Gender.MALE;
    private boolean child;
    private Skills skills = Skills.empty();
    private @Nullable Vec3 lastPosition;
    private @Nullable BlockPos respawnPosition;
    private @Nullable BlockPos homeBuilding;
    private @Nullable BlockPos bedPos;
    private boolean asleep;
    private @Nullable BlockPos workBuilding;
    private final CitizenHunger hunger = new CitizenHunger(MAX_SATURATION);
    private final CitizenHappiness happiness = new CitizenHappiness();
    private JobStatus jobStatus = JobStatus.IDLE;
    private int leisureTime;
    private Inventory inventory = new Inventory(INVENTORY_SLOTS);
    private CitizenEquipment equipment = new CitizenEquipment();
    private @Nullable Job job;
    private @Nullable JsonObject unknownJob;
    /** Runtime only, never saved. */
    private final CitizenVitals vitals = new CitizenVitals();

    public CitizenData(int id) {
        this.id = id;
    }

    public int id() {
        return id;
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Gender gender() {
        return gender;
    }

    public void setGender(Gender gender) {
        this.gender = gender;
    }

    public boolean isChild() {
        return child;
    }

    public void setChild(boolean child) {
        this.child = child;
    }

    public Skills skills() {
        return skills;
    }

    public void setSkills(Skills skills) {
        this.skills = skills;
    }

    public @Nullable Vec3 lastPosition() {
        return lastPosition;
    }

    public void setLastPosition(@Nullable Vec3 lastPosition) {
        this.lastPosition = lastPosition;
    }

    /** Its body stands at {@code pos} now: the way from its last position counts as walking (MC walkDist). */
    public void moved(Vec3 pos) {
        if (lastPosition != null) {
            hunger.walked(lastPosition, pos);
        }
        lastPosition = pos;
    }

    public @Nullable BlockPos respawnPosition() {
        return respawnPosition;
    }

    public void setRespawnPosition(@Nullable BlockPos respawnPosition) {
        this.respawnPosition = respawnPosition;
    }

    public @Nullable BlockPos homeBuilding() {
        return homeBuilding;
    }

    public void setHomeBuilding(@Nullable BlockPos homeBuilding) {
        this.homeBuilding = homeBuilding;
    }

    /** MC CitizenData.bedPos: the bed it lies in; null while it has none (MC BlockPos.ZERO). */
    public @Nullable BlockPos bedPos() {
        return bedPos;
    }

    public void setBedPos(@Nullable BlockPos bedPos) {
        this.bedPos = bedPos;
    }

    public boolean asleep() {
        return asleep;
    }

    /** MC CitizenData.setAsleep: falling asleep or waking up ends its leisure time. */
    public void setAsleep(boolean asleep) {
        this.asleep = asleep;
        leisureTime = 0;
    }

    public @Nullable BlockPos workBuilding() {
        return workBuilding;
    }

    public void setWorkBuilding(@Nullable BlockPos workBuilding) {
        this.workBuilding = workBuilding;
    }

    public double saturation() {
        return hunger.saturation();
    }

    public void setSaturation(double saturation) {
        hunger.setSaturation(saturation);
    }

    /** Its saturation, last meals and the work waiting to cost it (MC CitizenData, CitizenFoodHandler). */
    public CitizenHunger hunger() {
        return hunger;
    }

    /** Its happiness and modifiers (MC CitizenHappinessHandler). */
    public CitizenHappiness happiness() {
        return happiness;
    }

    /** What its job is up to (MC CitizenData.jobStatus). */
    public JobStatus jobStatus() {
        return jobStatus;
    }

    public void setJobStatus(JobStatus jobStatus) {
        this.jobStatus = jobStatus;
    }

    /** MC CitizenData.isIdleAtJob: its job status is STUCK. */
    public boolean isIdleAtJob() {
        return jobStatus == JobStatus.STUCK;
    }

    /** Ticks of leisure left; 0 or less when not on a break. */
    public int leisureTime() {
        return leisureTime;
    }

    public void setLeisureTime(int leisureTime) {
        this.leisureTime = leisureTime;
    }

    public Inventory inventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    /** Its armour and the slots its hands hold (MC InventoryCitizen). */
    public CitizenEquipment equipment() {
        return equipment;
    }

    public void setEquipment(CitizenEquipment equipment) {
        this.equipment = equipment;
    }

    /** What its AI and walks did last, for diagnostics (not saved). */
    public CitizenVitals vitals() {
        return vitals;
    }

    public Optional<Job> job() {
        return Optional.ofNullable(job);
    }

    /**
     * Sets or clears the job; either way a kept unknown job is dropped, the game having decided anew. A new job puts
     * the job status back to IDLE (MC CitizenJobHandler.onJobChanged); clearing it keeps the status, as MC.
     */
    public void setJob(@Nullable Job job) {
        this.job = job;
        this.unknownJob = null;
        resetJobStatus();
    }

    /**
     * Its job status back to IDLE when it has a job (MC IJob.initEntityValues, on a job change and each time its body
     * appears); nothing without one.
     */
    public void resetJobStatus() {
        if (job != null) {
            jobStatus = JobStatus.IDLE;
        }
    }

    /** The saved job whose type is not registered (its pack disabled), kept verbatim to be written back. */
    public Optional<JsonObject> unknownJob() {
        return Optional.ofNullable(unknownJob);
    }

    /** Keeps a job this build cannot read; the citizen stays jobless, its work assignment untouched. */
    public void keepUnknownJob(JsonObject raw) {
        this.job = null;
        this.unknownJob = raw;
    }
}
