package dev.hycolony.core.kernel.port;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.WorldKey;
import dev.hycolony.core.kernel.item.ItemKey;
import java.util.Optional;

/** In-world citizen bodies. Implementations tag each body with (colonyId, citizenId) persistently. */
public interface CitizenBodies {
    /** Spawns a body at a free standing spot near {@code near}. Empty if impossible right now. */
    Optional<BodyId> spawn(WorldKey world, BlockPos near, int colonyId, int citizenId, String displayName);

    boolean isAlive(BodyId body);

    Optional<Vec3> position(BodyId body);

    void moveTo(BodyId body, Vec3 target);

    NavStatus navStatus(BodyId body);

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
}
