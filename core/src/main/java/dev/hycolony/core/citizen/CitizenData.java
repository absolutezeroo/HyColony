package dev.hycolony.core.citizen;

import com.google.gson.JsonObject;
import dev.hycolony.core.job.Job;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.item.Inventory;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Persistent citizen state. The in-world body is disposable and rebuilt from this. */
public final class CitizenData {
    public static final double MAX_SATURATION = 60;
    public static final int INVENTORY_SLOTS = 27;

    private final int id;
    private String name = "";
    private Gender gender = Gender.MALE;
    private boolean child;
    private Skills skills = Skills.empty();
    private @Nullable Vec3 lastPosition;
    private @Nullable BlockPos respawnPosition;
    private @Nullable BlockPos homeBuilding;
    private @Nullable BlockPos workBuilding;
    private double saturation = MAX_SATURATION;
    private Inventory inventory = new Inventory(INVENTORY_SLOTS);
    private @Nullable Job job;
    private @Nullable JsonObject unknownJob;

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

    public Inventory inventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
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
