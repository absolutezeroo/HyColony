package dev.hycolony.core.kernel.port;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.WorldKey;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import java.util.List;
import java.util.Optional;

/** In-world citizen bodies. Implementations tag each body with (colonyId, citizenId) persistently. */
public interface CitizenBodies {
    /**
     * Spawns a body at a free standing spot in {@code near}'s column, at a height the game picks around {@code near}'s.
     * Empty if impossible right now; the core tries the columns around (CitizenArrival).
     */
    Optional<BodyId> spawn(WorldKey world, BlockPos near, int colonyId, int citizenId, String displayName);

    boolean isAlive(BodyId body);

    /** The body's health in percent of its maximum, rounded down; 0 for a body not alive in a loaded world. */
    int healthPercent(BodyId body);

    /**
     * The share of physical damage the body's armour and active effects take off, in percent rounded down (0 to 100),
     * from the game's own percent resistances; flat ones are left out. 0 for a body not alive in a loaded world.
     */
    int defensePercent(BodyId body);

    Optional<Vec3> position(BodyId body);

    /**
     * Where the nearest living hostile creature stands in the body's bounding box inflated by {@code range} blocks
     * horizontally and 3 vertically (MC EntityAICitizenAvoidEntity.getClosestToAvoid, a Monster); empty without one.
     */
    Optional<Vec3> nearestThreat(BodyId body, double range);

    void moveTo(BodyId body, Vec3 target);

    NavStatus navStatus(BodyId body);

    /**
     * The points the body's walk still plans to pass, the next first, as its navigation computed them; empty when it
     * steers straight at its target (nothing in the way), stands, or is unknown.
     */
    List<Vec3> path(BodyId body);

    void setDisplayName(BodyId body, String name);

    void despawn(BodyId body);

    /** Shows {@code item} in the body's main hand; empty clears it. */
    void setHeldItem(BodyId body, Optional<ItemKey> item);

    /**
     * The body wears {@code pieces}, with their wear, one per armour slot (head, chest, hands, legs), empty for a bare
     * slot: it shows them and they protect it, as a player's armour (MC AbstractEntityCitizen.onArmorAdd). A copy: the
     * citizen's own armour stays the truth, and the game never wears the copy.
     */
    void setArmor(BodyId body, List<Optional<ItemAmount>> pieces);

    void playAnimation(BodyId body, BodyAnimation animation);

    /** Turns body and head toward {@code target} (MC WorkerUtil.faceBlock); ends any walk in progress. */
    void lookAt(BodyId body, Vec3 target);

    /** Moves the body to a free spot at or near {@code target} at once (MC's stuck handler, last resort). */
    void teleport(BodyId body, Vec3 target);

    /**
     * Sets the body's walking speed as a factor of its normal speed (1 = normal; MC's MOVEMENT_SPEED attribute over
     * its base 0.3). No effect on an unknown body.
     */
    void setMovementSpeed(BodyId body, double factor);

    /**
     * Lays the body in the bed whose base block is {@code bed} (MC CitizenSleepHandler.trySleep); false when it is no
     * loaded bed, the bed is taken or the body is unknown.
     */
    boolean sleepIn(BodyId body, BlockPos bed);

    /** Whether the body still lies in a bed: false once something else got it up (a broken bed, a teleport…). */
    boolean isInBed(BodyId body);

    /**
     * Gets the body up beside its bed (MC spawnCitizenFromBed) and ends its sleeping pose, also for a body something
     * else already got off its bed; no effect on an unknown body.
     */
    void wakeUp(BodyId body);
}
