package dev.hycolony.core.citizen;

import dev.hycolony.core.job.Job;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.item.Inventory;
import dev.hycolony.core.kernel.item.ItemKey;
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

    public CitizenData(int id) {
        this.id = id;
        inventory.onGone(this::forgetWear);
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
        inventory.onGone(this::forgetWear);
    }

    /**
     * The job counts tool wear per item kind (MC keeps it on the stack), so once the citizen holds none of {@code item}
     * its count ends with it: whichever path it left by, the next one delivered starts fresh.
     */
    private void forgetWear(ItemKey item) {
        if (job != null) {
            job.setToolUses(item, 0);
        }
    }

    public Optional<Job> job() {
        return Optional.ofNullable(job);
    }

    public void setJob(@Nullable Job job) {
        this.job = job;
    }
}
