package dev.hycolony.core.kernel.port;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.WorldKey;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface PlayerDirectory {
    boolean isOnline(UUID player);

    Optional<BlockPos> position(UUID player);

    Collection<UUID> onlineIn(WorldKey world);

    /** A server operator; false otherwise (never throws). */
    boolean isOperator(UUID player);

    /** Online in this world and in creative mode right now; false otherwise (never throws). */
    boolean isCreative(UUID player);

    /** A server operator, online and in creative mode right now. */
    default boolean isCreativeOperator(UUID player) {
        return isOperator(player) && isCreative(player);
    }
}
