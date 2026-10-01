package dev.hycolony.core.citizen;

import com.google.gson.JsonObject;
import dev.hycolony.core.citizen.vitals.CitizenVitals;
import dev.hycolony.core.job.Job;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.item.Inventory;
import java.util.Optional;
import java.util.random.RandomGenerator;
import org.jspecify.annotations.Nullable;

/** Persistent citizen state. The in-world body is disposable and rebuilt from this. */
public final class CitizenData {
    public static final double MAX_SATURATION = 60;
    public static final int INVENTORY_SLOTS = 27;
    /** MC CitizenData.update: a leisure break lasts 3 minutes, in ticks. */
    public static final int LEISURE_TICKS = 20 * 60 * 3;

    private static final int TICKS_SECOND = 20;

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
    private double saturation = MAX_SATURATION;
    private int leisureTime;
    private Inventory inventory = new Inventory(INVENTORY_SLOTS);
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
        return saturation;
    }

    public void setSaturation(double saturation) {
        this.saturation = saturation;
    }

    /** Ticks of leisure left; 0 or less when not on a break. */
    public int leisureTime() {
        return leisureTime;
    }

    public void setLeisureTime(int leisureTime) {
        this.leisureTime = leisureTime;
    }

    /**
     * Counts a running break down by {@code elapsed} ticks, else starts one ({@link #LEISURE_TICKS}) with a chance of
     * 1 in 1200 x (120 / home level) / {@code elapsed}: one break every 120 / home level minutes on average. MC
     * CitizenData.update (leisure part); falling asleep and waking up end a break ({@link #setAsleep}).
     */
    void tickLeisure(int elapsed, int homeLevel, RandomGenerator random) {
        if (leisureTime > 0) {
            leisureTime -= elapsed;
            return;
        }
        // Deviation from MC: a home not built yet (level 0) counts as level 1, where MC's bound would overflow.
        int level = Math.max(1, homeLevel);
        if (random.nextInt(TICKS_SECOND * 60 * (int) (60 / (level / 2.0)) / elapsed) <= 0) {
            leisureTime = LEISURE_TICKS;
        }
    }

    public Inventory inventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    /** What its AI and walks did last, for diagnostics (not saved). */
    public CitizenVitals vitals() {
        return vitals;
    }

    public Optional<Job> job() {
        return Optional.ofNullable(job);
    }

    /** Sets or clears the job; either way a kept unknown job is dropped, the game having decided anew. */
    public void setJob(@Nullable Job job) {
        this.job = job;
        this.unknownJob = null;
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
