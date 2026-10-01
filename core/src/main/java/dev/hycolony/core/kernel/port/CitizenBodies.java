package dev.hycolony.core.kernel.port;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.WorldKey;
import dev.hycolony.core.kernel.item.ItemKey;
import java.util.List;
import java.util.Optional;

/** In-world citizen bodies. Implementations tag each body with (colonyId, citizenId) persistently. */
public interface CitizenBodies {
    /** Spawns a body at a free standing spot near {@code near}. Empty if impossible right now. */
    Optional<BodyId> spawn(WorldKey world, BlockPos near, int colonyId, int citizenId, String displayName);

    boolean isAlive(BodyId body);

    Optional<Vec3> position(BodyId body);

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

    /** Gets the body up beside its bed (MC spawnCitizenFromBed); no effect on a standing or unknown body. */
    void wakeUp(BodyId body);
}
