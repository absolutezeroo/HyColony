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
}
