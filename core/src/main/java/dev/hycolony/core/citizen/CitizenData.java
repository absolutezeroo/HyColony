package dev.hycolony.core.citizen;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.item.Inventory;

/** Persistent citizen state. The in-world body is disposable and rebuilt from this. */
public final class CitizenData {
    public static final double MAX_SATURATION = 60;
    public static final int INVENTORY_SLOTS = 27;

    private final int id;
    private String name = "";
    private Gender gender = Gender.MALE;
    private boolean child;
    private Skills skills = Skills.empty();
    private Vec3 lastPosition;
    private BlockPos respawnPosition;
    private BlockPos homeBuilding;
    private BlockPos workBuilding;
    private double saturation = MAX_SATURATION;
    private Inventory inventory = new Inventory(INVENTORY_SLOTS);

    public CitizenData(int id) {
        this.id = id;
    }

    public int id() { return id; }
    public String name() { return name; }
    public void setName(String name) { this.name = name; }
    public Gender gender() { return gender; }
    public void setGender(Gender gender) { this.gender = gender; }
    public boolean isChild() { return child; }
    public void setChild(boolean child) { this.child = child; }
    public Skills skills() { return skills; }
    public void setSkills(Skills skills) { this.skills = skills; }
    public Vec3 lastPosition() { return lastPosition; }
    public void setLastPosition(Vec3 lastPosition) { this.lastPosition = lastPosition; }
    public BlockPos respawnPosition() { return respawnPosition; }
    public void setRespawnPosition(BlockPos respawnPosition) { this.respawnPosition = respawnPosition; }
    public BlockPos homeBuilding() { return homeBuilding; }
    public void setHomeBuilding(BlockPos homeBuilding) { this.homeBuilding = homeBuilding; }
    public BlockPos workBuilding() { return workBuilding; }
    public void setWorkBuilding(BlockPos workBuilding) { this.workBuilding = workBuilding; }
    public double saturation() { return saturation; }
    public void setSaturation(double saturation) { this.saturation = saturation; }
    public Inventory inventory() { return inventory; }
    public void setInventory(Inventory inventory) { this.inventory = inventory; }
}
